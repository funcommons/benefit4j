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
 * 资产回滚流水。当上游退款时，基于原消费记录退回已消耗额度。
 */
@Getter
@Setter
@TableName(value = "ubma_refund", autoResultMap = true)
public class UbmaRefund {
    /**
     * 主键ID (回滚流水号)
     */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /**
     * 归属的应用主键ID (关联 ubma_application.id)
     */
    @OpenId
    @TableField("app_id")
    private Long appId;

    /**
     * 关联的原消费流水号
     */
    @OpenId
    private Long consumeId;

    /**
     * 外部业务退款单号(幂等防重)
     */
    private String externalRefundId;

    /**
     * 实际退回的额度/次数
     */
    private Integer refundNum;

    /**
     * 退回发生的精确时间
     */
    private java.time.OffsetDateTime refundTime;

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
