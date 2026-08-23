package fun.commons.benefit4j.boot;

import fun.commons.benefit4j.service.BenefitRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class ReserveTimeoutScheduler {

    private final BenefitRuntimeService runtimeService;

    @Scheduled(fixedDelayString = "${benefit4j.runtime.reserve-check-interval:5000}")
    public void releaseExpiredReserves() {
        int count = runtimeService.releaseExpiredReserves();
        if (count > 0) {
            log.info("自动释放超时预扣记录: {}条", count);
        }
    }
}
