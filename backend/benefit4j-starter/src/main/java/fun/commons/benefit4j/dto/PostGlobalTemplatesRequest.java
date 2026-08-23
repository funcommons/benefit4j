package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PostGlobalTemplatesRequest {
    @NotBlank(message = "name不能为空")
    @Size(max = 64, message = "name最长64字符")
    private String name;

    @Min(value = 0, message = "duration最小为0")
    private Integer duration;

    @NotBlank(message = "duration_unit不能为空")
    private String durationUnit;

    @Min(value = 0, message = "priority最小为0")
    private Integer priority = 0;

    @Min(value = 0, message = "quota最小为0")
    private Integer quota = 0;

    private Integer refreshCycle;

    private String refreshCycleUnit;

    private List<TemplateItemRef> refs;

    @Data
    public static class TemplateItemRef {
        @NotBlank(message = "item_id不能为空")
        private String itemId;

        @Min(value = 0, message = "quota最小为0")
        private Integer quota = 0;

        private Integer refreshCycle;

        private String refreshCycleUnit;
    }
}
