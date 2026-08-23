package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostRefundsRequest {
    @NotBlank(message = "consume_id不能为空")
    private String consumeId;

    @NotBlank(message = "external_refund_id不能为空")
    @Size(max = 64, message = "external_refund_id最长64字符")
    private String externalRefundId;

    @Min(value = 1, message = "refund_num最小为1")
    private Integer refundNum = 1;
}
