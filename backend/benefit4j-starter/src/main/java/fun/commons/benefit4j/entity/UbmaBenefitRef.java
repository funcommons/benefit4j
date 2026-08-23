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
 * 将“定义”装配进“产品”的核心映射表。承载特定产品打包下的具体“分配额度”与“刷新周期”。
 */
@Getter
@Setter
@TableName(value = "ubma_benefit_ref", autoResultMap = true)
public class UbmaBenefitRef {
    /**
     * 主键ID
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
     * 关联的权益集ID
     */
    @OpenId
    private Long setId;

    /**
     * 包含的具体权益项ID
     */
    @OpenId
    private Long itemId;

    /**
     * 在当前产品包中分配的周期总额度
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
