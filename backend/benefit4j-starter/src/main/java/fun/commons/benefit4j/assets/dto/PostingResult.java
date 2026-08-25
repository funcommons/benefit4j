package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 记账结果。幂等重放时由 ubmx_tx_order.result_snapshot 原样重建(O10),
 * 重放结果与首次完全一致(txId 相同)。
 */
@Getter
@Setter
public class PostingResult {

    private Long txId;
    private String txType;
    private List<LegView> legs;

    @Getter
    @Setter
    public static class LegView {
        private Integer legSeq;
        private Long srcAccountId;
        private Long dstAccountId;
        private String assetCode;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
    }
}
