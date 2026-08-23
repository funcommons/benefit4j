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
 * 最基础的权益定义层。仅声明“权益本身是什么”，不包含任何额度与周期配置。
 */
@Getter
@Setter
@TableName(value = "ubma_benefit_item", autoResultMap = true)
public class UbmaBenefitItem {
    /**
     * 主键ID (权益项唯一标识)
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
     * 权益项名称 (如：免邮特权)
     */
    private String name;

    /**
     * 图标URL地址
     */
    private String icon;

    /**
     * 权益具体图文或文字说明
     */
    private String description;

    /**
     * 默认单次核销扣减基数
     */
    private Integer defaultDeduction;

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
