package fun.commons.benefit4j.entity;

import fun.commons.framework4j.openid.annotation.OpenId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "ubmp_benefit_tmpl_ref", autoResultMap = true)
public class UbmpBenefitTmplRef {
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    @OpenId
    private Long setId;

    @OpenId
    private Long itemId;

    private Integer quota;
    private Integer refreshCycle;
    private String refreshCycleUnit;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object ext;

    private java.time.OffsetDateTime createdAt;
    private java.time.OffsetDateTime updatedAt;
    private String createBy;
    private String updateBy;

    @TableLogic
    private Short isDeleted;
}
