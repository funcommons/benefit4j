package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.dto.RepayRequest;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.benefit4j.assets.service.PostingService;
import fun.commons.benefit4j.assets.service.PreConsumeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 P2 B7(F1 授信完整能力):
 *   1) 调额校验: 资产未开 can_credit → 拒;已开 → OK(OPS 双签为后续项,当前单 OPS token + 审计)
 *   2) 引擎能力兜底: TRANSFER→can_transfer / CONSUME→can_pay / EXCHANGE→can_exchange
 *   3) repay 还款分录: user → credit:{asset}(BOUNDARY),负余额回正
 *   4) 负余额户禁冻结(提现风控联动,冻结只认正余额)
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class CreditIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private AssetRegistryService registry;

    @Autowired
    private PostingService postingService;

    @Autowired
    private PreConsumeService preConsumeService;

    @Autowired
    private fun.commons.benefit4j.assets.service.AssetsFreezeService freezeService;

    @Autowired
    private fun.commons.benefit4j.controller.BenefitAssetsRuntimeController runtime;

    private Long tenantId;

    private Long app() {
        if (tenantId == null) tenantId = createTenant().getId();
        return tenantId;
    }

    private String ensureAsset(String code, boolean canCredit, boolean canTransfer) {
        try {
            var a = new UbmxAsset();
            a.setCode(code);
            a.setName("授信-" + code);
            a.setAssetType("VIRTUAL");
            a.setPrecision(2);
            a.setCanCredit(canCredit);
            a.setCanTransfer(canTransfer);
            registry.createAsset(a);
        } catch (org.springframework.dao.DuplicateKeyException ignore) {
            // 已注册
        }
        return code;
    }

    @Test
    public void testCreditLimit_requiresAssetCapability() {
        String okAsset = ensureAsset("CRD_OK_" + uniqueTenantid().substring(0, 6).toUpperCase(), true, true);
        String noAsset = ensureAsset("CRD_NO_" + uniqueTenantid().substring(0, 6).toUpperCase(), false, true);
        Long uid1 = uniqueLongId();
        Long uid2 = uniqueLongId();
        var acc1 = accountService.getOrCreateAccount(app(), "USER", uid1, okAsset);
        var acc2 = accountService.getOrCreateAccount(app(), "USER", uid2, noAsset);

        // 资产开了 can_credit: 授信 OK
        accountService.updateCreditLimit(app(), acc1.getId(), new BigDecimal("50"));
        assertThat(accountService.getAccount(acc1.getId()).getCreditLimit())
                .isEqualByComparingTo("50");

        // 资产未开: 拒(F1 能力位)
        assertThatThrownBy(() -> accountService.updateCreditLimit(app(), acc2.getId(), new BigDecimal("50")))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.ASSET_PRIVILEGE_DENIED);
    }

    @Test
    public void testTransferBlockedWithoutCapability() {
        // POINTS 种子 can_transfer=false → TRANSFER 腿被引擎兜底拒绝
        Long a = uniqueLongId();
        Long b = uniqueLongId();
        postingService.commitTx(cmd("IT-CRD-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + a, "POINTS", "10"),
                leg("issue:POINTS", "user:" + b, "POINTS", "10")));
        assertThatThrownBy(() -> postingService.commitTx(cmd("IT-CRD-T-" + uniqueTenantid(), "TRANSFER",
                leg("user:" + a, "user:" + b, "POINTS", "1"))))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.ASSET_PRIVILEGE_DENIED);
    }

    @Test
    public void testRepay_restoresNegativeBalance() {
        String asset = ensureAsset("CRD_RP_" + uniqueTenantid().substring(0, 6).toUpperCase(), true, true);
        Long uid = uniqueLongId();
        var acc = accountService.getOrCreateAccount(app(), "USER", uid, asset);
        accountService.updateCreditLimit(app(), acc.getId(), new BigDecimal("100"));
        postingService.commitTx(cmd("IT-CRD-RP-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:" + asset, "user:" + uid, asset, "20")));
        // 预扣 50 → 结算 60: 余额 20-60 = -40(授信内)
        var pre = new fun.commons.benefit4j.assets.dto.PreConsumeRequest();
        pre.setTenantId(app());
        pre.setRequestId("IT-CRD-RP-" + uniqueTenantid());
        pre.setAccountRef("user:" + uid);
        pre.setAssetCode(asset);
        pre.setEstimated(new BigDecimal("50"));
        preConsumeService.preConsume(pre);
        var settle = new fun.commons.benefit4j.assets.dto.SettleRequest();
        settle.setTenantId(app());
        settle.setRequestId(pre.getRequestId());
        settle.setActual(new BigDecimal("60"));
        preConsumeService.settle(settle);
        assertThat(accountService.getAccount(acc.getId()).getBalance())
                .isEqualByComparingTo("-40");

        // repay 50: user → credit:{asset}(BOUNDARY),余额回正到 10
        fun.commons.framework4j.accesstoken.context.TokenContext.set("APP",
                java.util.Map.of("tenant_id", app()));
        try {
            RepayRequest repay = new RepayRequest();
            repay.setRequestId("IT-CRD-RPY-" + uniqueTenantid());
            repay.setAccountRef("user:" + uid);
            repay.setAssetCode(asset);
            repay.setAmount(new BigDecimal("50"));
            var resp = runtime.postRepay(repay);
            assertThat(resp.isSuccess()).isTrue();
        } finally {
            fun.commons.framework4j.accesstoken.context.TokenContext.clear();
        }
        assertThat(accountService.getAccount(acc.getId()).getBalance())
                .isEqualByComparingTo("10");
        // credit 边界户不记账
        assertThat(accountService.getOrCreateBoundaryAccount(app(), "credit:" + asset, asset).getBalance())
                .isEqualByComparingTo("0");
    }

    @Test
    public void testNegativeBalance_cannotFreeze() {
        String asset = ensureAsset("CRD_FZ_" + uniqueTenantid().substring(0, 6).toUpperCase(), true, true);
        Long uid = uniqueLongId();
        var acc = accountService.getOrCreateAccount(app(), "USER", uid, asset);
        accountService.updateCreditLimit(app(), acc.getId(), new BigDecimal("100"));
        postingService.commitTx(cmd("IT-CRD-FZ-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:" + asset, "user:" + uid, asset, "30")));
        postingService.commitTx(cmd("IT-CRD-FZ-C-" + uniqueTenantid(), "CONSUME",
                leg("user:" + uid, "fee:" + asset, asset, "50")));   // 30-50 = -20
        assertThat(accountService.getAccount(acc.getId()).getBalance())
                .isEqualByComparingTo("-20");

        // 负余额户全额冻结被拒(提现风控联动: 冻结只认正余额)
        var fr = new fun.commons.benefit4j.assets.dto.FreezeRequest();
        fr.setTenantId(app());
        fr.setFreezeNo("IT-CRD-FZ-" + uniqueTenantid());
        fr.setAccountRef("user:" + uid);
        fr.setAssetCode(asset);
        fr.setAmount(new BigDecimal("10"));
        fr.setReason("WITHDRAW");
        assertThatThrownBy(() -> freezeService.freeze(fr))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.INSUFFICIENT_BALANCE);
    }

    // ---------- locals ----------

    private PostingCommand cmd(String orderId, String txType, PostingCommand.LegSpec... legs) {
        PostingCommand c = new PostingCommand();
        c.setTenantId(app());
        c.setExtOrderId(orderId);
        c.setTxType(txType);
        c.setLegs(List.of(legs));
        return c;
    }

    private PostingCommand.LegSpec leg(String src, String dst, String asset, String amount) {
        PostingCommand.LegSpec l = new PostingCommand.LegSpec();
        l.setSrc(src);
        l.setDst(dst);
        l.setAssetCode(asset);
        l.setAmount(new BigDecimal(amount));
        return l;
    }
}
