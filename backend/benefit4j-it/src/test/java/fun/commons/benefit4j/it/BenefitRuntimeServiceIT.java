package fun.commons.benefit4j.it;

import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BenefitRuntimeServiceIT extends BaseServiceTest {

    // === TCC Consume Flow ===

    @Test
    void testDirectConsume_updatesPeriodAndTotalConsumedAndVersion() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-direct-" + uniqueTenantid());
        req.setConsumeNum(5);

        Map<String, Object> data = extractData(runtimeService.postConsumesDirect(tenantId, req));
        assertThat(data.get("status")).isEqualTo("COMMITTED");

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getPeriodConsumed()).isEqualTo(5);
        assertThat(sub.getTotalConsumed()).isEqualTo(5);
        assertThat(sub.getVersion()).isEqualTo(1);

        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getPeriodConsumed()).isEqualTo(5);
        assertThat(items.get(0).getTotalConsumed()).isEqualTo(5);
        assertThat(items.get(0).getFrozenConsumed()).isEqualTo(0);
        assertThat(items.get(0).getVersion()).isEqualTo(1);
    }

    @Test
    void testReserveCommit_frozenTransitionsToConsumed() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        // Reserve
        PostConsumesReserveRequest reserveReq = new PostConsumesReserveRequest();
        reserveReq.setUserid("user-001");
        reserveReq.setItemId(String.valueOf(itemId));
        reserveReq.setExternalOrderId("ext-reserve-" + uniqueTenantid());
        reserveReq.setConsumeNum(3);

        Map<String, Object> reserveData = extractData(runtimeService.postConsumesReserve(tenantId, reserveReq));
        assertThat(reserveData.get("status")).isEqualTo("RESERVED");

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getFrozenConsumed()).isEqualTo(3);
        assertThat(sub.getPeriodConsumed()).isEqualTo(0);

        // Commit
        PostConsumesCommitRequest commitReq = new PostConsumesCommitRequest();
        commitReq.setExternalOrderId(reserveReq.getExternalOrderId());

        Map<String, Object> commitData = extractData(runtimeService.postConsumesCommit(tenantId, commitReq));
        assertThat(commitData.get("status")).isEqualTo("COMMITTED");

        sub = readSubscribe(subId);
        assertThat(sub.getFrozenConsumed()).isEqualTo(0);
        assertThat(sub.getPeriodConsumed()).isEqualTo(3);
        assertThat(sub.getTotalConsumed()).isEqualTo(3);
    }

    @Test
    void testReserveRelease_frozenReleasedBack() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        // Reserve
        PostConsumesReserveRequest reserveReq = new PostConsumesReserveRequest();
        reserveReq.setUserid("user-001");
        reserveReq.setItemId(String.valueOf(itemId));
        reserveReq.setExternalOrderId("ext-rls-" + uniqueTenantid());
        reserveReq.setConsumeNum(3);

        extractData(runtimeService.postConsumesReserve(tenantId, reserveReq));

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getFrozenConsumed()).isEqualTo(3);

        // Release
        PostConsumesReleaseRequest releaseReq = new PostConsumesReleaseRequest();
        releaseReq.setExternalOrderId(reserveReq.getExternalOrderId());

        Map<String, Object> releaseData = extractData(runtimeService.postConsumesRelease(tenantId, releaseReq));
        assertThat(releaseData.get("status")).isEqualTo("RELEASED");

        sub = readSubscribe(subId);
        assertThat(sub.getFrozenConsumed()).isEqualTo(0);
        assertThat(sub.getPeriodConsumed()).isEqualTo(0);
        assertThat(sub.getTotalConsumed()).isEqualTo(0);
    }

    @Test
    void testCrossSubscriptionConsume_deductsFromBoth() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId1 = createBenefitSet(tenantId, 5, 10, 5, itemId).getId(); // higher priority, set quota=5, ref quota=5
        Long setId2 = createBenefitSet(tenantId, 5, 5, 5, itemId).getId();  // lower priority, set quota=5, ref quota=5
        String subId1 = createSubscription(tenantId, "user-001", setId1);
        String subId2 = createSubscription(tenantId, "user-001", setId2);

        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-cross-" + uniqueTenantid());
        req.setConsumeNum(7);

        Map<String, Object> data = extractData(runtimeService.postConsumesDirect(tenantId, req));
        assertThat(data.get("consume_num")).isEqualTo(7);

        UbmaSubscribe sub1 = readSubscribe(subId1);
        UbmaSubscribe sub2 = readSubscribe(subId2);
        assertThat(sub1.getPeriodConsumed()).isEqualTo(5);
        assertThat(sub2.getPeriodConsumed()).isEqualTo(2);
    }

    @Test
    void testDirectConsume_idempotency() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        createSubscription(tenantId, "user-001", setId);

        String extId = "ext-idem-" + uniqueTenantid();
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId(extId);
        req.setConsumeNum(3);

        Map<String, Object> first = extractData(runtimeService.postConsumesDirect(tenantId, req));
        Map<String, Object> second = extractData(runtimeService.postConsumesDirect(tenantId, req));

        assertThat(first.get("consume_id")).isEqualTo(second.get("consume_id"));
    }

    @Test
    void testReserve_idempotency() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        createSubscription(tenantId, "user-001", setId);

        String extId = "ext-ridem-" + uniqueTenantid();
        PostConsumesReserveRequest req = new PostConsumesReserveRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId(extId);
        req.setConsumeNum(2);

        Map<String, Object> first = extractData(runtimeService.postConsumesReserve(tenantId, req));
        Map<String, Object> second = extractData(runtimeService.postConsumesReserve(tenantId, req));

        assertThat(first.get("consume_id")).isEqualTo(second.get("consume_id"));
    }

    @Test
    void testDirectConsume_insufficientQuota() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, 5, 10, 5, itemId).getId();
        createSubscription(tenantId, "user-001", setId);

        // First consume all 5
        PostConsumesDirectRequest consumeAll = new PostConsumesDirectRequest();
        consumeAll.setUserid("user-001");
        consumeAll.setItemId(String.valueOf(itemId));
        consumeAll.setExternalOrderId("ext-insuf-all-" + uniqueTenantid());
        consumeAll.setConsumeNum(5);
        extractData(runtimeService.postConsumesDirect(tenantId, consumeAll));

        // Now try to consume more — no quota left
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-insuf-" + uniqueTenantid());
        req.setConsumeNum(1);

        ApiResponse<?> resp = (ApiResponse<?>) runtimeService.postConsumesDirect(tenantId, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testDirectConsume_exhaustsSubscription() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, 5, 10, 5, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-exh-" + uniqueTenantid());
        req.setConsumeNum(5);

        extractData(runtimeService.postConsumesDirect(tenantId, req));

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("EXHAUSTED");
    }

    // === Subscription Lifecycle ===

    @Test
    void testCreateSubscription_fromBenefitSet() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();

        Map<String, Object> data = extractData(runtimeService.postSubscriptions(tenantId,
                new PostSubscriptionsRequest() {{
                    setUserid("user-001");
                    setSetId(String.valueOf(setId));
                    setExternalOrderId("ext-crt-" + uniqueTenantid());
                }}));

        String subId = (String) data.get("subscribe_id");
        assertThat(subId).isNotNull();

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getQuotaLimit()).isEqualTo(30);
        assertThat(sub.getPeriodConsumed()).isEqualTo(0);

        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuotaLimit()).isEqualTo(10);
    }

    @Test
    void testCreateSubscription_idempotency() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();

        String extId = "ext-sidem-" + uniqueTenantid();
        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("user-001");
        req.setSetId(String.valueOf(setId));
        req.setExternalOrderId(extId);

        Map<String, Object> first = extractData(runtimeService.postSubscriptions(tenantId, req));
        Map<String, Object> second = extractData(runtimeService.postSubscriptions(tenantId, req));
        assertThat(first.get("subscribe_id")).isEqualTo(second.get("subscribe_id"));
    }

    @Test
    void testCancelSubscription_activeToCanceled() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
        req.setSubscribeId(subId);
        req.setExternalOrderId("ext-cancel-" + uniqueTenantid());

        Map<String, Object> data = extractData(runtimeService.postSubscriptionsCancel(tenantId, req));
        assertThat(data.get("status")).isEqualTo("CANCELED");

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("CANCELED");
    }

    @Test
    void testCancelSubscription_withFrozenQuota_rejected() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        // Reserve some quota first
        PostConsumesReserveRequest reserveReq = new PostConsumesReserveRequest();
        reserveReq.setUserid("user-001");
        reserveReq.setItemId(String.valueOf(itemId));
        reserveReq.setExternalOrderId("ext-frz-" + uniqueTenantid());
        reserveReq.setConsumeNum(1);
        extractData(runtimeService.postConsumesReserve(tenantId, reserveReq));

        PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
        req.setSubscribeId(subId);
        req.setExternalOrderId("ext-cancel-frz-" + uniqueTenantid());

        ApiResponse<?> resp = (ApiResponse<?>) runtimeService.postSubscriptionsCancel(tenantId, req);
        assertThat(resp.isFail()).isTrue();

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void testCancelSubscription_alreadyCanceled_rejected() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        PostSubscriptionsCancelRequest req1 = new PostSubscriptionsCancelRequest();
        req1.setSubscribeId(subId);
        req1.setExternalOrderId("ext-cancel-1-" + uniqueTenantid());
        extractData(runtimeService.postSubscriptionsCancel(tenantId, req1));

        PostSubscriptionsCancelRequest req2 = new PostSubscriptionsCancelRequest();
        req2.setSubscribeId(subId);
        req2.setExternalOrderId("ext-cancel-2-" + uniqueTenantid());

        ApiResponse<?> resp = (ApiResponse<?>) runtimeService.postSubscriptionsCancel(tenantId, req2);
        assertThat(resp.isFail()).isTrue();
    }

    // === Refund Flow ===

    @Test
    void testRefund_restoresQuotaAndDecrementsConsumed() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        // Consume 5
        PostConsumesDirectRequest consumeReq = new PostConsumesDirectRequest();
        consumeReq.setUserid("user-001");
        consumeReq.setItemId(String.valueOf(itemId));
        consumeReq.setExternalOrderId("ext-crf-" + uniqueTenantid());
        consumeReq.setConsumeNum(5);
        Map<String, Object> consumeData = extractData(runtimeService.postConsumesDirect(tenantId, consumeReq));

        // Refund 3
        PostRefundsRequest refundReq = new PostRefundsRequest();
        refundReq.setConsumeId((String) consumeData.get("consume_id"));
        refundReq.setExternalRefundId("ext-refund-" + uniqueTenantid());
        refundReq.setRefundNum(3);

        Map<String, Object> refundData = extractData(runtimeService.postRefunds(tenantId, refundReq));
        assertThat(refundData.get("refund_num")).isEqualTo(3);

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getPeriodConsumed()).isEqualTo(2);
        assertThat(sub.getTotalConsumed()).isEqualTo(2);
    }

    @Test
    void testRefund_reactivatesExhaustedToActive() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, 5, 10, 5, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        // Consume all to exhaust
        PostConsumesDirectRequest consumeReq = new PostConsumesDirectRequest();
        consumeReq.setUserid("user-001");
        consumeReq.setItemId(String.valueOf(itemId));
        consumeReq.setExternalOrderId("ext-exhrf-" + uniqueTenantid());
        consumeReq.setConsumeNum(5);
        Map<String, Object> consumeData = extractData(runtimeService.postConsumesDirect(tenantId, consumeReq));

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("EXHAUSTED");

        // Refund 3 → should reactivate
        PostRefundsRequest refundReq = new PostRefundsRequest();
        refundReq.setConsumeId((String) consumeData.get("consume_id"));
        refundReq.setExternalRefundId("ext-refund-react-" + uniqueTenantid());
        refundReq.setRefundNum(3);

        extractData(runtimeService.postRefunds(tenantId, refundReq));

        sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getPeriodConsumed()).isEqualTo(2);
    }

    @Test
    void testRefund_idempotency() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        createSubscription(tenantId, "user-001", setId);

        PostConsumesDirectRequest consumeReq = new PostConsumesDirectRequest();
        consumeReq.setUserid("user-001");
        consumeReq.setItemId(String.valueOf(itemId));
        consumeReq.setExternalOrderId("ext-crfidem-" + uniqueTenantid());
        consumeReq.setConsumeNum(3);
        Map<String, Object> consumeData = extractData(runtimeService.postConsumesDirect(tenantId, consumeReq));

        String extRefundId = "ext-ridem-" + uniqueTenantid();
        PostRefundsRequest refundReq = new PostRefundsRequest();
        refundReq.setConsumeId((String) consumeData.get("consume_id"));
        refundReq.setExternalRefundId(extRefundId);
        refundReq.setRefundNum(2);

        Map<String, Object> first = extractData(runtimeService.postRefunds(tenantId, refundReq));
        Map<String, Object> second = extractData(runtimeService.postRefunds(tenantId, refundReq));
        assertThat(first.get("refund_id")).isEqualTo(second.get("refund_id"));
    }

    @Test
    void testRefund_cumulativeExceedsConsumeNum_rejected() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        createSubscription(tenantId, "user-001", setId);

        PostConsumesDirectRequest consumeReq = new PostConsumesDirectRequest();
        consumeReq.setUserid("user-001");
        consumeReq.setItemId(String.valueOf(itemId));
        consumeReq.setExternalOrderId("ext-crfcum-" + uniqueTenantid());
        consumeReq.setConsumeNum(5);
        Map<String, Object> consumeData = extractData(runtimeService.postConsumesDirect(tenantId, consumeReq));

        // First refund 4
        PostRefundsRequest refund1 = new PostRefundsRequest();
        refund1.setConsumeId((String) consumeData.get("consume_id"));
        refund1.setExternalRefundId("ext-rcum1-" + uniqueTenantid());
        refund1.setRefundNum(4);
        extractData(runtimeService.postRefunds(tenantId, refund1));

        // Second refund 2 → 4+2=6 > 5 → rejected
        PostRefundsRequest refund2 = new PostRefundsRequest();
        refund2.setConsumeId((String) consumeData.get("consume_id"));
        refund2.setExternalRefundId("ext-rcum2-" + uniqueTenantid());
        refund2.setRefundNum(2);

        ApiResponse<?> resp = (ApiResponse<?>) runtimeService.postRefunds(tenantId, refund2);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testRefund_notCommittedConsume_rejected() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, itemId).getId();
        createSubscription(tenantId, "user-001", setId);

        // Reserve (not commit)
        PostConsumesReserveRequest reserveReq = new PostConsumesReserveRequest();
        reserveReq.setUserid("user-001");
        reserveReq.setItemId(String.valueOf(itemId));
        reserveReq.setExternalOrderId("ext-rncom-" + uniqueTenantid());
        reserveReq.setConsumeNum(2);
        Map<String, Object> reserveData = extractData(runtimeService.postConsumesReserve(tenantId, reserveReq));

        // Try to refund the RESERVED consume
        PostRefundsRequest refundReq = new PostRefundsRequest();
        refundReq.setConsumeId((String) reserveData.get("consume_id"));
        refundReq.setExternalRefundId("ext-rncomref-" + uniqueTenantid());

        ApiResponse<?> resp = (ApiResponse<?>) runtimeService.postRefunds(tenantId, refundReq);
        assertThat(resp.isFail()).isTrue();
    }

}
