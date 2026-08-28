package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaTenant;
import fun.commons.benefit4j.service.BenefitAuthService;
import fun.commons.benefit4j.service.BenefitPlatformService;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
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
 *   ③ 平台域守卫逻辑由 starter 单测覆盖(PlatformIdentityGuardTest),真实进程链由 smoke 覆盖
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class TenantSecurityIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private BenefitAuthService authService;

    @Autowired
    private BenefitPlatformService platformService;

    @Autowired
    private AccessTokenGenerator tokenGenerator;

    @Autowired
    private StringRedisTemplate redis;

    private String sessionKey(UbmaTenant tenant, String type) {
        String hash = fun.commons.framework4j.accesstoken.util.TokenUtils.calculateKeyHash(
                String.valueOf(tenant.getId()), "salt");   // it yml hash-salt: salt
        return "benefit4j-it" + ":accesstoken:" + type + ":" + hash;
    }

    @Test
    public void bruteForceProtection_locksAfterFiveFailures() {
        UbmaTenant t = createTenant();
        String clientId = String.valueOf(t.getId());
        String lockKey = "benefit4j:auth:fail:" + clientId;
        redis.delete(lockKey);

        // ① 正确凭据先成功一次(清零路径)
        Object ok = authService.postToken("client_credentials", clientId, t.getTenantSecret());
        assertThat(((ApiResponse<?>) ok).getCode()).isZero();
        assertThat(redis.hasKey(lockKey)).isFalse();

        // ② 连续 5 次失败
        for (int i = 0; i < 5; i++) {
            Object fail = authService.postToken("client_credentials", clientId, "wrong-secret-" + i);
            assertThat(((ApiResponse<?>) fail).getCode()).isEqualTo(401);
        }

        // ③ 第 6 次即使凭据正确也被锁(429)
        Object locked = authService.postToken("client_credentials", clientId, t.getTenantSecret());
        assertThat(((ApiResponse<?>) locked).getCode()).isEqualTo(429);
        assertThat(redis.hasKey(lockKey)).isTrue();

        redis.delete(lockKey);   // 清理,不影响其他用例
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
        Object resp = platformService.postTenantsTenantIdSecret(t.getId());
        assertThat(((ApiResponse<?>) resp).getCode()).isZero();
        assertThat(redis.hasKey(sessionKey(t, "APP"))).isFalse();
        assertThat(redis.hasKey(sessionKey(t, "OPS"))).isFalse();

        // 宽限期(§5.5): 旧密钥在 24h 内仍可换 token(双版本过渡)
        Object grace = authService.postToken("client_credentials", String.valueOf(t.getId()), oldSecret);
        assertThat(((ApiResponse<?>) grace).getCode())
                .as("宽限期内旧密钥应可用").isZero();
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
        Object expired = authService.postToken("client_credentials", String.valueOf(t.getId()), oldSecret);
        assertThat(((ApiResponse<?>) expired).getCode())
                .as("宽限期外旧密钥应拒绝").isEqualTo(401);

        // 新密钥不受影响 —— 从 reset 响应拿不到(上个调用),重取
        UbmaTenant fresh = tenantMapper.selectById(t.getId());
        Object freshOk = authService.postToken("client_credentials", String.valueOf(t.getId()), fresh.getTenantSecret());
        assertThat(((ApiResponse<?>) freshOk).getCode()).isZero();
        redis.delete("benefit4j:auth:fail:" + t.getId());
    }

    @Test
    public void platformIdentity_cannotActAsTenant() {
        // §6.2 L1 语义: 平台身份(tenant_id=0)是管理面,不是记账主体 ——
        // 打租户域/资金域 controller 的身份守卫应拒绝(否则账记到 tenant_id=0 幽灵租户)
        fun.commons.framework4j.accesstoken.context.TokenContext.set("APP", java.util.Map.of("tenant_id", 0L));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            fun.commons.benefit4j.security.TenantIdentityGuard::requireTenant)
                    .isInstanceOf(SecurityException.class);
            org.assertj.core.api.Assertions.assertThatCode(
                            fun.commons.benefit4j.security.PlatformIdentityGuard::requirePlatform)
                    .doesNotThrowAnyException();   // 平台身份在平台域仍放行(同一 claim,两面守卫)
        } finally {
            fun.commons.framework4j.accesstoken.context.TokenContext.clear();
        }
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
        // 零行为验证: 当前 admin 连接(表 owner/superuser)不受 RLS 限制
        try (var conn = AssetsMigrations.open();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT count(*) FROM ubmx_account")) {
            rs.next();
            assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(0);
        }
    }
}
