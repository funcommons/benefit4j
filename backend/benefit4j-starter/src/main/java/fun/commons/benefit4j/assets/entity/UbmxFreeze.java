package fun.commons.benefit4j.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 冻结原因明细(O5 分桶 / O14 单一数据源):
 * 主数据 = SUM(amount - used_amount) WHERE status='ACTIVE',
 * account.frozen 仅为缓存聚合,对账每日校验一致。
 */
@Getter
@Setter
@TableName(value = "ubmx_freeze", autoResultMap = true)
public class UbmxFreeze {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long accountId;

    /** 业务冻结单号(幂等键) */
    private String freezeNo;

    /** WITHDRAW | AFTER_SALE | RISK | PRE_CONSUME | OTHER */
    private String reason;

    private BigDecimal amount;

    /** 已使用部分(支持部分释放) */
    private BigDecimal usedAmount;

    /** ACTIVE | RELEASED | CONSUMED | EXPIRED */
    private String status;

    /** 可选,自动过期 */
    private java.time.OffsetDateTime expireTime;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object ext;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;
}
