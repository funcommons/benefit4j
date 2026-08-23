package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostConsumesCommitRequest {
    @NotBlank(message = "external_order_id不能为空")
    @Size(max = 64, message = "external_order_id最长64字符")
    private String externalOrderId;
}
