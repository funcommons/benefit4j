package fun.commons.benefit4j.entity;

import fun.commons.framework4j.openid.annotation.OpenId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * 核销流水表。记录每次消耗特定单项资产的明确明细。
 */
@Getter
@Setter
@TableName(value = "ubma_consume", autoResultMap = true)
public class UbmaConsume {
    /**
     * 主键ID (消费流水号)
     */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /**
     * 归属的租户主键ID (关联 ubma_tenant.id)
     */
    @OpenId
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 实际扣减的订阅明细账单号
     */
    @OpenId
    private Long subsItemId;

    /**
     * 核销的权益项ID
     */
    @OpenId
    private Long itemId;

    /**
     * 外部业务核销单号(幂等防重)
     */
    private String externalOrderId;

    /**
     * 实际扣减或预扣的额度/次数
     */
    private Integer consumeNum;

    /**
     * 核销状态 (COMMITTED:已确认扣减, RESERVED:预扣冻结中, RELEASED:已释放取消)
     */
    private String status;

    /**
     * 核销/冻结发生的精确时间
     */
    private java.time.OffsetDateTime consumeTime;

    /**
     * 预扣冻结超时时间，超时后自动释放（仅RESERVED状态有效）
     */
    private java.time.OffsetDateTime expireTime;

    /**
     * 租户自定义透传动态属性 (底层必须建 GIN 索引)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
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
