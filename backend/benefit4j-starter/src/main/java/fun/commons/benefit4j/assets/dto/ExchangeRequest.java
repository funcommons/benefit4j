package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 兑换请求(§3.2): 汇率由调用方定价,资产域只记账(FROM→exchange:FROM + exchange:TO→TO) */
@Getter
@Setter
public class ExchangeRequest {

    private Long appId;

    /** 幂等键 */
    private String orderId;

    /** 兑换发起方账户引用,如 user:{id} */
    private String ownerRef;

    private String fromAssetCode;

    private String toAssetCode;

    private BigDecimal fromAmount;

    private BigDecimal toAmount;
}
