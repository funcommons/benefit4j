package fun.commons.benefit4j.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

/** 权益项/集分页查询 (getPlatformItems/getPlatformBenefitSets) */
@Getter
@Setter
public class ItemQuery extends PageQuery {
    private Long appId;
    private String status;
    private Integer priorityMin;
    private Integer priorityMax;
    private OffsetDateTime createdAtStart;
    private OffsetDateTime createdAtEnd;
}
