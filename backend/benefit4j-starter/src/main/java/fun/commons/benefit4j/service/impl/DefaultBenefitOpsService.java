package fun.commons.benefit4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.benefit4j.service.BenefitOpsService;
import fun.commons.framework4j.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultBenefitOpsService implements BenefitOpsService {

    private final UbmaSubscribeMapper subscribeMapper;
    private final UbmaSubscribeItemMapper subscribeItemMapper;
    private final UbmaBenefitSetMapper benefitSetMapper;

    @Override
    public Object getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", OffsetDateTime.now());
        return ApiResponse.success(health);
    }

    @Override
    public Object getMetrics() {
        // Basic operational metrics
        LambdaQueryWrapper<UbmaSubscribe> activeQuery = new LambdaQueryWrapper<>();
        activeQuery.eq(UbmaSubscribe::getStatus, "ACTIVE");
        long activeSubscriptions = subscribeMapper.selectCount(activeQuery);

        LambdaQueryWrapper<UbmaSubscribe> exhaustedQuery = new LambdaQueryWrapper<>();
        exhaustedQuery.eq(UbmaSubscribe::getStatus, "EXHAUSTED");
        long exhaustedSubscriptions = subscribeMapper.selectCount(exhaustedQuery);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("active_subscriptions", activeSubscriptions);
        metrics.put("exhausted_subscriptions", exhaustedSubscriptions);
        metrics.put("timestamp", OffsetDateTime.now());
        return ApiResponse.success(metrics);
    }

    @Override
    public Object postCacheEvict(PostCacheEvictRequest req) {
        // Cache eviction is handled by Redis; this is a no-op for local mode
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evicted", true);
        result.put("cache_type", req.getCacheType() != null ? req.getCacheType() : "ALL");
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object postJobsRefreshCycles(PostJobsRefreshCyclesRequest req) {
        boolean dryRun = req.getDryRun() != null && req.getDryRun();
        OffsetDateTime now = OffsetDateTime.now();
        int refreshedCount = 0;
        int expiredCount = 0;

        // 1. 查找需要刷新的订阅 (nextRefreshTime <= now 且 status in [ACTIVE, EXHAUSTED])
        LambdaQueryWrapper<UbmaSubscribe> refreshQuery = new LambdaQueryWrapper<>();
        refreshQuery.le(UbmaSubscribe::getNextRefreshTime, now)
                .in(UbmaSubscribe::getStatus, "ACTIVE", "EXHAUSTED");
        if (req.getAppId() != null) {
            refreshQuery.eq(UbmaSubscribe::getAppId, req.getAppId());
        }
        List<UbmaSubscribe> toRefresh = subscribeMapper.selectList(refreshQuery);

        for (UbmaSubscribe sub : toRefresh) {
            // 检查是否已过期
            if (sub.getDateEnd() != null && sub.getDateEnd().isBefore(now)) {
                if (!dryRun) {
                    sub = subscribeMapper.selectById(sub.getId()); // re-read for version
                    if (sub == null) { expiredCount++; continue; }
                    LambdaUpdateWrapper<UbmaSubscribe> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(UbmaSubscribe::getId, sub.getId())
                            .eq(UbmaSubscribe::getVersion, sub.getVersion())
                            .set(UbmaSubscribe::getStatus, "EXPIRED")
                            .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                            .set(UbmaSubscribe::getUpdatedAt, now);
                    subscribeMapper.update(null, wrapper);
                }
                expiredCount++;
                continue;
            }

            if (!dryRun) {
                // 跳过有冻结额度的订阅（存在未决的TCC预留）
                sub = subscribeMapper.selectById(sub.getId()); // re-read for latest state
                if (sub == null) continue;
                if (sub.getFrozenConsumed() != null && sub.getFrozenConsumed() > 0) {
                    continue;
                }

                // 刷新集合级周期
                UbmaBenefitSet set = benefitSetMapper.selectById(sub.getSetId());
                OffsetDateTime nextRefresh = set != null
                        ? calculateNextRefreshTime(now, set.getRefreshCycle(), set.getRefreshCycleUnit())
                        : null;

                LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
                subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                        .eq(UbmaSubscribe::getVersion, sub.getVersion())
                        .set(UbmaSubscribe::getPeriodConsumed, 0)
                        .set(UbmaSubscribe::getNextRefreshTime, nextRefresh)
                        .set(UbmaSubscribe::getStatus, "ACTIVE") // EXHAUSTED → ACTIVE on refresh
                        .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                        .set(UbmaSubscribe::getUpdatedAt, now);
                subscribeMapper.update(null, subWrapper);

                // 刷新条目级周期
                LambdaQueryWrapper<UbmaSubscribeItem> itemQuery = new LambdaQueryWrapper<>();
                itemQuery.eq(UbmaSubscribeItem::getSubscribeId, sub.getId());
                List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(itemQuery);
                for (UbmaSubscribeItem item : items) {
                    LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
                    itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                            .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                            .set(UbmaSubscribeItem::getPeriodConsumed, 0)
                            .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                            .set(UbmaSubscribeItem::getUpdatedAt, now);
                    subscribeItemMapper.update(null, itemWrapper);
                }
            }
            refreshedCount++;
        }

        // 桶过期观测 (V1.2.0 多源桶上线后): 候选查询已过滤 expires_at > now,
        // 但运维需要看到「有多少桶已过期」用于清理决策. 不改数据, 只统计.
        LambdaQueryWrapper<UbmaSubscribeItem> expiredBucketQuery = new LambdaQueryWrapper<>();
        expiredBucketQuery.isNotNull(UbmaSubscribeItem::getExpiresAt)
                .lt(UbmaSubscribeItem::getExpiresAt, now)
                .eq(UbmaSubscribeItem::getIsDeleted, 0);
        if (req.getAppId() != null) {
            expiredBucketQuery.eq(UbmaSubscribeItem::getAppId, req.getAppId());
        }
        Long expiredBucketCount = subscribeItemMapper.selectCount(expiredBucketQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refreshed_count", refreshedCount);
        result.put("expired_count", expiredCount);
        result.put("expired_bucket_count", expiredBucketCount);
        result.put("dry_run", dryRun);
        return ApiResponse.success(result);
    }

    private OffsetDateTime calculateNextRefreshTime(OffsetDateTime begin, Integer cycle, String unit) {
        if (cycle == null || cycle == 0) return null;
        return switch (unit != null ? unit : "month") {
            case "hour" -> begin.plusHours(cycle);
            case "day" -> begin.plusDays(cycle);
            case "week" -> begin.plusWeeks(cycle);
            case "month" -> begin.plusMonths(cycle);
            case "year" -> begin.plusYears(cycle);
            default -> begin.plusMonths(cycle);
        };
    }
}
