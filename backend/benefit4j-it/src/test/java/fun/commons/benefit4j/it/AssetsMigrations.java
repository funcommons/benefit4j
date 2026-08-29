package fun.commons.benefit4j.it;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * assets 域迁移执行器(IT 共用): 本仓未接 Flyway,迁移 SQL 文件即 SSOT,
 * 各 IT @BeforeAll 直连执行(幂等可重放),避免 IT 间隐式依赖执行顺序。
 */
final class AssetsMigrations {

    static final String[] FILES = {
            "V1.3.0__init_assets_asset.sql",
            "V1.3.1__init_assets_account.sql",
            "V1.3.2__init_assets_posting.sql",
            "V1.3.3__init_assets_tx_order.sql",
            "V1.3.4__init_assets_pre_consume.sql",
            "V1.3.5__init_assets_freeze.sql",
            "V1.3.6__init_reconcile.sql",
            "V1.4.0__rename_app_to_tenant.sql",
            "V1.4.1__tenant_security_hardening.sql",
            "V1.4.2__tenant_contract_columns.sql",
    };

    private AssetsMigrations() {
    }

    static Connection open() throws Exception {
        String url = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/benefit4j");
        String user = System.getProperty("spring.datasource.username", "admin");
        String pass = System.getProperty("spring.datasource.password", "test@2026");
        return DriverManager.getConnection(url, user, pass);
    }

    static void applyAll() throws Exception {
        try (Connection conn = open()) {
            if (atV142(conn)) return;              // V1.4.2 已生效(契约列就位)→ 全部就绪
            if (atV141(conn)) {                    // RLS 就位但缺契约列 → 只补 V1.4.2
                apply(conn, "V1.4.2__tenant_contract_columns.sql");
                return;
            }
            if (atV140(conn)) {                    // 列已 tenant_id 但缺 V1.4.1 → 补 V1.4.1 + V1.4.2
                apply(conn, "V1.4.1__tenant_security_hardening.sql");
                apply(conn, "V1.4.2__tenant_contract_columns.sql");
                return;
            }
            for (String file : FILES) {            // 空库/纯旧库(app_id)走全序列
                apply(conn, file);
            }
        }
    }

    private static void apply(Connection conn, String file) throws Exception {
        try (var stmt = conn.createStatement()) {
            stmt.execute(loadDdl(file));
        }
    }

    /** V1.4.2 标志: ubma_tenant 契约列 privileges 存在(framework4j-tenant 接入) */
    private static boolean atV142(Connection conn) throws Exception {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT 1 FROM information_schema.columns "
                     + "WHERE table_name='ubma_tenant' AND column_name='privileges'")) {
            return rs.next();
        }
    }

    /** V1.4.1 标志: tenant_isolation 策略存在 */
    private static boolean atV141(Connection conn) throws Exception {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT 1 FROM pg_policies WHERE policyname='tenant_isolation'")) {
            return rs.next();
        }
    }

    /**
     * V1.4.0 标志: ubmx_account 列已是 tenant_id。
     * 历史迁移里的 CREATE INDEX IF NOT EXISTS(引用 app_id)在跳过分支仍会因列不存在
     * 而报错 —— 已改名库不重放 V1.3.x,只补缺失的最新档。三态闭合:空库/旧库/新库。
     */
    private static boolean atV140(Connection conn) throws Exception {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT 1 FROM information_schema.columns "
                 + "WHERE table_name='ubmx_account' AND column_name='tenant_id'")) {
            return rs.next();
        }
    }

    private static String loadDdl(String fileName) throws IOException {
        String backendRoot = System.getProperty("benefit4j.backend.root",
                Path.of(System.getProperty("user.dir")).toString());
        Path candidate = Path.of(backendRoot, "../benefit4j-app/src/main/resources/db/migration/" + fileName);
        if (!Files.exists(candidate)) {
            candidate = Path.of(backendRoot, "benefit4j-app/src/main/resources/db/migration/" + fileName);
        }
        return Files.readString(candidate, StandardCharsets.UTF_8);
    }
}
