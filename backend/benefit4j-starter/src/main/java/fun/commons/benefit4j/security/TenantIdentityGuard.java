package fun.commons.benefit4j.security;

import fun.commons.framework4j.accesstoken.context.TokenContext;

/**
 * 租户身份守卫(中间件中台租户设计 §5.3/§6.2 信任分级的代码化):
 * 租户域/资金域入口要求真实租户身份(tenant_id > 0)。
 *
 * 平台身份(tenant_id==0,合成租户)语义 = 平台域管理面 —— 不是记账主体:
 * 若放行,issue 等资金接口会把账记到 tenant_id=0 名下(幽灵租户账本,
 * 平台域查不到、对账恒等式被污染)。L1(平台密钥直发)消费方的资金操作
 * 必须以其自身租户身份(tenant_secret 换 token)进行,而非平台身份。
 */
public final class TenantIdentityGuard {

    private TenantIdentityGuard() {
    }

    /** 租户域/资金域入口统一校验;非真实租户身份(tenant_id<=0 / 缺失 / 非法)抛 SecurityException(403) */
    public static void requireTenant() {
        Object claim = TokenContext.getClaim(PlatformIdentityGuard.CLAIM_TENANT_ID);
        long tenantId;
        if (claim instanceof Number n) {
            tenantId = n.longValue();
        } else if (claim != null) {
            try {
                tenantId = Long.parseLong(String.valueOf(claim));
            } catch (NumberFormatException e) {
                tenantId = -1;
            }
        } else {
            tenantId = -1;
        }
        if (tenantId <= 0L) {
            throw new SecurityException("租户域需要真实租户身份(tenant_id>0),平台身份(tenant_id=0)不可作为记账主体");
        }
    }
}
