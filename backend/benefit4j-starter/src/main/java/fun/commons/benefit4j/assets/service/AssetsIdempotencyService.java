package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.entity.UbmxTxOrder;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxTxOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 幂等键释放(assets-design §4.3.2,O6):
 * SUCCESS → FAILED 仅用于运维纠错(键污染/快照损坏);释放后同号永久禁用
 * (引擎遇 FAILED 键抛 IDEMPOTENCY_CONFLICT,防双记账),新单必须换号。
 * 调用入口挂 OPS 域(OPS token + 审计);「先冲正后释放」为业务红线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetsIdempotencyService {

    private final UbmxTxOrderMapper txOrderMapper;

    public record ReleaseView(String extOrderId, String status, String reason) {
    }

    public ReleaseView release(Long tenantId, String extOrderId, String reason) {
        UbmxTxOrder old = txOrderMapper.selectOne(new LambdaQueryWrapper<UbmxTxOrder>()
                .eq(UbmxTxOrder::getTenantId, tenantId)
                .eq(UbmxTxOrder::getExtOrderId, extOrderId));
        if (old == null) {
            throw new AssetsException(AssetsException.PRE_CONSUME_NOT_FOUND, "幂等键不存在: " + extOrderId);
        }
        if ("FAILED".equals(old.getStatus())) {
            return new ReleaseView(extOrderId, "FAILED", reason);   // 幂等
        }
        txOrderMapper.releaseKey(tenantId, extOrderId, reason);
        log.warn("[assets][幂等释放] OPS 纠错: app={} extOrderId={} reason={} txId={}",
                tenantId, extOrderId, reason, old.getTxId());
        return new ReleaseView(extOrderId, "FAILED", reason);
    }
}
