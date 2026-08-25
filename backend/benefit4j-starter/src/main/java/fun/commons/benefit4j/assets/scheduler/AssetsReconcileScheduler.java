package fun.commons.benefit4j.assets.scheduler;

import fun.commons.benefit4j.assets.mapper.UbmaApplicationAdapter;
import fun.commons.benefit4j.assets.service.AssetsReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T+1 对账调度(assets-design §8.4): 每日 02:00 Asia/Shanghai 全资产对账;
 * 多实例安全: 对账为只读+差错池插入,幂等可重跑(快照 upsert,差错池重复行由人工去重)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetsReconcileScheduler {

    private final AssetsReconcileService reconcileService;
    private final UbmaApplicationAdapter appAdapter;

    @Scheduled(cron = "${assets.reconcile.cron:0 0 2 * * *}", zone = "Asia/Shanghai")
    public void dailyReconcile() {
        for (Long appId : appAdapter.allAppIds()) {
            try {
                int diffs = reconcileService.runAllAssets(appId);
                if (diffs > 0) {
                    log.error("[assets][对账] 发现差异待处置: appId={} diffs={}", appId, diffs);
                }
            } catch (Exception e) {
                log.error("[assets][对账] 单 app 对账失败,继续下一个", e);
            }
        }
    }
}
