package fun.commons.benefit4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.benefit4j.entity.UbmaTenant;
import fun.commons.benefit4j.mapper.UbmaTenantMapper;
import fun.commons.benefit4j.service.BenefitAuthService;
import fun.commons.framework4j.accesstoken.config.AccessTokenProperties;
import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnClass(AccessTokenGenerator.class)
public class DefaultBenefitAuthService implements BenefitAuthService {

    private final UbmaTenantMapper applicationMapper;
    private final ObjectProvider<AccessTokenGenerator> tokenGeneratorProvider;
    private final AccessTokenProperties tokenProperties;

    @Value("${benefit4j.security.platform.client-id:PLATFORM}")
    private String platformClientId;

    @Value("${benefit4j.security.platform.client-secret:}")
    private String platformClientSecret;

    private static final String TOKEN_TYPE = "APP";

    @Override
    public Object postToken(String grantType, String clientId, String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            return ApiResponse.fail(400, "不支持的grant_type，仅支持client_credentials");
        }
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            return ApiResponse.fail(400, "client_id和client_secret不能为空");
        }

        UbmaTenant app = resolveApp(clientId, clientSecret);
        if (app == null) {
            return ApiResponse.fail(401, "client_id或client_secret无效");
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("tenant_id", app.getId());

        String token = tokenGeneratorProvider.getObject().generateToken(TOKEN_TYPE, claims);

        long expires = resolveExpireSeconds();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", token);
        result.put("token_type", "Bearer");
        result.put("expires_in", expires);
        return ApiResponse.success(result);
    }

    private boolean authenticate(String clientId, String clientSecret) {
        return resolveApp(clientId, clientSecret) != null;
    }

    /**
     * 查找匹配 clientId/secret 的租户, 用于把租户 id 写入 token claim。
     *
     * client_id 接受三种形式 (按优先级):
     *   1. UI 暴露的 OpenID (Base62 编码 + 校验位), 例如 "jZyCTw8xIjz4"
     *   2. 原始 Long id, 例如 "1"
     *   3. 租户 name (向后兼容), 例如 "Demo Tenant"
     *
     * 平台凭据 (benefit4j.security.platform.*) 不依赖 DB 行存在 —
     * 平台 token 的 tenant_id claim 用合成对象 (id=0) 即可, 平台控制器走 query string 的 tenant_id 而非 token claim.
     */
    private UbmaTenant resolveApp(String clientId, String clientSecret) {
        if (platformClientId.equals(clientId) && platformClientSecret.equals(clientSecret)) {
            return syntheticPlatformTenant();
        }
        UbmaTenant app = findAppByClientId(clientId);
        if (app == null || !clientSecret.equals(app.getTenantSecret())) return null;
        return app;
    }

    private UbmaTenant syntheticPlatformTenant() {
        UbmaTenant app = new UbmaTenant();
        app.setId(0L);
        app.setName(platformClientId);
        app.setTenantSecret(platformClientSecret);
        app.setStatus("ACTIVE");
        app.setDescription("Synthetic platform tenant — used when no DB row is provisioned");
        return app;
    }

    private UbmaTenant findAppByClientId(String clientId) {
        // 1. 尝试 OpenID 解码 (UI 显示的字符串形如 "jZyCTw8xIjz4")
        if (IdObfuscator.isValid(clientId)) {
            try {
                long rawId = IdObfuscator.fromOpenId(clientId);
                LambdaQueryWrapper<UbmaTenant> q = new LambdaQueryWrapper<>();
                q.eq(UbmaTenant::getId, rawId)
                        .eq(UbmaTenant::getStatus, "ACTIVE");
                UbmaTenant app = applicationMapper.selectOne(q);
                if (app != null) return app;
            } catch (Exception ignored) {
                // 退化到下面的查找
            }
        }
        // 2. 尝试原始 Long id
        if (clientId.matches("\\d+")) {
            try {
                long rawId = Long.parseLong(clientId);
                LambdaQueryWrapper<UbmaTenant> q = new LambdaQueryWrapper<>();
                q.eq(UbmaTenant::getId, rawId)
                        .eq(UbmaTenant::getStatus, "ACTIVE");
                UbmaTenant app = applicationMapper.selectOne(q);
                if (app != null) return app;
            } catch (NumberFormatException ignored) {
                // 退化到下面的查找
            }
        }
        // 3. 退化到按 name 查找 (向后兼容)
        LambdaQueryWrapper<UbmaTenant> query = new LambdaQueryWrapper<>();
        query.eq(UbmaTenant::getName, clientId)
                .eq(UbmaTenant::getStatus, "ACTIVE");
        return applicationMapper.selectOne(query);
    }

    private long resolveExpireSeconds() {
        AccessTokenProperties.Policy policy = tokenProperties.getPolicies() == null
                ? null : tokenProperties.getPolicies().get(TOKEN_TYPE);
        if (policy != null && policy.getExpireTime() != null) {
            return policy.getExpireTime();
        }
        return tokenProperties.getExpireTime() > 0 ? tokenProperties.getExpireTime() : 7200L;
    }
}
