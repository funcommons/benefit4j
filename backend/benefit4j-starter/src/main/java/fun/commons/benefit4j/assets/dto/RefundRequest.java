package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

/** 退款请求: requestId 与 pre-consume 相同(幂等贯穿三阶段) */
@Getter
@Setter
public class RefundRequest {

    private Long tenantId;

    private String requestId;
}
