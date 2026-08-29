package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaTenant;
import fun.commons.benefit4j.service.BenefitPlatformService;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.tenant.auth.TenantAuthTemplate;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户安全三件套(中间件中台租户设计 §5.3/§5.5/§8 落地):
 *   ① 换 token 防爆破 —— 连续失败 5 次锁 15min,正确凭据也被拒;窗口 key 过期/清除后恢复
 *   ② reset-secret 撤销存量会话 —— 重置后该租户 APP/OPS 会话 key 立即消失
 *   ③ 宽限期双版本(§5.5)—— 旧密钥 24h 内可换 token,过期后 401
 *
 * v1.5.0 起认证/密钥生命周期由 framework4j-tenant 提供(TenantAuthTemplate / TenantSecretService),
 * 双面守卫由模块 DomainGuardInterceptor 承担(冒烟 AssetsSmokeIT 覆盖真实进程 403 链)。
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class TenantSecurityIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private TenantAuthTemplate authTemplate;

    @Autowired
    private BenefitPlatformService platformService;

    @Autowired
    private AccessTokenGenerator tokenGenerator;

    @Autowired
    private StringRedisTemplate redis;

    /** framework4j-tenant 防爆破计数 key: {appName}:tenant:auth:fail:{clientId} */
    private static String failKey(Object clientId) {
        return "benefit4j-it" + TenantAuthTemplate.FAIL_KEY_PREFIX + clientId;
    }

    private String sessionKey(UbmaTenant tenant, String type) {
        String hash = fun.commons.framework4j.accesstoken.util.TokenUtils.calculateKeyHash(
                String.valueOf(tenant.getId()), "salt");   // it yml hash-salt: salt
        return "benefit4j-it" + ":accesstoken:" + type + ":" + hash;
    }

    @Test
    public void bruteForceProtection_locksAfterFiveFailures() {
        UbmaTenant t = createTenant();
        String clientId = String.valueOf(t.getId());
        redis.delete(failKey(clientId));

        // ① 正确凭据先成功一次(清零路径)
        ApiResponse<?> ok = authTemplate.postToken("client_credentials", clientId, t.getTenantSecret());
        assertThat(ok.getCode()).isZero();
        assertThat(redis.hasKey(failKey(clientId))).isFalse();

        // ② 连续 5 次失败
        for (int i = 0; i < 5; i++) {
            ApiResponse<?> fail = authTemplate.postToken("client_credentials", clientId, "wrong-secret-" + i);
            assertThat(fail.getCode()).isEqualTo(401);
        }

        // ③ 第 6 次即使凭据正确也被锁(429)
        ApiResponse<?> locked = authTemplate.postToken("client_credentials", clientId, t.getTenantSecret());
        assertThat(locked.getCode()).isEqualTo(429);
        assertThat(redis.hasKey(failKey(clientId))).isTrue();

        redis.delete(failKey(clientId));   // 清理,不影响其他用例
    }

    @Test
    public void resetSecret_revokesTenantSessions() {
        UbmaTenant t = createTenant();
        Map<String, Object> claims = Map.of("tenant_id", String.valueOf(t.getId()));

        // 造两型会话(等价于换 token 成功后的存量会话)
        tokenGenerator.generateToken("APP", claims);
        tokenGenerator.generateToken("OPS", claims);
        assertThat(redis.hasKey(sessionKey(t, "APP"))).isTrue();
        assertThat(redis.hasKey(sessionKey(t, "OPS"))).isTrue();

        String oldSecret = t.getTenantSecret();

        // reset-secret → 存量会话全部撤销
        ApiResponse<?> resp = (ApiResponse<?>) platformService.postTenantsTenantIdSecret(t.getId());
        assertThat(resp.getCode()).isZero();
        assertThat(redis.hasKey(sessionKey(t, "APP"))).isFalse();
        assertThat(redis.hasKey(sessionKey(t, "OPS"))).isFalse();

        // 宽限期(§5.5): 旧密钥在 24h 内仍可换 token(双版本过渡)
        ApiResponse<?> grace = authTemplate.postToken("client_credentials", String.valueOf(t.getId()), oldSecret);
        assertThat(grace.getCode()).as("宽限期内旧密钥应可用").isZero();
    }

    @Test
    public void resetSecret_graceSecret_expires() throws Exception {
        UbmaTenant t = createTenant();
        String oldSecret = t.getTenantSecret();
        platformService.postTenantsTenantIdSecret(t.getId());

        // 宽限期内旧密钥可用(上一用例已验);把 prev_at 拨到 25h 前 → 旧密钥失效
        try (var conn = AssetsMigrations.open();
             var stmt = conn.createStatement()) {
            stmt.execute("UPDATE ubma_tenant SET tenant_secret_prev_at = now() - interval '25 hours' "
                    + "WHERE id = " + t.getId());
        }
        ApiResponse<?> expired = authTemplate.postToken("client_credentials", String.valueOf(t.getId()), oldSecret);
        assertThat(expired.getCode()).as("宽限期外旧密钥应拒绝").isEqualTo(401);

        // 新密钥不受影响 —— 从 reset 响应拿不到(上个调用),重取
        UbmaTenant fresh = tenantMapper.selectById(t.getId());
        ApiResponse<?> freshOk = authTemplate.postToken("client_credentials",
                String.valueOf(t.getId()), fresh.getTenantSecret());
        assertThat(freshOk.getCode()).isZero();
        redis.delete(failKey(t.getId()));
    }

    @Test
    public void platformCredentials_issueSyntheticPlatformToken() {
        // 平台凭据(it yml framework4j.tenant.platform.*)→ 合成平台租户 tenant_id=0
        ApiResponse<?> resp = authTemplate.postToken("client_credentials", "PLATFORM", "platform-secret-it");
        assertThat(resp.getCode()).isZero();
        String hash = fun.commons.framework4j.accesstoken.util.TokenUtils.calculateKeyHash("0", "salt");
        assertThat(redis.hasKey("benefit4j-it:accesstoken:APP:" + hash))
                .as("平台 token 会话 key(tenant_id=0)").isTrue();
    }

    @Test
    public void rlsPolicies_inPlace_notForcing() throws Exception {
        // V1.4.1: 六表 RLS 就位(ENABLE 不 FORCE,零行为变化),策略 tenant_isolation 存在
        try (var conn = AssetsMigrations.open();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT c.relname, c.relrowsecurity FROM pg_class c " +
                 "WHERE c.relname IN ('ubmx_account','ubmx_posting','ubmx_tx_order'," +
                 "'ubmx_pre_consume','ubmx_freeze','ubmx_reconcile_diff') AND c.relkind IN ('r','p')")) {
            int enabled = 0;
            while (rs.next()) {
                if (rs.getBoolean(2)) enabled++;
            }
            assertThat(enabled).as("六表 RLS 应全部 enabled").isEqualTo(6);
        }
        try (var conn = AssetsMigrations.open();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT count(*) FROM pg_policies WHERE policyname='tenant_isolation'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(6);
        }
    }

    @Test
    public void tenantContractColumns_v142() throws Exception {
        // V1.4.2: 契约列补齐(framework4j-tenant §3.1) —— email/channel/privileges/config/oem
        try (var conn = AssetsMigrations.open();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                 "SELECT string_agg(column_name, ',') FROM information_schema.columns "
                 + "WHERE table_name='ubma_tenant' AND column_name IN "
                 + "('email','channel','privileges','config','oem','tenant_secret_prev','tenant_secret_prev_at')")) {
            rs.next();
            String cols = rs.getString(1);
            assertThat(cols).contains("email", "channel", "privileges", "config", "oem",
                    "tenant_secret_prev", "tenant_secret_prev_at");
        }
    }
}
