package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PreConsumeRequest;
import fun.commons.benefit4j.assets.dto.PreConsumeView;
import fun.commons.benefit4j.assets.dto.SettleRequest;
import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.PostingService;
import fun.commons.benefit4j.assets.service.PreConsumeService;
import fun.commons.benefit4j.assets.entity.UbmxPreConsume;
import fun.commons.benefit4j.assets.mapper.UbmxPreConsumeMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 A7: TCC 三阶段(pre-consume / settle / refund)+ 过期回收
 * 依据 assets-design.md v0.4 §4.4 / §2.4(O15 PARTIAL_SETTLED + guard)
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class PreConsumeServiceIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private PreConsumeService preConsumeService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UbmxPreConsumeMapper preConsumeMapper;

    private Long tenantId;

    private Long app() {
        if (tenantId == null) tenantId = createTenant().getId();
        return tenantId;
    }

    /** 预置: 给 user:{uid} 充 amount POINTS */
    private UbmxAccount fund(Long uid, String amount) {
        postingService.commitTx(cmd("IT-PC-FUND-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", amount)));
        return accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
    }

    private fun.commons.benefit4j.assets.dto.PostingCommand cmd(String orderId, String txType,
            fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec... legs) {
        fun.commons.benefit4j.assets.dto.PostingCommand c = new fun.commons.benefit4j.assets.dto.PostingCommand();
        c.setTenantId(app());
        c.setExtOrderId(orderId);
        c.setTxType(txType);
        c.setLegs(java.util.List.of(legs));
        return c;
    }

    private fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec leg(String src, String dst, String asset, String amount) {
        fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec l = new fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec();
        l.setSrc(src);
        l.setDst(dst);
        l.setAssetCode(asset);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    private PreConsumeRequest pre(Long uid, String requestId, String est) {
        PreConsumeRequest r = new PreConsumeRequest();
        r.setTenantId(app());
        r.setRequestId(requestId);
        r.setAccountRef("user:" + uid);
        r.setAssetCode("POINTS");
        r.setEstimated(new BigDecimal(est));
        return r;
    }

    @Test
    public void testPreConsume_movesBalanceToFrozen() {
        Long uid = uniqueLongId();
        fund(uid, "100");

        PreConsumeView v = preConsumeService.preConsume(pre(uid, "IT-PC-1-" + uniqueTenantid(), "30"));

        assertThat(v.getStatus()).isEqualTo("RESERVED");
        assertThat(v.getExpireTime()).isNotNull();
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        assertThat(acc.getBalance()).isEqualByComparingTo("70");
        assertThat(acc.getFrozen()).isEqualByComparingTo("30");
    }

    @Test
    public void testPreConsume_idempotentReplay() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String req = "IT-PC-2-" + uniqueTenantid();

        PreConsumeView first = preConsumeService.preConsume(pre(uid, req, "30"));
        PreConsumeView second = preConsumeService.preConsume(pre(uid, req, "30"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo("RESERVED");
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        assertThat(acc.getBalance()).isEqualByComparingTo("70");   // 不重复扣
        assertThat(acc.getFrozen()).isEqualByComparingTo("30");
    }

    @Test
    public void testPreConsume_insufficientRejected() {
        Long uid = uniqueLongId();
        fund(uid, "10");
        assertThatThrownBy(() -> preConsumeService.preConsume(pre(uid, "IT-PC-3-" + uniqueTenantid(), "30")))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.INSUFFICIENT_BALANCE);
    }

    @Test
    public void testSettle_diffRefunded() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String req = "IT-PC-4-" + uniqueTenantid();
        preConsumeService.preConsume(pre(uid, req, "30"));

        SettleRequest s = new SettleRequest();
        s.setTenantId(app());
        s.setRequestId(req);
        s.setActual(new BigDecimal("20"));
        PreConsumeView v = preConsumeService.settle(s);

        assertThat(v.getStatus()).isEqualTo("SETTLED");
        assertThat(v.getSettledAmount()).isEqualByComparingTo("20");
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        // est 30 → actual 20, diff 10 退回: 70 + 10 = 80;frozen 清零
        assertThat(acc.getBalance()).isEqualByComparingTo("80");
        assertThat(acc.getFrozen()).isEqualByComparingTo("0");
    }

    @Test
    public void testSettle_overEstimate_deductsMore() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String req = "IT-PC-5-" + uniqueTenantid();
        preConsumeService.preConsume(pre(uid, req, "30"));

        SettleRequest s = new SettleRequest();
        s.setTenantId(app());
        s.setRequestId(req);
        s.setActual(new BigDecimal("50"));   // 超预估 20
        preConsumeService.settle(s);

        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        assertThat(acc.getBalance()).isEqualByComparingTo("50");   // 70 - 20
        assertThat(acc.getFrozen()).isEqualByComparingTo("0");
    }

    @Test
    public void testSettle_overAndInsufficient_partialSettled() {
        // can_credit=true 专用资产(B7 后 POINTS 不允许授信)
        ensureCreditAsset("CRD_P");
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-PC-6F-" + uniqueTenantid(), "ISSUE",
                leg("issue:CRD_P", "user:" + uid, "CRD_P", "100")));
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "CRD_P");
        accountService.updateCreditLimit(app(), acc.getId(), new BigDecimal("10"));
        PreConsumeRequest pre = new PreConsumeRequest();
        pre.setTenantId(app());
        pre.setRequestId("IT-PC-6-" + uniqueTenantid());
        pre.setAccountRef("user:" + uid);
        pre.setAssetCode("CRD_P");
        pre.setEstimated(new BigDecimal("30"));
        preConsumeService.preConsume(pre);
        // 可用 = balance 70 + credit 10 = 80;actual=200 → need 170 > 80 → PARTIAL,扣满 80
        SettleRequest s = new SettleRequest();
        s.setTenantId(app());
        s.setRequestId(pre.getRequestId());
        s.setActual(new BigDecimal("200"));
        PreConsumeView v = preConsumeService.settle(s);

        assertThat(v.getStatus()).isEqualTo("PARTIAL_SETTLED");
        UbmxAccount after = accountService.getAccount(acc.getId());
        assertThat(after.getBalance()).isEqualByComparingTo("-10");   // 扣到授信下限
        assertThat(after.getFrozen()).isEqualByComparingTo("0");
    }

    private void ensureCreditAsset(String code) {
        try {
            var a = new fun.commons.benefit4j.assets.entity.UbmxAsset();
            a.setCode(code);
            a.setName("授信-" + code);
            a.setAssetType("VIRTUAL");
            a.setPrecision(2);
            a.setCanCredit(true);
            registry2().createAsset(a);
        } catch (org.springframework.dao.DuplicateKeyException ignore) {
            // 已注册
        }
    }

    @Autowired
    private fun.commons.benefit4j.assets.service.AssetRegistryService registryRef;

    private fun.commons.benefit4j.assets.service.AssetRegistryService registry2() {
        return registryRef;
    }

    @Test
    public void testSettle_idempotentAndGuarded() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String req = "IT-PC-7-" + uniqueTenantid();
        preConsumeService.preConsume(pre(uid, req, "30"));

        SettleRequest s = new SettleRequest();
        s.setTenantId(app());
        s.setRequestId(req);
        s.setActual(new BigDecimal("30"));
        PreConsumeView first = preConsumeService.settle(s);
        PreConsumeView second = preConsumeService.settle(s);   // 幂等重放

        assertThat(second.getStatus()).isEqualTo("SETTLED");
        assertThat(second.getSettledAmount()).isEqualByComparingTo(first.getSettledAmount());
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        assertThat(acc.getFrozen()).isEqualByComparingTo("0");
        assertThat(acc.getBalance()).isEqualByComparingTo("70");   // 不二次变动

        // 终态后 refund 被幂等语义拒绝重复变更(返回 SETTLED 状态)
        PreConsumeView r = preConsumeService.refund(app(), req);
        assertThat(r.getStatus()).isEqualTo("SETTLED");
        assertThat(acc.getBalance()).isEqualByComparingTo("70");
    }

    @Test
    public void testRefund_restoresBalance() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String req = "IT-PC-8-" + uniqueTenantid();
        preConsumeService.preConsume(pre(uid, req, "30"));

        PreConsumeView v = preConsumeService.refund(app(), req);

        assertThat(v.getStatus()).isEqualTo("REFUNDED");
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        assertThat(acc.getBalance()).isEqualByComparingTo("100");
        assertThat(acc.getFrozen()).isEqualByComparingTo("0");
    }

    @Test
    public void testExpireOnce_releasesExpiredOnly() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String reqExpired = "IT-PC-9E-" + uniqueTenantid();
        String reqAlive = "IT-PC-9A-" + uniqueTenantid();
        PreConsumeView expired = preConsumeService.preConsume(pre(uid, reqExpired, "30"));
        preConsumeService.preConsume(pre(uid, reqAlive, "20"));

        // 手工把第一单推进过期(测试直改 DB,不给生产 service 加测试后门)
        UbmxPreConsume patch = new UbmxPreConsume();
        patch.setId(expired.getId());
        patch.setExpireTime(java.time.OffsetDateTime.now().minusMinutes(1));
        preConsumeMapper.updateById(patch);

        int n = preConsumeService.expireOnce(100);
        assertThat(n).isGreaterThanOrEqualTo(1);

        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        // 两单预扣后 balance=50 frozen=50;过期单 30 回补: balance=80, frozen=20(存活单仍冻结)
        assertThat(acc.getBalance()).isEqualByComparingTo("80");
        assertThat(acc.getFrozen()).isEqualByComparingTo("20");
        // 过期单终态 EXPIRED,存活单仍 RESERVED
        assertThat(preConsumeService.refund(app(), reqExpired).getStatus()).isEqualTo("EXPIRED");
        assertThat(preConsumeService.settle(settle(reqAlive, "20")).getStatus()).isEqualTo("SETTLED");
    }

    private SettleRequest settle(String req, String actual) {
        SettleRequest s = new SettleRequest();
        s.setTenantId(app());
        s.setRequestId(req);
        s.setActual(new BigDecimal(actual));
        return s;
    }

    @Test
    public void testSettle_unknownRequestRejected() {
        SettleRequest s = new SettleRequest();
        s.setTenantId(app());
        s.setRequestId("NO-SUCH-" + uniqueTenantid());
        s.setActual(BigDecimal.ONE);
        assertThatThrownBy(() -> preConsumeService.settle(s))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.PRE_CONSUME_NOT_FOUND);
    }
}
