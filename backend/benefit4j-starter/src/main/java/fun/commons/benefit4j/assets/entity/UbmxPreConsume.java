package fun.commons.benefit4j.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * TCC 预扣单。终态竞态 guard(O15): 过期回收与 settle 的 UPDATE 均带
 * WHERE status='RESERVED',先抢到者赢,后到方按幂等语义原样返回。
 */
@Getter
@Setter
@TableName("ubmx_pre_consume")
public class UbmxPreConsume {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long appId;

    /** 调用方幂等键 */
    private String requestId;

    /** 对应的 posting 主交易号(预扣腿已写入) */
    private Long txId;

    /** SOLO | DUAL(DUAL = user+tenant 同额双扣原子) */
    private String chargeMode;

    private Long userAccountId;

    private Long tenantAccountId;

    private String assetCode;

    /** 预扣估算额 */
    private BigDecimal estimated;

    /** 结算时回填实际 */
    private BigDecimal settledAmount;

    /** RESERVED | SETTLED | PARTIAL_SETTLED | REFUNDED | EXPIRED */
    private String status;

    /** 默认 NOW()+30min,scheduler 扫描过期 */
    private java.time.OffsetDateTime expireTime;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;
}
