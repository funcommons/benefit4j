package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostCompensationsRequest {
    @NotBlank(message = "subscribe_id不能为空")
    private String subscribeId;

    @NotBlank(message = "subs_item_id不能为空")
    private String subsItemId;

    @NotBlank(message = "item_id不能为空")
    private String itemId;

    @Min(value = 1, message = "adjust_num最小为1")
    private Integer adjustNum;

    private String adjustType = "ADD";

    @Size(max = 255, message = "reason最长255字符")
    private String reason;

    @Size(max = 64, message = "operator最长64字符")
    private String operator;

    /**
     * ADD 时新桶的 source_type, 默认 COMPENSATION
     * 例如: TOPUP (独立充值补充) / GRANT (运营发放) / COMPENSATION (人工补偿)
     */
    private String sourceType;

    /**
     * ADD 时新桶的 bucket_priority, 默认 0
     * 数字越大越优先扣减
     */
    private Integer priority;

    /**
     * ADD 时新桶的过期时间, NULL = 永不过期
     */
    private java.time.OffsetDateTime expiresAt;
}
