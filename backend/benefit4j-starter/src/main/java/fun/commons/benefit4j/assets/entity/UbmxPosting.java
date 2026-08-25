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
 * 复式分录(append-only,月分区)。应用层无 UPDATE/DELETE 入口;
 * 唯一合法变更 = INIT → FAILED 幂等撤销标记(O6)。幂等闸在 ubmx_tx_order(O10)。
 */
@Getter
@Setter
@TableName(value = "ubmx_posting", autoResultMap = true)
public class UbmxPosting {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long appId;

    /** 业务交易号(同一笔业务多腿共享) */
    private Long txId;

    /** ISSUE | CONSUME | REFUND | TRANSFER | EXCHANGE | ADJUST | FREEZE | UNFREEZE */
    private String txType;

    /** 调用方幂等键(闸在 ubmx_tx_order,此处仅查询维度) */
    private String extOrderId;

    /** 同 tx 的腿序号(0,1,2...) */
    private Integer legSeq;

    private Long srcAccountId;

    private Long dstAccountId;

    private String assetCode;

    /** 金额恒正,方向由腿含义区分 */
    private BigDecimal amount;

    /** IN | OUT */
    private String direction;

    /** 该腿记账后 src/dst 余额(追溯用) */
    private BigDecimal balanceAfter;

    /** INIT | SUCCESS | FAILED(事务内直接以 SUCCESS 写入) */
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object ext;

    private java.time.OffsetDateTime createdAt;
}
