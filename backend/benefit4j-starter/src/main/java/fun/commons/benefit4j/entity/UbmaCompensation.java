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
 * 客服补偿/调整流水表。记录人工对用户单项资产额度的手动增减操作。
 */
@Getter
@Setter
@TableName(value = "ubma_compensation", autoResultMap = true)
public class UbmaCompensation {
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    @OpenId
    @TableField("app_id")
    private Long appId;

    @OpenId
    private Long subscribeId;

    @OpenId
    private Long subsItemId;

    @OpenId
    private Long itemId;

    /** 调整的额度数量(正数) */
    private Integer adjustNum;

    /** 调整类型 (ADD:增加额度, REDUCE:减少额度) */
    private String adjustType;

    private String reason;

    /** 操作人(客服ID) */
    private String operator;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object ext;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String createBy;

    private String updateBy;

    @TableLogic
    private Short isDeleted;
}
