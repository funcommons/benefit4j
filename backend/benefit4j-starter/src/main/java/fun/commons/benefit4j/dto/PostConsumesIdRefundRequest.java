package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动退减请求: 将已 COMMIT 的 consume 流水逆向回滚, 把 consume_num 退回对应 subscribe_item 的总/期额度.
 * 业务语义区别于"释放冻结" (RESERVED -> RELEASED), 用新 status REFUNDED 标识.
 */
@Data
public class PostConsumesIdRefundRequest {
    @Size(max = 256, message = "退减原因最长256字符")
    private String reason;

    @Size(max = 64, message = "操作人标识最长64字符")
    private String operator;
}
