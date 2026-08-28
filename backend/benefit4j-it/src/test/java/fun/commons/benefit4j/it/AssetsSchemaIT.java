package fun.commons.benefit4j.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * assets 域六表 schema 基线 (V1.3.0 ~ V1.3.5)
 *
 * 断言与 documents/assets-design.md v0.4 §2 一致,防止后续 schema 变更悄悄回退:
 *   1) 六表 (ubmx_asset/account/posting/tx_order/pre_consume/freeze) 存在 + 关键列;
 *   2) 资金安全 CHECK: account (BOUNDARY OR balance >= -credit_limit) / posting(amount > 0);
 *   3) 幂等闸: tx_order UNIQUE(tenant_id, ext_order_id) —— O10,且 posting 上【无】该唯一键(防回退);
 *   4) posting 为原生 RANGE 分区表 + 当月/下月/default 分区 (O1);
 *   5) 部分索引: pre_consume(expire WHERE RESERVED) / freeze(account WHERE ACTIVE);
 *   6) 种子: 3 个 VIRTUAL 资产 (POINTS/GOLD/COMPUTE),无 FIAT 种子(FIAT fail-fast 前置,A4 依赖)。
 *
 * 与 BucketCandidateIndexIT 同款模式: 本仓未接 Flyway,迁移 SQL 文件即单一事实源,
 * @BeforeAll 直连 PG 执行(绕过 Druid filter chain),IT 可重复运行。
 */
public class AssetsSchemaIT {

    private static String url;
    private static String user;
    private static String pass;

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
        try (Connection c = AssetsMigrations.open()) {
            url = c.getMetaData().getURL();
        }
        user = System.getProperty("spring.datasource.username", "admin");
        pass = System.getProperty("spring.datasource.password", "test@2026");
    }

    @Test
    void testTablesAndKeyColumns() throws Exception {
        Map<String, String[]> expected = new HashMap<>();
        expected.put("ubmx_asset", new String[]{
                "code", "asset_type", "precision", "can_recharge", "can_withdraw", "can_pay",
                "can_transfer", "can_exchange", "can_credit", "issue_mode", "expire_policy",
                "limit_policy", "status"});
        expected.put("ubmx_account", new String[]{
                "id", "tenant_id", "owner_type", "owner_id", "asset_code", "account_type",
                "balance", "credit_limit", "frozen", "version", "status", "ext"});
        expected.put("ubmx_posting", new String[]{
                "id", "tenant_id", "tx_id", "tx_type", "ext_order_id", "leg_seq",
                "src_account_id", "dst_account_id", "asset_code", "amount", "direction",
                "balance_after", "status", "ext", "created_at"});
        expected.put("ubmx_tx_order", new String[]{
                "id", "tenant_id", "ext_order_id", "tx_type", "tx_id", "status", "result_snapshot"});
        expected.put("ubmx_pre_consume", new String[]{
                "id", "tenant_id", "request_id", "tx_id", "charge_mode", "user_account_id",
                "tenant_account_id", "asset_code", "estimated", "settled_amount", "status",
                "expire_time"});
        expected.put("ubmx_freeze", new String[]{
                "id", "tenant_id", "account_id", "freeze_no", "reason", "amount", "used_amount",
                "status", "expire_time", "ext"});

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            for (var e : expected.entrySet()) {
                TreeSet<String> cols = columnsOf(conn, e.getKey());
                assertThat(cols).as("table %s should exist with columns", e.getKey()).isNotNull();
                for (String col : e.getValue()) {
                    assertThat(cols).as("table %s missing column %s", e.getKey(), col).contains(col);
                }
            }
        }
    }

    @Test
    void testAccountFundSafetyCheck() throws Exception {
        // O11/F1: account_type='BOUNDARY' OR balance >= -credit_limit —— 防超发与授信共用同一道闸
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            String def = constraintDef(conn, "ubmx_account", "c",
                    d -> d.contains("BOUNDARY") && d.contains("credit_limit"));
            assertThat(def).as("account fund-safety CHECK must exist").isNotNull();

            // frozen >= 0(PG 输出为 "frozen >= (0)::numeric",匹配列名+操作符)
            String frozenDef = constraintDef(conn, "ubmx_account", "c",
                    d -> d.contains("frozen") && d.contains(">="));
            assertThat(frozenDef).as("account frozen >= 0 CHECK").isNotNull();

            // UNIQUE(tenant_id, owner_type, owner_id, asset_code) 账户唯一
            String uk = constraintDef(conn, "ubmx_account", "u", d -> true);
            assertThat(uk).as("account UNIQUE(tenant_id, owner_type, owner_id, asset_code)")
                    .contains("tenant_id").contains("owner_type").contains("owner_id").contains("asset_code");
        }
    }

    @Test
    void testPostingAmountPositiveAndAppendOnlyShape() throws Exception {
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            String amountDef = constraintDef(conn, "ubmx_posting", "c",
                    d -> d.contains("amount") && d.contains(">"));
            assertThat(amountDef).as("posting amount > 0 CHECK").isNotNull();
            // direction IN ('IN','OUT')
            String dirDef = constraintDef(conn, "ubmx_posting", "c",
                    d -> d.contains("direction") && d.contains("'IN'") && d.contains("'OUT'"));
            assertThat(dirDef).as("posting direction IN/OUT CHECK").isNotNull();
        }
    }

    @Test
    void testIdempotencyGateOnTxOrder_notOnPosting() throws Exception {
        // O10: 幂等闸必须在 tx_order(不分区小表) UNIQUE(tenant_id, ext_order_id);
        //      posting(分区表)上【不得】有 tenant_id+ext_order_id 唯一键(分区键会稀释幂等)
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            String txUk = constraintDef(conn, "ubmx_tx_order", "u", d -> true);
            assertThat(txUk).as("tx_order UNIQUE(tenant_id, ext_order_id)")
                    .contains("tenant_id").contains("ext_order_id");

            String postingUk = constraintDef(conn, "ubmx_posting", "u",
                    d -> d.contains("ext_order_id"));
            assertThat(postingUk)
                    .as("O10 regression: posting must NOT carry ext_order_id unique key")
                    .isNull();
        }
    }

    @Test
    void testPostingPartitioned() throws Exception {
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            // 父表是 RANGE 分区表
            String strat = queryOne(conn,
                    "SELECT p.partstrat FROM pg_partitioned_table p JOIN pg_class c ON c.oid = p.partrelid "
                            + "WHERE c.relname = 'ubmx_posting'");
            assertThat(strat).as("ubmx_posting must be partitioned").isEqualTo("r");

            // 子分区: 当月(202608)+ 未来两月 + default,防 default 分区吃数据后无法补建(O1)
            TreeSet<String> parts = new TreeSet<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT c.relname FROM pg_inherits i "
                            + "JOIN pg_class c ON c.oid = i.inhrelid "
                            + "JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'ubmx_posting'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) parts.add(rs.getString(1));
                }
            }
            assertThat(parts).contains("ubmx_posting_202608", "ubmx_posting_202609",
                    "ubmx_posting_202610", "ubmx_posting_default");
        }
    }

    @Test
    void testPartialIndexesAndPreConsumeStates() throws Exception {
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            // scheduler 部分索引: WHERE status='RESERVED'
            assertThat(indexDef(conn, "idx_ubmx_pre_consume_expire"))
                    .contains("RESERVED");
            // freeze 部分索引: WHERE status='ACTIVE'
            assertThat(indexDef(conn, "idx_ubmx_freeze_account"))
                    .contains("ACTIVE");
            // account 部分索引: WHERE status='ACTIVE'
            assertThat(indexDef(conn, "idx_ubmx_account_owner"))
                    .contains("ACTIVE");
            // O8: 对账按资产切片复合索引
            assertThat(indexDef(conn, "idx_ubmx_posting_asset_time"))
                    .contains("asset_code").contains("created_at");

            // O15: PARTIAL_SETTLED 状态必须在 CHECK 值域内
            String pcStatus = constraintDef(conn, "ubmx_pre_consume", "c",
                    d -> d.contains("PARTIAL_SETTLED"));
            assertThat(pcStatus).as("pre_consume status must include PARTIAL_SETTLED (O15)").isNotNull();

            // DUAL/SOLO
            String modeDef = constraintDef(conn, "ubmx_pre_consume", "c",
                    d -> d.contains("charge_mode") && d.contains("DUAL"));
            assertThat(modeDef).as("pre_consume charge_mode SOLO/DUAL CHECK").isNotNull();

            // freeze reason 分桶(O5)
            String reasonDef = constraintDef(conn, "ubmx_freeze", "c",
                    d -> d.contains("reason") && d.contains("AFTER_SALE"));
            assertThat(reasonDef).as("freeze reason buckets CHECK").isNotNull();
        }
    }

    @Test
    void testSeedVirtualAssetsOnly() throws Exception {
        // 种子: POINTS/GOLD/COMPUTE 三个 VIRTUAL;无 FIAT 种子(A4 FIAT fail-fast 依赖此前提)
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            TreeSet<String> codes = new TreeSet<>();
            List<String> types = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT code, asset_type FROM ubmx_asset")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        codes.add(rs.getString(1));
                        types.add(rs.getString(2));
                    }
                }
            }
            assertThat(codes).as("seed assets").contains("POINTS", "GOLD", "COMPUTE");
            assertThat(types).as("no FIAT seed allowed").containsOnly("VIRTUAL");
        }
    }

    @Test
    void testNoForeignKeys() throws Exception {
        // mc-database-spec P0 第 5 项: 禁外键约束 —— 一致性由 PostingService 应用层单事务保证
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            for (String table : new String[]{"ubmx_asset", "ubmx_account", "ubmx_posting",
                    "ubmx_tx_order", "ubmx_pre_consume", "ubmx_freeze"}) {
                String cnt = queryOne(conn,
                        "SELECT count(*) FROM pg_constraint WHERE conrelid = '" + table + "'::regclass "
                                + "AND contype = 'f'");
                assertThat(cnt).as("table %s must have NO foreign key", table).isEqualTo("0");
            }
        }
    }

    // ---------- helpers ----------

    private static TreeSet<String> columnsOf(Connection conn, String table) throws Exception {
        TreeSet<String> cols = new TreeSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString(1));
            }
        }
        return cols;
    }

    /** 按类型 + 过滤条件取第一条约束定义,找不到返回 null */
    private static String constraintDef(Connection conn, String table, String contype,
                                        java.util.function.Predicate<String> filter) throws Exception {
        List<String> defs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid = ?::regclass AND contype = ?")) {
            ps.setString(1, table);
            ps.setString(2, contype);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) defs.add(rs.getString(1));
            }
        }
        return defs.stream().filter(filter).findFirst().orElse(null);
    }

    private static String indexDef(Connection conn, String indexName) throws Exception {
        return queryOne(conn, "SELECT indexdef FROM pg_indexes "
                + "WHERE schemaname = 'public' AND indexname = '" + indexName + "'");
    }

    private static String queryOne(Connection conn, String sql) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
