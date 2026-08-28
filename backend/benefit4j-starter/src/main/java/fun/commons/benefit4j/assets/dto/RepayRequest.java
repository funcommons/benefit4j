package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 还款请求(F1): 分录 user:{ref} → credit:{asset}(BOUNDARY),负余额回正 */
@Getter
@Setter
public class RepayRequest {

    private Long tenantId;

    /** 幂等键 */
    private String requestId;

    /** 还款账户引用,如 user:{id} */
    private String accountRef;

    private String assetCode;

    private BigDecimal amount;
}
