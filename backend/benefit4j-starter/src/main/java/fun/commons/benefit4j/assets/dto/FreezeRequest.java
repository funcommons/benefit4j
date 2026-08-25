package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 业务冻结请求(§2.5): 钱包中台提现申请 / 售后 / 风控 */
@Getter
@Setter
public class FreezeRequest {

    private Long appId;

    /** 业务冻结单号(幂等键) */
    private String freezeNo;

    /** 冻结账户引用,如 user:{id} */
    private String accountRef;

    private String assetCode;

    private BigDecimal amount;

    /** WITHDRAW | AFTER_SALE | RISK | PRE_CONSUME | OTHER */
    private String reason;

    /** 可选,自动过期秒数 */
    private Integer expireSeconds;
}
