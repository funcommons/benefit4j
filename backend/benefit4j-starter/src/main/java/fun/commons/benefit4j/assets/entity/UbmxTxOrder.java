package fun.commons.benefit4j.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * 幂等闸(O10)。不分区小表,UNIQUE(tenant_id, ext_order_id) 是真正的幂等闸;
 * PostingService 事务第一步 INSERT 抢占,冲突读旧行:
 * 同参返回 result_snapshot,异参抛 IDEMPOTENCY_CONFLICT。
 */
@Getter
@Setter
@TableName(value = "ubmx_tx_order", autoResultMap = true)
public class UbmxTxOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    /** 调用方幂等键(issueOrderId / requestId) */
    private String extOrderId;

    /** ISSUE | CONSUME | REFUND | ... */
    private String txType;

    /** 抢占成功后分配的业务交易号 */
    private Long txId;

    /** SUCCESS | FAILED */
    private String status;

    /** 首次成功响应快照(重放时原样返回) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object resultSnapshot;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;
}
