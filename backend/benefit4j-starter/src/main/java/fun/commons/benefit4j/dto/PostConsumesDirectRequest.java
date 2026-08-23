package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostConsumesDirectRequest {
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
     * 是否允许部分成功: 默认 false (严格模式, 总额不足整笔拒绝)
     * true 时按多桶排空扣到能扣的为止, 响应返回实际扣除量
     */
    private Boolean partialAllowed = false;
}
