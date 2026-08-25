package fun.commons.benefit4j.assets.scheduler;

import fun.commons.benefit4j.assets.service.PreConsumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预扣过期回收(assets-design §2.4,沿用 ReserveTimeoutScheduler 模式)。
 * 多实例安全: 扫描不加锁,依赖 O15 终态 guard(WHERE status='RESERVED'),
 * 并发 expire/settle 只有一方生效,重复尝试无副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetsExpireScheduler {

    private final PreConsumeService preConsumeService;

    @Scheduled(fixedDelayString = "${assets.pre-consume.expire-check-interval:5000}")
    public void releaseExpired() {
        int count = preConsumeService.expireOnce(100);
        if (count > 0) {
            log.info("[assets] 过期预扣回收: {} 条", count);
        }
    }
}
