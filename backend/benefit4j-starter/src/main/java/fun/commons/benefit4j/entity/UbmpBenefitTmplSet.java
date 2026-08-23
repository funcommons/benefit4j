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
@TableName(value = "ubmp_benefit_tmpl_set", autoResultMap = true)
public class UbmpBenefitTmplSet {
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    private String name;

    private Integer duration;
    private String durationUnit;
    private Integer priority;
    private Integer quota;
    private Integer refreshCycle;
    private String refreshCycleUnit;
    private String timingMode;
    private String quotaUnit;
    private String status;

    private String defaultSourceType;
    private Integer defaultExpiresInDays;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object ext;

    private java.time.OffsetDateTime createdAt;
    private java.time.OffsetDateTime updatedAt;
    private String createBy;
    private String updateBy;

    @TableLogic
    private Short isDeleted;
}
