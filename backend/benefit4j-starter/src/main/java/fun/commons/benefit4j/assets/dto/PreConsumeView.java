package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 预扣单视图: 幂等重放与终态查询共用 */
@Getter
@Setter
public class PreConsumeView {

    private Long id;
    private String requestId;
    private String status;          // RESERVED | SETTLED | PARTIAL_SETTLED | REFUNDED | EXPIRED
    private BigDecimal estimated;
    private BigDecimal settledAmount;
    private OffsetDateTime expireTime;
}
