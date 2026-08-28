package fun.commons.benefit4j.entity;

import fun.commons.framework4j.openid.annotation.OpenId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * 精确跟踪用户具体单项资产消耗的明细账。
 */
@Getter
@Setter
@TableName(value = "ubma_subscribe_item", autoResultMap = true)
public class UbmaSubscribeItem {
    /**
     * 主键ID (订阅明细项单号)
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
     * 所属的主订阅单号
     */
    @OpenId
    private Long subscribeId;

    /**
     * 对应的单项权益ID
     */
    @OpenId
    private Long itemId;

    /**
     * 总消耗量 (生命周期内累计消耗)
     */
    private Integer totalConsumed;

    /**
     * 当期消耗量 (本刷新周期内已实际消耗)
     */
    private Integer periodConsumed;

    /**
     * 预扣减冻结量 (TCC/两阶段提交的预占额度)
     */
    private Integer frozenConsumed;

    /**
     * 当期额度上限快照 (从ref表拷贝的额度)
     */
    private Integer quotaLimit;

    /**
     * 下一次额度刷新结转的时间点
     */
    private java.time.OffsetDateTime nextRefreshTime;

    /**
     * 桶来源类型: SUBSCRIPTION / TOPUP / GRANT / COMPENSATION / PROMOTION
     */
    private String sourceType;

    /**
     * 桶过期时间, NULL = 永不过期
     */
    private java.time.OffsetDateTime expiresAt;

    /**
     * 单桶优先级, 数字越大越优先扣减 (继承自 ubma_benefit_set.priority 或补偿时指定)
     */
    private Integer bucketPriority;

    @Version
    private Integer version;

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
