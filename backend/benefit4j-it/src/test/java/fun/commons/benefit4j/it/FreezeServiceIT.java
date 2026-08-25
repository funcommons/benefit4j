package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.FreezeRequest;
import fun.commons.benefit4j.assets.dto.FreezeView;
import fun.commons.benefit4j.assets.dto.UnfreezeRequest;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.AssetsFreezeService;
import fun.commons.benefit4j.assets.service.PostingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 P2 B3: 业务冻结分桶(§2.5,O5)+ O14 单源一致性
 *   freeze: balance→frozen 挪移 + ubmx_freeze(ACTIVE, reason 分桶)
 *   unfreeze: RELEASE(回余额,支持部分 used_amount)/ CONSUME(提现成功语义,出账不回)
 *   checkConsistency: Σ(amount - used WHERE ACTIVE) == account.frozen(O14 对账校验)
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class FreezeServiceIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private AssetsFreezeService freezeService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountService accountService;

    private Long appId;

    private Long app() {
        if (appId == null) appId = createApp().getId();
        return appId;
    }

    private fun.commons.benefit4j.assets.entity.UbmxAccount account(Long uid) {
        return accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
    }

    private void fund(Long uid, String amount) {
        postingService.commitTx(cmd("IT-FRZ-F-" + uniqueAppid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", amount)));
    }

    private FreezeRequest freeze(Long uid, String freezeNo, String amount, String reason) {
        FreezeRequest r = new FreezeRequest();
        r.setAppId(app());
        r.setFreezeNo(freezeNo);
        r.setAccountRef("user:" + uid);
        r.setAssetCode("POINTS");
        r.setAmount(new BigDecimal(amount));
        r.setReason(reason);
        return r;
    }

    private UnfreezeRequest unfreeze(String freezeNo, String mode, String amount) {
        UnfreezeRequest r = new UnfreezeRequest();
        r.setAppId(app());
        r.setFreezeNo(freezeNo);
        r.setMode(mode);
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        return r;
    }

    @Test
    public void testFreeze_movesBalanceToFrozen() {
        Long uid = uniqueLongId();
        fund(uid, "100");

        FreezeView v = freezeService.freeze(freeze(uid, "IT-FRZ-1-" + uniqueAppid(), "30", "AFTER_SALE"));

        assertThat(v.getStatus()).isEqualTo("ACTIVE");
        var acc = account(uid);
        assertThat(acc.getBalance()).isEqualByComparingTo("70");
        assertThat(acc.getFrozen()).isEqualByComparingTo("30");
    }

    @Test
    public void testFreeze_idempotentByFreezeNo() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String no = "IT-FRZ-2-" + uniqueAppid();
        var req = freeze(uid, no, "30", "RISK");

        FreezeView first = freezeService.freeze(req);
        FreezeView second = freezeService.freeze(req);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(account(uid).getBalance()).isEqualByComparingTo("70");   // 不重复冻
    }

    @Test
    public void testFreeze_insufficientRejected() {
        Long uid = uniqueLongId();
        fund(uid, "10");
        assertThatThrownBy(() -> freezeService.freeze(freeze(uid, "IT-FRZ-3-" + uniqueAppid(), "30", "RISK")))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.INSUFFICIENT_BALANCE);
        // 冻结只认正余额,不透支授信
        assertThat(account(uid).getFrozen()).isEqualByComparingTo("0");
    }

    @Test
    public void testFreeze_invalidReasonRejected() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        assertThatThrownBy(() -> freezeService.freeze(freeze(uid, "IT-FRZ-4-" + uniqueAppid(), "10", "WHATEVER")))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.ASSET_INVALID);
    }

    @Test
    public void testUnfreezeRelease_full() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String no = "IT-FRZ-5-" + uniqueAppid();
        freezeService.freeze(freeze(uid, no, "30", "AFTER_SALE"));

        FreezeView v = freezeService.unfreeze(unfreeze(no, "RELEASE", null));

        assertThat(v.getStatus()).isEqualTo("RELEASED");
        var acc = account(uid);
        assertThat(acc.getBalance()).isEqualByComparingTo("100");
        assertThat(acc.getFrozen()).isEqualByComparingTo("0");
    }

    @Test
    public void testUnfreezeRelease_partialThenFull() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String no = "IT-FRZ-6-" + uniqueAppid();
        freezeService.freeze(freeze(uid, no, "30", "RISK"));

        // 部分释放 10: 70+10=80, frozen 20, 仍 ACTIVE
        FreezeView part = freezeService.unfreeze(unfreeze(no, "RELEASE", "10"));
        assertThat(part.getStatus()).isEqualTo("ACTIVE");
        assertThat(part.getUsedAmount()).isEqualByComparingTo("10");
        var acc = account(uid);
        assertThat(acc.getBalance()).isEqualByComparingTo("80");
        assertThat(acc.getFrozen()).isEqualByComparingTo("20");

        // 再全放: 回到 100/0, RELEASED
        FreezeView full = freezeService.unfreeze(unfreeze(no, "RELEASE", null));
        assertThat(full.getStatus()).isEqualTo("RELEASED");
        assertThat(account(uid).getFrozen()).isEqualByComparingTo("0");
        assertThat(account(uid).getBalance()).isEqualByComparingTo("100");
    }

    @Test
    public void testUnfreezeConsume_withdrawSemantics() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String no = "IT-FRZ-7-" + uniqueAppid();
        freezeService.freeze(freeze(uid, no, "30", "WITHDRAW"));

        FreezeView v = freezeService.unfreeze(unfreeze(no, "CONSUME", null));

        assertThat(v.getStatus()).isEqualTo("CONSUMED");
        var acc = account(uid);
        // 提现成功: frozen 清零,balance 不回(70)
        assertThat(acc.getFrozen()).isEqualByComparingTo("0");
        assertThat(acc.getBalance()).isEqualByComparingTo("70");
    }

    @Test
    public void testUnfreeze_idempotentAndUnknown() {
        Long uid = uniqueLongId();
        fund(uid, "100");
        String no = "IT-FRZ-8-" + uniqueAppid();
        freezeService.freeze(freeze(uid, no, "30", "AFTER_SALE"));
        freezeService.unfreeze(unfreeze(no, "RELEASE", null));

        // 终态重放: 幂等返回原状态,不二次变更
        FreezeView replay = freezeService.unfreeze(unfreeze(no, "RELEASE", null));
        assertThat(replay.getStatus()).isEqualTo("RELEASED");
        assertThat(account(uid).getBalance()).isEqualByComparingTo("100");

        // 不存在
        assertThatThrownBy(() -> freezeService.unfreeze(unfreeze("NO-SUCH-" + uniqueAppid(), "RELEASE", null)))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.FREEZE_NOT_FOUND);
    }

    @Test
    public void testO14Consistency() throws Exception {
        Long uid = uniqueLongId();
        fund(uid, "200");
        String no1 = "IT-FRZ-9A-" + uniqueAppid();
        String no2 = "IT-FRZ-9B-" + uniqueAppid();
        freezeService.freeze(freeze(uid, no1, "50", "RISK"));
        freezeService.freeze(freeze(uid, no2, "30", "AFTER_SALE"));
        freezeService.unfreeze(unfreeze(no2, "RELEASE", "10"));   // no2 剩 20

        var acc = account(uid);
        // O14: Σ(amount - used WHERE ACTIVE) = (50-0) + (30-10) = 70 == account.frozen
        assertThat(freezeService.checkConsistency(app(), acc.getId())).isTrue();

        // 篡改 account.frozen(测试直改 DB)→ 不一致暴露(对账告警口径)
        try (var conn = AssetsMigrations.open();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE ubmx_account SET frozen = 1 WHERE id = " + acc.getId());
        }
        assertThat(freezeService.checkConsistency(app(), acc.getId())).isFalse();
    }

    // ---------- locals ----------

    private fun.commons.benefit4j.assets.dto.PostingCommand cmd(String orderId, String txType,
            fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec... legs) {
        fun.commons.benefit4j.assets.dto.PostingCommand c = new fun.commons.benefit4j.assets.dto.PostingCommand();
        c.setAppId(app());
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
