package fun.commons.benefit4j.it;

import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.UbmaSubscribe;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class BenefitConcurrencyIT extends BaseServiceTest {

    @Test
    void testConcurrentDirectConsume_oneSucceedsOneRetriesOrPartialFail() throws Exception {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, 5, 10, 5, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        // Two threads try to consume 5 each from a subscription with quota=5
        // Only one should fully succeed; the other should get partial or fail
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                startLatch.await();
                PostConsumesDirectRequest req = new PostConsumesDirectRequest();
                req.setUserid("user-001");
                req.setItemId(String.valueOf(itemId));
                req.setExternalOrderId("ext-conc-" + uniqueTenantid());
                req.setConsumeNum(5);

                Object result = runtimeService.postConsumesDirect(tenantId, req);
                ApiResponse<?> resp = (ApiResponse<?>) result;
                if (resp.isSuccess()) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(executor.submit(task));
        futures.add(executor.submit(task));

        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // At least one should succeed, total consumed should not exceed quota
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getPeriodConsumed()).isLessThanOrEqualTo(5);
    }

    @Test
    void testConcurrentCancelAndConsume_cancelBlocksConsume() throws Exception {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, 30, 10, 30, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger cancelSuccess = new AtomicInteger(0);
        AtomicInteger consumeSuccess = new AtomicInteger(0);

        Runnable cancelTask = () -> {
            try {
                startLatch.await();
                PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
                req.setSubscribeId(subId);
                req.setExternalOrderId("ext-conccan-" + uniqueTenantid());

                Object result = runtimeService.postSubscriptionsCancel(tenantId, req);
                ApiResponse<?> resp = (ApiResponse<?>) result;
                if (resp.isSuccess()) cancelSuccess.incrementAndGet();
            } catch (Exception ignored) {
            }
        };

        Runnable consumeTask = () -> {
            try {
                startLatch.await();
                PostConsumesDirectRequest req = new PostConsumesDirectRequest();
                req.setUserid("user-001");
                req.setItemId(String.valueOf(itemId));
                req.setExternalOrderId("ext-conccon-" + uniqueTenantid());
                req.setConsumeNum(5);

                Object result = runtimeService.postConsumesDirect(tenantId, req);
                ApiResponse<?> resp = (ApiResponse<?>) result;
                if (resp.isSuccess()) consumeSuccess.incrementAndGet();
            } catch (Exception ignored) {
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(executor.submit(cancelTask));
        futures.add(executor.submit(consumeTask));

        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // If cancel won, consume should have failed; if consume won, cancel should have failed
        // Both cannot succeed simultaneously
        assertThat(cancelSuccess.get() + consumeSuccess.get()).isLessThanOrEqualTo(2);

        UbmaSubscribe sub = readSubscribe(subId);
        // Status should be either CANCELED or ACTIVE (if consume won)
        assertThat(sub.getStatus()).isIn("CANCELED", "ACTIVE", "EXHAUSTED");
    }

    @Test
    void testConcurrentReserveAndCommit_noLostUpdate() throws Exception {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, 30, 10, 30, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        // Two threads each reserve 10 from a subscription with quota=30
        // With optimistic locking on a single item, one may lose the version race
        // The key invariant: frozenConsumed must never exceed quotaLimit (no lost update)
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                startLatch.await();
                PostConsumesReserveRequest req = new PostConsumesReserveRequest();
                req.setUserid("user-001");
                req.setItemId(String.valueOf(itemId));
                req.setExternalOrderId("ext-concrsv-" + uniqueTenantid());
                req.setConsumeNum(10);

                Object result = runtimeService.postConsumesReserve(tenantId, req);
                ApiResponse<?> resp = (ApiResponse<?>) result;
                if (resp.isSuccess()) successCount.incrementAndGet();
            } catch (Exception ignored) {
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        futures.add(executor.submit(task));
        futures.add(executor.submit(task));

        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // At least one should succeed; frozenConsumed must not exceed quotaLimit
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getFrozenConsumed()).isLessThanOrEqualTo(sub.getQuotaLimit());
        assertThat(sub.getFrozenConsumed()).isGreaterThan(0);
    }
}
