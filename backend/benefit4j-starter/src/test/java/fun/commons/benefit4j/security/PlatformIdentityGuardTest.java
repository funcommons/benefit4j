package fun.commons.benefit4j.security;

import fun.commons.framework4j.accesstoken.context.TokenContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 平台域身份守卫纯逻辑(§5.3 方案 B): tenant_id==0 放行,其余(非 0 / 缺失 / 非法)全拒。
 */
class PlatformIdentityGuardTest {

    @AfterEach
    void clear() {
        TokenContext.clear();
    }

    @Test
    void platformIdentity_zeroLong_passes() {
        TokenContext.set("APP", Map.of("tenant_id", 0L));
        assertThatCode(PlatformIdentityGuard::requirePlatform).doesNotThrowAnyException();
    }

    @Test
    void platformIdentity_zeroString_passes() {
        // 平台合成租户登录链: claim 序列化后可能是字符串 "0"
        TokenContext.set("APP", Map.of("tenant_id", "0"));
        assertThatCode(PlatformIdentityGuard::requirePlatform).doesNotThrowAnyException();
    }

    @Test
    void tenantIdentity_nonZero_rejected() {
        TokenContext.set("APP", Map.of("tenant_id", 1234567890123456789L));
        assertThatThrownBy(PlatformIdentityGuard::requirePlatform)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("平台身份");
    }

    @Test
    void missingClaim_rejected() {
        TokenContext.set("APP", Map.of("other", 1L));
        assertThatThrownBy(PlatformIdentityGuard::requirePlatform)
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void malformedClaim_rejected() {
        TokenContext.set("APP", Map.of("tenant_id", "not-a-number"));
        assertThatThrownBy(PlatformIdentityGuard::requirePlatform)
                .isInstanceOf(SecurityException.class);
    }
}
