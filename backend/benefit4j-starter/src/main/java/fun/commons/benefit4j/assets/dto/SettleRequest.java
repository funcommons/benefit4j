package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 结算请求: actual 为真实发生额,与预估额的差自动回补/补扣(O15) */
@Getter
@Setter
public class SettleRequest {

    private Long tenantId;

    /** 与 pre-consume 相同的幂等键 */
    private String requestId;

    private BigDecimal actual;
}
