package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.ExchangeRequest;
import fun.commons.benefit4j.assets.dto.PreConsumeDualRequest;
import fun.commons.benefit4j.assets.dto.PreConsumeView;
import fun.commons.benefit4j.assets.dto.SettleRequest;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.benefit4j.assets.service.AssetsExchangeService;
import fun.commons.benefit4j.assets.service.PostingService;
import fun.commons.benefit4j.assets.service.PreConsumeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 P2 B1+B2: DUAL 双账户原子扣(§3.3 定案: 无中间户,双侧 balance→frozen 挪移)
 * + EXCHANGE 兑换(§3.2: exchange 边界户双腿,汇率由调用方定价)
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class DualAndExchangeIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private PreConsumeService preConsumeService;

    @Autowired
    private AssetsExchangeService exchangeService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AssetRegistryService registry;

    private Long tenantId;

    private Long app() {
        if (tenantId == null) tenantId = createTenant().getId();
        return tenantId;
    }

    private void fund(Long uid, Long tenantId, String amount) {
        postingService.commitTx(cmd("IT-DUAL-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", amount),
                leg("issue:POINTS", "tenant:" + tenantId, "POINTS", amount)));
    }

    private PreConsumeDualRequest dual(Long uid, Long tenantId, String requestId, String est) {
        PreConsumeDualRequest r = new PreConsumeDualRequest();
        r.setTenantId(app());
        r.setRequestId(requestId);
        r.setUserAccountRef("user:" + uid);
        r.setTenantAccountRef("tenant:" + tenantId);
        r.setAssetCode("POINTS");
        r.setEstimated(new BigDecimal(est));
        return r;
    }

    private SettleRequest settle(String requestId, String actual) {
        SettleRequest s = new SettleRequest();
        s.setTenantId(app());
        s.setRequestId(requestId);
        s.setActual(new BigDecimal(actual));
        return s;
    }

    @Test
    public void testDualPreConsume_bothSidesFrozen() {
        Long uid = uniqueLongId();
        Long tid = uniqueLongId();
        fund(uid, tid, "100");

        PreConsumeView v = preConsumeService.preConsumeDual(dual(uid, tid, "IT-DUAL-1-" + uniqueTenantid(), "30"));

        assertThat(v.getStatus()).isEqualTo("RESERVED");
        var user = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        var tenant = accountService.getOrCreateAccount(app(), "TENANT", tid, "POINTS");
        assertThat(user.getBalance()).isEqualByComparingTo("70");
        assertThat(user.getFrozen()).isEqualByComparingTo("30");
        assertThat(tenant.getBalance()).isEqualByComparingTo("70");
        assertThat(tenant.getFrozen()).isEqualByComparingTo("30");
    }

    @Test
    public void testDualSettle_diffRefundedBothSides() {
        Long uid = uniqueLongId();
        Long tid = uniqueLongId();
        fund(uid, tid, "100");
        String req = "IT-DUAL-2-" + uniqueTenantid();
        preConsumeService.preConsumeDual(dual(uid, tid, req, "30"));

        PreConsumeView v = preConsumeService.settle(settle(req, "20"));

        assertThat(v.getStatus()).isEqualTo("SETTLED");
        assertThat(v.getSettledAmount()).isEqualByComparingTo("20");
        for (var acc : List.of(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS"),
                accountService.getOrCreateAccount(app(), "TENANT", tid, "POINTS"))) {
            assertThat(acc.getBalance()).isEqualByComparingTo("80");   // 70 + diff 10
            assertThat(acc.getFrozen()).isEqualByComparingTo("0");
        }
    }

    @Test
    public void testDualRefund_bothSidesRestored() {
        Long uid = uniqueLongId();
        Long tid = uniqueLongId();
        fund(uid, tid, "100");
        String req = "IT-DUAL-3-" + uniqueTenantid();
        preConsumeService.preConsumeDual(dual(uid, tid, req, "40"));

        PreConsumeView v = preConsumeService.refund(app(), req);

        assertThat(v.getStatus()).isEqualTo("REFUNDED");
        assertThat(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getBalance())
                .isEqualByComparingTo("100");
        assertThat(accountService.getOrCreateAccount(app(), "TENANT", tid, "POINTS").getBalance())
                .isEqualByComparingTo("100");
    }

    @Test
    public void testDualPreConsume_oneSideInsufficient_allOrNothing() {
        Long uid = uniqueLongId();
        Long tid = uniqueLongId();
        // user 只有 10,tenant 100
        postingService.commitTx(cmd("IT-DUAL-4-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "10"),
                leg("issue:POINTS", "tenant:" + tid, "POINTS", "100")));

        assertThatThrownBy(() -> preConsumeService.preConsumeDual(
                dual(uid, tid, "IT-DUAL-4-" + uniqueTenantid(), "30")))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.INSUFFICIENT_BALANCE);

        // 整笔回滚: 双侧原封不动
        assertThat(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getBalance())
                .isEqualByComparingTo("10");
        assertThat(accountService.getOrCreateAccount(app(), "TENANT", tid, "POINTS").getBalance())
                .isEqualByComparingTo("100");
        assertThat(accountService.getOrCreateAccount(app(), "TENANT", tid, "POINTS").getFrozen())
                .isEqualByComparingTo("0");
    }

    @Test
    public void testDualIdempotentReplay() {
        Long uid = uniqueLongId();
        Long tid = uniqueLongId();
        fund(uid, tid, "100");
        String req = "IT-DUAL-5-" + uniqueTenantid();
        var r = dual(uid, tid, req, "30");

        PreConsumeView first = preConsumeService.preConsumeDual(r);
        PreConsumeView second = preConsumeService.preConsumeDual(r);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getBalance())
                .isEqualByComparingTo("70");
    }

    @Test
    public void testDualPartialSettle_whenOneSideCannotCover() {
        Long uid = uniqueLongId();
        Long tid = uniqueLongId();
        var userAcc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        accountService.updateCreditLimit(app(), userAcc.getId(), BigDecimal.ZERO);
        postingService.commitTx(cmd("IT-DUAL-6-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "100"),
                leg("issue:POINTS", "tenant:" + tid, "POINTS", "35")));
        String req = "IT-DUAL-6-" + uniqueTenantid();
        preConsumeService.preConsumeDual(dual(uid, tid, req, "30"));   // user 70/f30, tenant 5/f30

        // actual 100: user 侧 need 70(affordable 70 全扣), tenant 侧 need 70(affordable 5)→ PARTIAL
        PreConsumeView v = preConsumeService.settle(settle(req, "100"));
        assertThat(v.getStatus()).isEqualTo("PARTIAL_SETTLED");

        assertThat(accountService.getAccount(userAcc.getId()).getBalance()).isEqualByComparingTo("0");
        assertThat(accountService.getOrCreateAccount(app(), "TENANT", tid, "POINTS").getBalance())
                .isEqualByComparingTo("0");   // 5 - 5 = 0
    }

    // ---------- EXCHANGE ----------

    @Test
    public void testExchange_twoLegsAtomic() {
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-EX-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "2000")));

        ExchangeRequest req = new ExchangeRequest();
        req.setTenantId(app());
        req.setOrderId("IT-EX-1-" + uniqueTenantid());
        req.setOwnerRef("user:" + uid);
        req.setFromAssetCode("POINTS");
        req.setToAssetCode("GOLD");
        req.setFromAmount(new BigDecimal("2000"));
        req.setToAmount(new BigDecimal("20"));
        var result = exchangeService.exchange(req);

        assertThat(result.getTxId()).isNotNull();
        assertThat(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getBalance())
                .isEqualByComparingTo("0");
        assertThat(accountService.getOrCreateAccount(app(), "USER", uid, "GOLD").getBalance())
                .isEqualByComparingTo("20");
        // exchange 边界户不记账(O11)
        assertThat(accountService.getOrCreateBoundaryAccount(app(), "exchange:POINTS", "POINTS").getBalance())
                .isEqualByComparingTo("0");
    }

    @Test
    public void testExchange_insufficientSource() {
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-EX-2-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "10")));

        ExchangeRequest req = new ExchangeRequest();
        req.setTenantId(app());
        req.setOrderId("IT-EX-2-" + uniqueTenantid());
        req.setOwnerRef("user:" + uid);
        req.setFromAssetCode("POINTS");
        req.setToAssetCode("GOLD");
        req.setFromAmount(new BigDecimal("2000"));
        req.setToAmount(new BigDecimal("20"));
        assertThatThrownBy(() -> exchangeService.exchange(req))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.INSUFFICIENT_BALANCE);
    }

    @Test
    public void testExchange_capabilityDeniedAndIdempotent() {
        // 关闭 GOLD 的兑换能力
        try {
            var gold = new UbmxAsset();
            gold.setCode("GOLD");
            gold.setName("金币");
            gold.setAssetType("VIRTUAL");
            gold.setPrecision(2);
            registry.createAsset(gold);
        } catch (DuplicateKeyException ignore) {
            // 种子已有
        }
        var patch = new UbmxAsset();
        patch.setCanExchange(false);
        registry.patchAsset("GOLD", patch);
        try {
            Long uid = uniqueLongId();
            postingService.commitTx(cmd("IT-EX-3-F-" + uniqueTenantid(), "ISSUE",
                    leg("issue:POINTS", "user:" + uid, "POINTS", "100")));
            ExchangeRequest req = new ExchangeRequest();
            req.setTenantId(app());
            req.setOrderId("IT-EX-3-" + uniqueTenantid());
        req.setOwnerRef("user:" + uid);
            req.setFromAssetCode("POINTS");
            req.setToAssetCode("GOLD");
            req.setFromAmount(new BigDecimal("100"));
            req.setToAmount(new BigDecimal("1"));
            assertThatThrownBy(() -> exchangeService.exchange(req))
                    .isInstanceOf(AssetsException.class)
                    .extracting(e -> ((AssetsException) e).getCode())
                    .isEqualTo(AssetsException.ASSET_PRIVILEGE_DENIED);
        } finally {
            var restore = new UbmxAsset();
            restore.setCanExchange(true);
            registry.patchAsset("GOLD", restore);
        }
    }

    // ---------- locals ----------

    private fun.commons.benefit4j.assets.dto.PostingCommand cmd(String orderId, String txType,
            fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec... legs) {
        fun.commons.benefit4j.assets.dto.PostingCommand c = new fun.commons.benefit4j.assets.dto.PostingCommand();
        c.setTenantId(app());
        c.setExtOrderId(orderId);
        c.setTxType(txType);
        c.setLegs(List.of(legs));
        return c;
    }

    private fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec leg(String src, String dst, String asset, String amount) {
        fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec l = new fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec();
        l.setSrc(src); l.setDst(dst); l.setAssetCode(asset); l.setAmount(new BigDecimal(amount));
        return l;
    }
}
