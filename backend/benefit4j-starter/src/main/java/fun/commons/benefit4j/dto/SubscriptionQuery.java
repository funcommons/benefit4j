package fun.commons.benefit4j.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

/** 订阅分页查询 (getSubscriptions/getPlatformSubscriptions) */
@Getter
@Setter
public class SubscriptionQuery extends PageQuery {
    private String userid;
    private String setId;
    private String status;
    private String externalOrderId;
    private OffsetDateTime dateBeginStart;
    private OffsetDateTime dateBeginEnd;
    private OffsetDateTime createdAtStart;
    private OffsetDateTime createdAtEnd;
}
