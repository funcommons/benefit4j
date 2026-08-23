package fun.commons.benefit4j.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * 对账请求 (T+1 三方订单 vs benefit4j 流水比对)
 */
@Getter
@Setter
public class PostReconcileRequest {
    private Long appId;
    private List<ReconcileOrder> orders;

    @Getter
    @Setter
    public static class ReconcileOrder {
        /** 三方订单号 (对应 ubma_consume.external_order_id) */
        private String externalOrderId;
        /** 三方期望状态 (COMMITTED/RESERVED/REFUNDED) */
        private String expectedStatus;
        /** 三方期望扣减数量 */
        private Integer expectedConsumeNum;
    }
}
