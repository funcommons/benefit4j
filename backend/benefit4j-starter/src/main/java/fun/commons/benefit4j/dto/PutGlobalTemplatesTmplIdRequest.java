package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PutGlobalTemplatesTmplIdRequest {
    @Size(max = 64, message = "name最长64字符")
    private String name;

    @Min(value = 0, message = "duration最小为0")
    private Integer duration;

    private String durationUnit;

    @Min(value = 0, message = "priority最小为0")
    private Integer priority;

    @Min(value = 0, message = "quota最小为0")
    private Integer quota;

    private Integer refreshCycle;

    private String refreshCycleUnit;

    private List<PostGlobalTemplatesRequest.TemplateItemRef> refs;
}
