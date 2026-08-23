package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PutBenefitSetsSetIdRequest {
    @Size(min = 2, max = 50, message = "name长度2-50字符")
    private String name;

    @Min(value = 0, message = "duration最小为0")
    private Integer duration;

    private String durationUnit;

    private String timingMode;

    @Min(value = 0, message = "quota最小为0")
    private Integer quota;

    private String quotaUnit;

    private Integer refreshCycle;

    private String refreshCycleUnit;

    @Min(value = 0, message = "priority最小为0")
    private Integer priority;

    private List<PostBenefitSetsRequest.BenefitSetItemRef> items;

    private Object ext;
}
