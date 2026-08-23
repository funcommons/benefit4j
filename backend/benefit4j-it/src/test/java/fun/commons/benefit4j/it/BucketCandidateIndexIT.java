package fun.commons.benefit4j.it;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.benefit4j.entity.UbmaSubscribeItem;
import fun.commons.benefit4j.mapper.UbmaSubscribeItemMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 扣减候选桶查询的索引基线 (V1.2.1)
 *
 * 目标:
 *   1) 验证 idx_user_item_active_priority 索引在 PG schema 中存在,
 *      列顺序 (app_id, item_id, bucket_priority DESC, expires_at ASC NULLS LAST)
 *      与 WHERE is_deleted=0 部分索引条件都被正确创建.
 *      防止后续 schema 变更悄悄回退这个索引.
 *   2) 验证 findDeductionCandidates 等价查询在 50+ 桶/单用户场景下
 *      按 bucket_priority DESC + expires_at ASC + created_at ASC 排序结果正确.
 *
 * 注意:
 *   - 当前 benefit4j-app 未启用 Flyway (无 flyway-core 依赖, 无 spring.flyway.* 配置),
 *     V1.2.1__bucket_candidate_index.sql 不会被自动执行. @BeforeAll 兜底: 索引不存在
 *     就补建, 保证 IT 可重复运行且与生产期望一致.
 *   - 测试 1 用 schema 元数据断言 (pg_indexes / pg_index) 而非 EXPLAIN:
 *     PG optimizer 在 5K 行规模下会选更便宜的"单列 app_id 索引 + Sort"
 *     (Sort 才 4ms/896kB, 不值得走复合索引), 所以 EXPLAIN 断言不稳定.
 *     索引存在 + 列定义正确才是真正的回归保护.
 *   - Druid Wall 默认拦截 ANALYZE / EXPLAIN 等维护语句, 本测试用 DriverManager
 *     直连 PG 完全绕过 Druid filter chain, 保证索引可观测性.
 */
public class BucketCandidateIndexIT extends BaseServiceTest {

    private static final String INDEX_NAME = "idx_user_item_active_priority";
    private static final String TABLE_NAME = "ubma_subscribe_item";
    // 迁移文件作为单一事实源 (single source of truth) — 改 SQL 时无需同步两份
    // IT 模块依赖 benefit4j-app 但读不到 app/src/main/resources (classpath 隔离),
    // 改成按 backend 根目录相对路径读; 路径可通过 sysprop 覆盖
    private static String loadV121Ddl() throws IOException {
        String backendRoot = System.getProperty("benefit4j.backend.root",
                Path.of(System.getProperty("user.dir")).toString());
        // mvn -pl benefit4j-it 时 user.dir = backend/benefit4j-it, 向上两级到 backend/
        Path candidate = Path.of(backendRoot, "../benefit4j-app/src/main/resources/db/migration/V1.2.1__bucket_candidate_index.sql");
        if (!Files.exists(candidate)) {
            // 备选: 当 user.dir 已经是 backend 根目录时
            candidate = Path.of(backendRoot, "benefit4j-app/src/main/resources/db/migration/V1.2.1__bucket_candidate_index.sql");
        }
        return Files.readString(candidate, StandardCharsets.UTF_8);
    }

    @Autowired
    private UbmaSubscribeItemMapper subscribeItemMapper;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPass;

    @BeforeAll
    static void ensureIndex() throws Exception {
        // 从系统属性读 (surefire fork 模式可注入), 缺省用 yml 里的本地连接
        String url = System.getProperty("spring.datasource.url",
                "jdbc:postgresql://localhost:5432/benefit4j");
        String user = System.getProperty("spring.datasource.username", "admin");
        String pass = System.getProperty("spring.datasource.password", "test@2026");
        // 直接读 SQL 迁移文件, 与生产运维执行的脚本保持一致
        String ddl = loadV121Ddl();
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            try (var stmt = conn.createStatement()) {
                stmt.execute(ddl);
            }
        }
    }

    @Test
    void testIndexExists_withCorrectColumnsAndPredicate() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            // 1) 索引必须在 pg_indexes 中存在
            String definition = readIndexDefinition(conn);
            assertThat(definition)
                    .as("index %s should exist on %s", INDEX_NAME, TABLE_NAME)
                    .isNotNull()
                    .startsWith("CREATE INDEX");

            // 2) 列顺序必须包含 (app_id, item_id, bucket_priority, expires_at)
            List<String> indexedColumns = readIndexedColumnNames(conn);
            assertThat(indexedColumns)
                    .as("indexed columns (in order)")
                    .containsExactly("app_id", "item_id", "bucket_priority", "expires_at");

            // 3) WHERE is_deleted = 0 部分索引条件必须存在
            assertThat(definition.toLowerCase())
                    .as("partial index predicate WHERE is_deleted = 0")
                    .contains("where (is_deleted = 0)");

            // 4) 复合排序键: bucket_priority DESC, expires_at ASC NULLS LAST
            List<IndexColumnOption> opts = readColumnOptions(conn);
            assertThat(opts)
                    .as("column sort options")
                    .hasSize(4);

            IndexColumnOption priority = opts.get(2);
            assertThat(priority.desc).as("bucket_priority should be DESC").isTrue();

            IndexColumnOption expires = opts.get(3);
            assertThat(expires.desc).as("expires_at should be ASC").isFalse();
            assertThat(expires.nullsFirst).as("expires_at should be NULLS LAST (not FIRST)").isFalse();
        }
    }

    @Test
    void testFindDeductionCandidates_returnsByPriorityOrder() {
        // 功能正确性: 50 个桶, priority 不同, 排序符合预期
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 100, 10, itemId).getId();
        String subscribeId = createSubscription(appId, "order-user-" + uniqueAppid(), setId);
        Long subId = toLongId(subscribeId);

        OffsetDateTime t0 = OffsetDateTime.now();
        for (int i = 0; i < 50; i++) {
            UbmaSubscribeItem b = new UbmaSubscribeItem();
            b.setAppId(appId);
            b.setSubscribeId(subId);
            b.setItemId(itemId);
            b.setQuotaLimit(100);
            b.setSourceType("ORD_" + i);
            b.setBucketPriority(50 - i);  // 倒序插入, 验证排序
            b.setExpiresAt(t0.plusDays(60));
            b.setCreatedAt(t0);
            b.setUpdatedAt(t0);
            subscribeItemMapper.insert(b);
        }

        OffsetDateTime now = OffsetDateTime.now();
        LambdaQueryWrapper<UbmaSubscribeItem> q = new LambdaQueryWrapper<>();
        q.eq(UbmaSubscribeItem::getAppId, appId)
                .eq(UbmaSubscribeItem::getItemId, itemId)
                // 只看自己插入的 ORD_* 桶, 排除 createSubscription 自动建的 SUBSCRIPTION 默认桶
                .like(UbmaSubscribeItem::getSourceType, "ORD_%")
                .apply("EXISTS (SELECT 1 FROM ubma_subscribe s WHERE s.id = ubma_subscribe_item.subscribe_id AND s.status = 'ACTIVE' AND s.is_deleted = 0)")
                .and(w -> w.isNull(UbmaSubscribeItem::getExpiresAt)
                        .or().gt(UbmaSubscribeItem::getExpiresAt, now))
                .orderByDesc(UbmaSubscribeItem::getBucketPriority)
                .orderByAsc(UbmaSubscribeItem::getExpiresAt)
                .orderByAsc(UbmaSubscribeItem::getCreatedAt);
        List<UbmaSubscribeItem> rows = subscribeItemMapper.selectList(q);
        assertThat(rows).hasSize(50);
        // 第一个 priority 应该是 50, 最后一个是 1
        assertThat(rows.get(0).getBucketPriority()).isEqualTo(50);
        assertThat(rows.get(49).getBucketPriority()).isEqualTo(1);
    }

    /**
     * 从 pg_indexes 读 CREATE INDEX 语句, 用于校验 partial predicate.
     */
    private String readIndexDefinition(Connection conn) throws Exception {
        try (var ps = conn.prepareStatement(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?")) {
            ps.setString(1, INDEX_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * 读索引列顺序 (按索引定义中的位置).
     * 用 unnest(... WITH ORDINALITY) 一次拿到列名 + 序号.
     * 注意: indkey 是表 attnum, 必须 join 到表的 pg_attribute, 不是索引的.
     */
    private List<String> readIndexedColumnNames(Connection conn) throws Exception {
        String sql =
                "SELECT a.attname " +
                "  FROM pg_index i " +
                "  JOIN pg_class c ON c.oid = i.indexrelid " +
                "  JOIN pg_class t ON t.oid = i.indrelid " +
                "  JOIN LATERAL unnest(i.indkey::int2[]) WITH ORDINALITY AS u(attnum, ord) ON true " +
                "  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = u.attnum " +
                " WHERE c.relname = ? " +
                " ORDER BY u.ord";
        List<String> cols = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, INDEX_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cols.add(rs.getString(1));
            }
        }
        return cols;
    }

    /**
     * 读每列的 DESC / NULLS FIRST/LAST 选项.
     * pg_index.indoption 是 int2[] bitmask: 0x01 = DESC, 0x02 = NULLS FIRST.
     */
    private List<IndexColumnOption> readColumnOptions(Connection conn) throws Exception {
        String sql =
                "SELECT u.ord, u.opt " +
                "  FROM pg_index i " +
                "  JOIN pg_class c ON c.oid = i.indexrelid " +
                "  JOIN LATERAL unnest(i.indoption) WITH ORDINALITY AS u(opt, ord) ON true " +
                " WHERE c.relname = ? " +
                " ORDER BY u.ord";
        List<IndexColumnOption> result = new ArrayList<>();
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, INDEX_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int o = rs.getInt(2);
                    result.add(new IndexColumnOption((o & 0x01) != 0, (o & 0x02) != 0));
                }
            }
        }
        return result;
    }

    private record IndexColumnOption(boolean desc, boolean nullsFirst) {}

    private Long toLongId(String openId) {
        try {
            return Long.parseLong(openId);
        } catch (NumberFormatException e) {
            return fun.commons.framework4j.id.util.IdObfuscator.fromOpenId(openId);
        }
    }
}