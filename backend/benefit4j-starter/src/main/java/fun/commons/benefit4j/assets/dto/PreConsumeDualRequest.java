package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** DUAL 双账户预扣请求(§3.3: user + tenant 同额双扣原子,无中间户) */
@Getter
@Setter
public class PreConsumeDualRequest {

    private Long appId;

    /** 幂等键,贯穿三阶段 */
    private String requestId;

    /** 用户账户引用,如 user:{id} */
    private String userAccountRef;

    /** 租户账户引用,如 tenant:{id} */
    private String tenantAccountRef;

    private String assetCode;

    /** 双侧同额预扣估算 */
    private BigDecimal estimated;

    private Integer expireSeconds;
}
