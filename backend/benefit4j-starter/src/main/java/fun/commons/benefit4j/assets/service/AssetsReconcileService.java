package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.mapper.UbmxReconcileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * T+1 对账(assets-design §8.4):
 *   1) 资产恒等式(O11 口径): Σ NORMAL balance == Σ(BOUNDARY→NORMAL) − Σ(NORMAL→BOUNDARY)
 *   2) O14 冻结一致性全量: account.frozen == Σ(ACTIVE 冻结明细)
 *   3) 差异入差错池(OPEN)+ error 告警日志
 *   4) 日终快照(当前全量校验;数据量增长后从快照起算增量)
 * scheduler 每日 02:00 Asia/Shanghai 跑全资产;runOnce 可手动触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetsReconcileService {

    private final UbmxReconcileMapper reconcileMapper;
    private final AssetRegistryService registry;

    public record Report(boolean assetIdentityOk, boolean freezeConsistencyOk, int diffCount,
                         BigDecimal expected, BigDecimal actual) {
    }

    /** 对账单个资产(事务保证差错池与快照原子) */
    @Transactional
    public Report runOnce(Long tenantId, String assetCode) {
        int diffCount = 0;

        // 1. 恒等式
        BigDecimal expected = nz(reconcileMapper.expectedNormalBalance(tenantId, assetCode));
        BigDecimal actual = nz(reconcileMapper.actualNormalBalance(tenantId, assetCode));
        boolean identityOk = expected.compareTo(actual) == 0;
        if (!identityOk) {
            diffCount++;
            reconcileMapper.insertDiff(tenantId, "ASSET_IDENTITY", assetCode, null, expected, actual);
            log.error("[assets][对账] 资产恒等式破坏! app={} asset={} expected={} actual={}",
                    tenantId, assetCode, expected, actual);
        }

        // 2. O14 冻结一致性(全量)
        List<Map<String, Object>> mismatches = reconcileMapper.freezeMismatches(tenantId);
        boolean freezeOk = mismatches.isEmpty();
        for (Map<String, Object> m : mismatches) {
            diffCount++;
            Long accountId = ((Number) m.get("id")).longValue();
            reconcileMapper.insertDiff(tenantId, "FREEZE_MISMATCH", assetCode, accountId,
                    toDecimal(m.get("expected")), toDecimal(m.get("frozen")));
            log.error("[assets][对账] 冻结不一致(O14): app={} accountId={} expected={} actual={}",
                    tenantId, accountId, m.get("expected"), m.get("frozen"));
        }

        // 3. 快照
        reconcileMapper.writeSnapshot(tenantId);

        return new Report(identityOk, freezeOk, diffCount, expected, actual);
    }

    /** scheduler 入口: 全资产对账 */
    public int runAllAssets(Long tenantId) {
        int totalDiffs = 0;
        for (var asset : registry.list(null, "ACTIVE")) {
            Report r = runOnce(tenantId, asset.getCode());
            totalDiffs += r.diffCount();
        }
        return totalDiffs;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal toDecimal(Object v) {
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }
}
