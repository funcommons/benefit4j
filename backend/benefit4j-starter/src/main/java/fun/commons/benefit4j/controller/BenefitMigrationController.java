package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.entity.UbmaTenant;
import fun.commons.benefit4j.mapper.UbmaTenantMapper;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import fun.commons.framework4j.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次性数据迁移控制器 (V1.2.2 tenant_secret 全量加密)
 * <p>
 * sensitive 模块上线后, tenant_secret 走 EncryptedFieldTypeHandler 自动加解密,
 * 但库里历史明文行读出会解密失败 → null。本接口用 JdbcTemplate 绕过 TypeHandler
 * 读原始值, 判断是否已加密, 对明文行用 updateById (走 TypeHandler) 加密回写。
 * <p>
 * 开关: {@code benefit4j.migration.tenant-secret-encrypt=true} (默认关), 运维手动开一次跑完即关。
 */
@RestController
@RequiredArgsConstructor
@RequiresToken(value = "OPS", type = "access")
@ConditionalOnProperty(prefix = "benefit4j.migration", name = "tenant-secret-encrypt", havingValue = "true")
public class BenefitMigrationController {

    private static final Logger log = LoggerFactory.getLogger(BenefitMigrationController.class);

    private final UbmaTenantMapper applicationMapper;
    private final JdbcTemplate jdbc;
    @Qualifier("sensitiveAesKeyBytes")
    private final byte[] keyBytes;

    @PostMapping("/benefit/api/v1/ops/migrate/encrypt-tenant-secret")
    public Object postMigrateEncryptTenantSecret() {
        // 1. 绕过 TypeHandler 直读原始 tenant_secret (可能是明文或密文)
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tenant_secret FROM ubma_tenant WHERE is_deleted = 0");
        int migrated = 0, skipped = 0, failed = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String raw = (String) row.get("tenant_secret");
            if (raw == null || raw.isBlank()) { skipped++; continue; }
            // 2. 尝试解密: 成功=已加密跳过, 失败=明文需迁移
            try {
                AesGcmCryptoUtil.decrypt(keyBytes, raw);
                skipped++; continue;  // 已加密
            } catch (Exception notEncrypted) {
                // 明文, 继续迁移
            }
            try {
                // 3. selectById 拿 entity (tenant_secret 字段读为 null 因明文解密失败),
                //    setTenantSecret(原始明文) + updateById → TypeHandler 加密写入
                UbmaTenant app = applicationMapper.selectById(id);
                if (app == null) { failed++; continue; }
                app.setTenantSecret(raw);
                applicationMapper.updateById(app);
                migrated++;
            } catch (Exception e) {
                log.warn("[Migration] tenant_secret 加密失败 id={}: {}", id, e.getMessage());
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("migrated", migrated);
        result.put("skipped_already_encrypted", skipped);
        result.put("failed", failed);
        log.info("[Migration] tenant_secret 加密完成: {}", result);
        return ApiResponse.success(result);
    }
}
