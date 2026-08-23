package fun.commons.benefit4j.handler;

import fun.commons.benefit4j.context.SpringContextHolder;
import fun.commons.framework4j.sensitive.util.AesGcmCryptoUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ubma_application.app_secret 字段加密 TypeHandler (lazy key)
 * <p>
 * 父类 {@link fun.commons.framework4j.sensitive.typehandler.EncryptedFieldTypeHandler}
 * 构造时固定 keyBytes, 但 MyBatis 在 Mapper 解析阶段 (启动早期) 反射实例化 TypeHandler,
 * 此时 Spring 容器可能未就绪 → SpringContextHolder 取不到 sensitiveAesKeyBytes Bean →
 * 若构造时取 key 会 fallback 测试 key, 与运行时真 key 不一致 → 加密/解密 key 错位。
 * <p>
 * 本类改为 lazy: 无参构造不取 key, 每次 set/get 时从 SpringContextHolder 取
 * (运行时 context 必就绪), 保证 insert/select 用同一真 key。
 * <p>
 * 仅标在 app_secret 字段 (@TableField), 不污染其他 String 字段。
 */
@MappedTypes(String.class)
public class BenefitAppSecretTypeHandler extends BaseTypeHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(BenefitAppSecretTypeHandler.class);

    /** 无参构造 (MyBatis 反射用), 不在此取 key */
    public BenefitAppSecretTypeHandler() {
    }

    private byte[] key() {
        return SpringContextHolder.getBean("sensitiveAesKeyBytes", byte[].class);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, AesGcmCryptoUtil.encrypt(key(), parameter));
        } catch (Exception e) {
            throw new SQLException("app_secret 加密失败", e);
        }
    }

    private String safeDecrypt(String value) {
        if (value == null) return null;
        try {
            return AesGcmCryptoUtil.decrypt(key(), value);
        } catch (Exception e) {
            // 旧明文数据解密失败 → null (需走迁移接口加密)
            log.warn("[Sensitive] app_secret 解密失败 (明文/密钥不匹配): {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return safeDecrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return safeDecrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return safeDecrypt(cs.getString(columnIndex));
    }
}
