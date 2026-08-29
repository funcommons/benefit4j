package fun.commons.benefit4j.it;

import fun.commons.framework4j.tenant.tck.TenantComplianceSuite;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * benefit4j 租户合规断言(中间件中台租户设计 §10 的机器可执行版)。
 * <p>
 * 结构断言 T1-T3(租户表契约列 / 业务表 tenant_id 索引打头 / email 部分唯一索引)直接跑;
 * 行为断言 T4-T8(双面 403/防爆破/reset 撤销/X-User-Id)由 TenantSecurityIT + AssetsSmokeIT 覆盖,
 * 此处不重放(端点行为断言需真实进程,见 smoke)。
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class TenantComplianceIT extends TenantComplianceSuite {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Override
    public TenantComplianceContext complianceContext() {
        return new TenantComplianceContext() {
            @Override
            public String tenantTable() {
                return "ubma_tenant";
            }

            @Override
            public List<String> businessTables() {
                return List.of("ubmx_account", "ubmx_posting", "ubmx_tx_order",
                        "ubmx_pre_consume", "ubmx_freeze", "ubmx_reconcile_diff");
            }
        };
    }
}
