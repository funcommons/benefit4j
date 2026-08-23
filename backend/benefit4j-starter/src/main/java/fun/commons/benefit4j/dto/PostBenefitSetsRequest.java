package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PostBenefitSetsRequest {
    @NotBlank(message = "name不能为空")
    @Size(min = 2, max = 50, message = "name长度2-50字符")
    private String name;

    @Min(value = 0, message = "duration最小为0")
    private Integer duration;

    @NotBlank(message = "duration_unit不能为空")
    private String durationUnit;

    @NotBlank(message = "timing_mode不能为空")
    private String timingMode = "RENEWAL";

    @Min(value = 0, message = "quota最小为0")
    private Integer quota = 0;

    private String quotaUnit = "次";

    private Integer refreshCycle;

    private String refreshCycleUnit;

    @Min(value = 0, message = "priority最小为0")
    private Integer priority = 0;

    @NotEmpty(message = "items不能为空")
    private List<BenefitSetItemRef> items;

    private Object ext;

    @Data
    public static class BenefitSetItemRef {
        @NotBlank(message = "item_id不能为空")
        private String itemId;

        @Min(value = 0, message = "quota最小为0")
        private Integer quota = 0;

        private Integer refreshCycle;

        private String refreshCycleUnit;
    }
}
