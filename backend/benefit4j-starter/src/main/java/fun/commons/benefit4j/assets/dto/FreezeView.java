package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class FreezeView {

    private Long id;
    private String freezeNo;
    private String reason;
    private String status;      // ACTIVE | RELEASED | CONSUMED | EXPIRED
    private BigDecimal amount;
    private BigDecimal usedAmount;
    private OffsetDateTime expireTime;
}
