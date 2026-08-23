package fun.commons.benefit4j.kms;

import fun.commons.benefit4j.entity.UbmaApplication;
import fun.commons.benefit4j.mapper.UbmaApplicationMapper;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.signature.service.SecretProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Signature 模块的 SecretProvider 实现
 * <p>
 * 三方调用 runtime API 时 X-Access-Key = app_id (OpenID 字符串),
 * 本 provider 解码为雪花 id 查 ubma_application, 返回 app_secret 明文
 * (EncryptedFieldTypeHandler 读时自动解密, 用作 HMAC-SHA256 签名密钥)。
 * <p>
 * 覆盖 framework4j 默认的 InMemorySecretProvider (@ConditionalOnMissingBean)。
 */
@Component
@RequiredArgsConstructor
public class BenefitAppSecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(BenefitAppSecretProvider.class);

    private final UbmaApplicationMapper applicationMapper;

    @Override
    public String getSecret(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) return null;
        try {
            Long appId = IdObfuscator.fromOpenId(accessKey);
            UbmaApplication app = applicationMapper.selectById(appId);
            if (app == null) {
                log.warn("[Signature] accessKey={} 解码 appId={} 无对应 application", accessKey, appId);
                return null;
            }
            // app_secret 走 EncryptedFieldTypeHandler 自动解密返回明文 HMAC 密钥
            return app.getAppSecret();
        } catch (Exception e) {
            log.warn("[Signature] getSecret 失败 accessKey={}: {}", accessKey, e.getMessage());
            return null;
        }
    }
}
