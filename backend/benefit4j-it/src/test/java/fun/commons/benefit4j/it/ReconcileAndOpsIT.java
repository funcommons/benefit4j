package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AssetsFreezeService;
import fun.commons.benefit4j.assets.service.AssetsIdempotencyService;
import fun.commons.benefit4j.assets.service.AssetsReconcileService;
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
 * assets 域 P2 B5/B6/B8: T+1 对账(恒等式+O14+快照+差错池)/ outbox 事件 / 幂等释放
 * 恒等式口径(O11): Σ NORMAL balance == Σ(BOUNDARY→NORMAL 腿) − Σ(NORMAL→BOUNDARY 腿)
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class ReconcileAndOpsIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private AssetsReconcileService reconcileService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private AssetsFreezeService freezeService;

    @Autowired
    private AssetsIdempotencyService idempotencyService;

    private Long appId;

    private Long app() {
        if (appId == null) appId = createApp().getId();
        return appId;
    }

    private void seedTraffic(Long uid) {
        postingService.commitTx(cmd("IT-REC-SEED-" + uniqueAppid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "200")));
        postingService.commitTx(cmd("IT-REC-SPEND-" + uniqueAppid(), "CONSUME",
                leg("user:" + uid, "fee:POINTS", "POINTS", "30")));   // NORMAL→BOUNDARY 30
    }

    @Test
    public void testIdentityHolds_thenCorruptionDetected_thenRecovered() throws Exception {
        Long uid = uniqueLongId();
        seedTraffic(uid);
        Long accountId = postingService != null ? accountIdOf(uid) : null;

        // ① 正常流量: 恒等式成立,无差异
        AssetsReconcileService.Report ok = reconcileService.runOnce(app(), "POINTS");
        assertThat(ok.assetIdentityOk()).isTrue();
        assertThat(ok.diffCount()).isZero();

        // ② 篡改 balance(+10)→ 恒等式破坏,差异入池
        try (var conn = AssetsMigrations.open(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE ubmx_account SET balance = balance + 10 WHERE id = " + accountId);
        }
        AssetsReconcileService.Report bad = reconcileService.runOnce(app(), "POINTS");
        assertThat(bad.assetIdentityOk()).isFalse();
        assertThat(bad.diffCount()).isGreaterThanOrEqualTo(1);

        // ③ 修回后恢复
        try (var conn = AssetsMigrations.open(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE ubmx_account SET balance = balance - 10 WHERE id = " + accountId);
        }
        assertThat(reconcileService.runOnce(app(), "POINTS").assetIdentityOk()).isTrue();
    }

    @Test
    public void testFreezeConsistencyInReconcile() throws Exception {
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-REC-FZ-F-" + uniqueAppid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "100")));
        var req = new fun.commons.benefit4j.assets.dto.FreezeRequest();
        req.setAppId(app());
        req.setFreezeNo("IT-REC-FZ-" + uniqueAppid());
        req.setAccountRef("user:" + uid);
        req.setAssetCode("POINTS");
        req.setAmount(new BigDecimal("40"));
        req.setReason("RISK");
        freezeService.freeze(req);

        assertThat(reconcileService.runOnce(app(), "POINTS").freezeConsistencyOk()).isTrue();

        // 篡改 frozen → O14 全量校验暴露
        try (var conn = AssetsMigrations.open(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE ubmx_account SET frozen = frozen + 1 "
                    + "WHERE id = " + accountIdOf(uid));
        }
        AssetsReconcileService.Report bad = reconcileService.runOnce(app(), "POINTS");
        assertThat(bad.freezeConsistencyOk()).isFalse();
        assertThat(bad.diffCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void testSnapshotWritten() throws Exception {
        Long uid = uniqueLongId();
        seedTraffic(uid);
        reconcileService.runOnce(app(), "POINTS");

        try (var conn = AssetsMigrations.open();
             var ps = conn.prepareStatement(
                     "SELECT balance, frozen FROM ubmx_balance_snapshot WHERE account_id = ?")) {
            ps.setLong(1, accountIdOf(uid));
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("170");   // 200-30
                assertThat(rs.getBigDecimal(2)).isEqualByComparingTo("0");
            }
        }
    }

    @Test
    public void testOutboxEventOnCommitTx() throws Exception {
        String order = "IT-OBX-" + uniqueAppid();
        postingService.commitTx(cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uniqueLongId(), "POINTS", "5")));

        try (var conn = AssetsMigrations.open();
             var ps = conn.prepareStatement(
                     "SELECT payload FROM ubmp_outbox WHERE aggregate_type = 'assets_tx' "
                             + "AND payload->>'extOrderId' = ?")) {
            ps.setString(1, order);
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains(order);
            }
        }
    }

    @Test
    public void testIdempotencyRelease_isolatesKey() throws Exception {
        String order = "IT-REL-1-" + uniqueAppid();
        Long uid = uniqueLongId();
        postingService.commitTx(cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "10")));

        // 释放: SUCCESS → FAILED(运维纠错,同号永久禁用防双记)
        idempotencyService.release(app(), order, "OPS_CORRECTION");

        // 同号再记账: 拒绝(键已隔离)
        assertThatThrownBy(() -> postingService.commitTx(cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "10")))
        ).isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.IDEMPOTENCY_CONFLICT);

        // 新号正常;且不重复入账(余额仍 10)
        postingService.commitTx(cmd("IT-REL-2-" + uniqueAppid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "5")));
        try (var conn = AssetsMigrations.open();
             var ps = conn.prepareStatement(
                     "SELECT balance FROM ubmx_account WHERE id = ?")) {
            ps.setLong(1, accountIdOf(uid));
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("15");
            }
        }
    }

    // ---------- locals ----------

    private Long accountIdOf(Long uid) throws Exception {
        try (var conn = AssetsMigrations.open();
             var ps = conn.prepareStatement(
                     "SELECT id FROM ubmx_account WHERE app_id = ? AND owner_type = 'USER' "
                             + "AND owner_id = ? AND asset_code = 'POINTS'")) {
            ps.setLong(1, app());
            ps.setLong(2, uid);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private PostingCommand cmd(String orderId, String txType, PostingCommand.LegSpec... legs) {
        PostingCommand c = new PostingCommand();
        c.setAppId(app());
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
