package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostItemTemplatesRequest {
    @NotBlank(message = "name不能为空")
    @Size(min = 1, max = 64, message = "name长度1-64字符")
    private String name;

    @Size(max = 255, message = "icon最长255字符")
    private String icon;

    @Size(max = 255, message = "description最长255字符")
    private String description;

    @Min(value = 0, message = "default_deduction最小为0")
    private Integer defaultDeduction = 1;

    private String status = "ACTIVE";
}