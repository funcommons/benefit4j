package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 预扣请求(assets-design §4.4) */
@Getter
@Setter
public class PreConsumeRequest {

    private Long tenantId;

    /** 调用方幂等键,贯穿 pre-consume/settle/refund 三阶段 */
    private String requestId;

    /** 预扣账户引用,如 user:{id} */
    private String accountRef;

    private String assetCode;

    /** 预扣估算额(>0) */
    private BigDecimal estimated;

    /** 预扣有效期(秒),默认 1800 */
    private Integer expireSeconds;
}
