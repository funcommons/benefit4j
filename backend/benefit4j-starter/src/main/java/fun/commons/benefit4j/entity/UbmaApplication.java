package fun.commons.benefit4j.entity;

import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.sensitive.annotation.Sensitive;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.benefit4j.handler.BenefitAppSecretTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统应用/租户配置表。用于实现基于 AppId 的多业务数据物理/逻辑隔离。
 */
@Getter
@Setter
@TableName(value = "ubma_application", autoResultMap = true)
public class UbmaApplication {
    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /**
     * 租户全局唯一标识
     */

    /**
     * OAuth2 client_secret (HMAC-SHA256签名密钥)
     * 写入 DB 自动 AES-256-GCM 加密, 读取自动解密; 响应序列化时脱敏 (保留前2后4)
     */
    @TableField(typeHandler = BenefitAppSecretTypeHandler.class)
    @Sensitive(value = SensitiveRule.CUSTOM, pattern = "2,4,0")
    private String appSecret;

    /**
     * 应用名称
     */
    private String name;

    /**
     * 应用描述 (用途 / 业务范围)
     */
    private String description;

    /**
     * 状态 (ACTIVE:正常, INACTIVE:停用)
     */
    private String status;

    /**
     * 租户自定义透传动态属性 (底层必须建 GIN 索引)
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Object ext;

    /**
     * 创建时间
     */
    private java.time.OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private java.time.OffsetDateTime updatedAt;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 逻辑删除标志(0:未删, 1:已删)
     */
    @TableLogic
    private Short isDeleted;

}
