package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostBenefitItemsRequest {
    @NotBlank(message = "name不能为空")
    @Size(min = 2, max = 50, message = "name长度2-50字符")
    private String name;

    private String icon;

    @Size(max = 2000, message = "description最长2000字符")
    private String description;

    @Min(value = 0, message = "default_deduction最小为0")
    private Integer defaultDeduction = 1;

    private String status = "ACTIVE";
}
