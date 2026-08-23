package fun.commons.benefit4j.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

/** 扣减流水分页查询 (getConsumes/getPlatformConsumes) */
@Getter
@Setter
public class ConsumeQuery extends PageQuery {
    private String userid;
    private String subsItemId;
    private String itemId;
    private String status;
    private String externalOrderId;
    private Integer consumeNumMin;
    private Integer consumeNumMax;
    private OffsetDateTime consumeTimeStart;
    private OffsetDateTime consumeTimeEnd;
}
