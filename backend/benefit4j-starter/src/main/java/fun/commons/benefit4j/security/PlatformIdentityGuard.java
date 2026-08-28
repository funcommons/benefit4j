package fun.commons.benefit4j.security;

import fun.commons.framework4j.accesstoken.context.TokenContext;

/**
 * 平台域身份守卫(中间件中台租户设计 §5.3 方案 B):
 * 平台域(/platform/**)仅平台身份(合成租户 tenant_id==0)可达;租户 token(非 0)一律 403。
 * 双 controller 的 @ModelAttribute 调用 —— Spring 保证在每个请求进入 handler 前执行,
 * 时序在框架 token 拦截器之后(此时 TokenContext 已填充)。
 *
 * 反例教训(benefit4j 越权缺口): 平台域与租户域同为 APP 型且入口不校验零值时,
 * 任何租户 token 都能调平台域管理所有租户 —— 本守卫即为此而生。
 */
public final class PlatformIdentityGuard {

    public static final String CLAIM_TENANT_ID = "tenant_id";

    private PlatformIdentityGuard() {
    }

    /** 平台域入口统一校验;非平台身份抛 SecurityException(由 ExceptionHandler 映射 403) */
    public static void requirePlatform() {
        Object claim = TokenContext.getClaim(CLAIM_TENANT_ID);
        long tenantId;
        if (claim instanceof Number n) {
            tenantId = n.longValue();
        } else if (claim != null) {
            try {
                tenantId = Long.parseLong(String.valueOf(claim));
            } catch (NumberFormatException e) {
                tenantId = -1;   // 非法 claim 视为无平台身份,拒绝
            }
        } else {
            tenantId = -1;       // 无 tenant_id claim(非本体系 token)拒绝
        }
        if (tenantId != 0L) {
            throw new SecurityException("平台域需要平台身份(tenant_id=0),拒绝租户身份访问");
        }
    }
}
