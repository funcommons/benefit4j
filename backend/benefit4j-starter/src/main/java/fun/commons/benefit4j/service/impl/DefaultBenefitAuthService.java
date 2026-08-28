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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnClass(AccessTokenGenerator.class)
public class DefaultBenefitAuthService implements BenefitAuthService {

    private final UbmaTenantMapper applicationMapper;
    private final ObjectProvider<AccessTokenGenerator> tokenGeneratorProvider;
    private final AccessTokenProperties tokenProperties;
    private final StringRedisTemplate redisTemplate;

    @Value("${benefit4j.security.platform.client-id:PLATFORM}")
    private String platformClientId;

    @Value("${benefit4j.security.platform.client-secret:}")
    private String platformClientSecret;

    /** 密钥轮换宽限期(§5.5): reset 后旧密钥仍可换 token 的时长(小时) */
    @Value("${benefit4j.security.secret-grace-hours:24}")
    private long secretGraceHours;

    private static final String TOKEN_TYPE = "APP";

    /** 换 token 防爆破(§8 #7): 连续失败 5 次锁 15min,成功清零 */
    static final String FAIL_KEY_PREFIX = "benefit4j:auth:fail:";
    static final int MAX_FAIL = 5;
    static final Duration LOCK_TTL = Duration.ofMinutes(15);

    @Override
    public Object postToken(String grantType, String clientId, String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            return ApiResponse.fail(400, "不支持的grant_type，仅支持client_credentials");
        }
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            return ApiResponse.fail(400, "client_id和client_secret不能为空");
        }

        String lockKey = FAIL_KEY_PREFIX + clientId;
        if (isLocked(lockKey)) {
            log.warn("[Auth] 换 token 已锁定(防爆破): clientId={}", clientId);
            return ApiResponse.fail(429, "认证失败次数过多，已锁定 15 分钟");
        }

        UbmaTenant app = resolveApp(clientId, clientSecret);
        if (app == null) {
            recordFailure(lockKey, clientId);
            return ApiResponse.fail(401, "client_id或client_secret无效");
        }
        redisTemplate.delete(lockKey);   // 成功即清零

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

    // ---------- 防爆破(§8 #7) ----------

    private boolean isLocked(String lockKey) {
        String fails = redisTemplate.opsForValue().get(lockKey);
        if (fails == null) return false;
        try {
            return Long.parseLong(fails) >= MAX_FAIL;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void recordFailure(String lockKey, String clientId) {
        Long n = redisTemplate.opsForValue().increment(lockKey);
        if (n != null && n == 1L) {
            redisTemplate.expire(lockKey, LOCK_TTL);   // 固定窗口: 首次失败起算 15min
        }
        log.warn("[Auth] 换 token 失败: clientId={}, 窗口内第 {} 次(达 {} 次锁定)", clientId, n, MAX_FAIL);
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
        if (app == null) return null;
        if (clientSecret.equals(app.getTenantSecret())) return app;          // 主密钥
        if (matchesGraceSecret(app, clientSecret)) return app;               // 宽限期内旧密钥(§5.5)
        return null;
    }

    /** 轮换宽限期双版本比对: 旧密钥命中且未过宽限期 → 视同认证成功(懒校验,无需清理任务) */
    private boolean matchesGraceSecret(UbmaTenant app, String clientSecret) {
        String prev = app.getTenantSecretPrev();
        if (prev == null || prev.isEmpty() || !clientSecret.equals(prev)) return false;
        var prevAt = app.getTenantSecretPrevAt();
        if (prevAt == null) return false;
        return prevAt.isAfter(java.time.OffsetDateTime.now().minusHours(secretGraceHours));
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
