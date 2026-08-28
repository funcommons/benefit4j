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

        // reset-secret → 存量会话全部撤销
        Object resp = platformService.postTenantsTenantIdSecret(t.getId());
        assertThat(((ApiResponse<?>) resp).getCode()).isZero();
        assertThat(redis.hasKey(sessionKey(t, "APP"))).isFalse();
        assertThat(redis.hasKey(sessionKey(t, "OPS"))).isFalse();
    }
}
