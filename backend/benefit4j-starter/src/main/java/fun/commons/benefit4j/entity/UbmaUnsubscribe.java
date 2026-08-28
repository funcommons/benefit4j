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
 * 退订快照表。记录退订某一有效订阅资产时的操作凭证。
 */
@Getter
@Setter
@TableName(value = "ubma_unsubscribe", autoResultMap = true)
public class UbmaUnsubscribe {
    /**
     * 主键ID (退订流水号)
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
     * 被退订的主单号
     */
    @OpenId
    private Long subscribeId;

    /**
     * 外部系统的退订单号(幂等防重)
     */
    private String externalOrderId;

    /**
     * 退订原因
     */
    private String reason;

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
