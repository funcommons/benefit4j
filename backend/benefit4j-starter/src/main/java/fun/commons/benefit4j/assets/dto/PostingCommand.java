package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 记账指令: 一笔业务 = N 条腿,单事务原子(assets-design §3.1)。
 * 账户引用格式:
 *   主体户  user:{id} | tenant:{id} | merchant:{id} | platform:{id} | external:{id}
 *   边界户  issue:{asset} | fee:{asset} | exchange:{asset} | credit:{asset} | world:{channel}
 */
@Getter
@Setter
public class PostingCommand {

    private Long tenantId;

    /** 调用方幂等键,经 ubmx_tx_order 抢占(O10) */
    private String extOrderId;

    /** ISSUE | CONSUME | REFUND | TRANSFER | EXCHANGE | ADJUST */
    private String txType;

    private List<LegSpec> legs;

    /** 业务透传,落 posting 查询维度 */
    private Object ext;

    @Getter
    @Setter
    public static class LegSpec {
        private String src;
        private String dst;
        private String assetCode;
        private BigDecimal amount;
    }
}
