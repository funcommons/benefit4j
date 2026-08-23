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
 * 面向用户的商业化产品包装（如VIP包月会员卡）。
 */
@Getter
@Setter
@TableName(value = "ubma_benefit_set", autoResultMap = true)
public class UbmaBenefitSet {
    /**
     * 主键ID (权益集唯一标识)
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
     * 集合名称 (如：加量尊享包)
     */
    private String name;

    /**
     * 整体有效时长 (0代表永久)
     */
    private Integer duration;

    /**
     * 时长单位 (day/month/year)
     */
    private String durationUnit;

    /**
     * 核销优先级 (数字越大越优先扣减)
     */
    private Integer priority;

    /**
     * 集合级的总额度 (0表示不限量)
     */
    private Integer quota;

    /**
     * 额度刷新周期数
     */
    private Integer refreshCycle;

    /**
     * 额度刷新周期单位 (day/month/year)
     */
    private String refreshCycleUnit;

    /**
     * 计时方式 (RENEWAL:续期, CURRENT:当期)
     */
    private String timingMode;

    /**
     * 计量单位 (份/次/个/分)
     */
    private String quotaUnit;

    /**
     * 状态 (ACTIVE:正常, INACTIVE:停用)
     */
    private String status;

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
