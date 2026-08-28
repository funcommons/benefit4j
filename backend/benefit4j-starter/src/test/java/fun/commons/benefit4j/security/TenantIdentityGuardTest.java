package fun.commons.benefit4j.security;

import fun.commons.framework4j.accesstoken.context.TokenContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 租户身份守卫纯逻辑: tenant_id>0 放行;平台身份(0)/缺失/非法全拒
 * —— 平台身份不是记账主体(L1 语义的资金操作须走自身租户凭据)。
 */
class TenantIdentityGuardTest {

    @AfterEach
    void clear() {
        TokenContext.clear();
    }

    @Test
    void realTenant_passes() {
        TokenContext.set("APP", Map.of("tenant_id", 123L));
        assertThatCode(TenantIdentityGuard::requireTenant).doesNotThrowAnyException();
        TokenContext.set("APP", Map.of("tenant_id", "456"));
        assertThatCode(TenantIdentityGuard::requireTenant).doesNotThrowAnyException();
    }

    @Test
    void platformIdentity_rejected() {
        TokenContext.set("APP", Map.of("tenant_id", 0L));
        assertThatThrownBy(TenantIdentityGuard::requireTenant)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("记账主体");
    }

    @Test
    void missingOrMalformed_rejected() {
        TokenContext.set("APP", Map.of("other", 1L));
        assertThatThrownBy(TenantIdentityGuard::requireTenant).isInstanceOf(SecurityException.class);
        TokenContext.set("APP", Map.of("tenant_id", "abc"));
        assertThatThrownBy(TenantIdentityGuard::requireTenant).isInstanceOf(SecurityException.class);
    }
}
