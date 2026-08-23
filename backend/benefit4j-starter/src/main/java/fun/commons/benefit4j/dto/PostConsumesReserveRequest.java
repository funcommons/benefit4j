package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostConsumesReserveRequest {
    @NotBlank(message = "userid不能为空")
    private String userid;

    @NotBlank(message = "item_id不能为空")
    private String itemId;

    @Min(value = 1, message = "consume_num最小为1")
    private Integer consumeNum = 1;

    @NotBlank(message = "external_order_id不能为空")
    @Size(max = 64, message = "external_order_id最长64字符")
    private String externalOrderId;

    /**
     * 预扣超时时间（秒），不传则使用配置默认值
     */
    @Min(value = 1, message = "timeout_seconds最小为1")
    private Integer timeoutSeconds;
}
