package fun.commons.benefit4j.it;

import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class BenefitTenantServiceIT extends BaseServiceTest {

    // === Benefit Items CRUD ===

    @Test
    void testCreateBenefitItem_success() {
        Long appId = createApp().getId();

        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        req.setName("VIP权益项");

        Map<String, Object> data = extractData(tenantService.postBenefitItems(appId, req));
        assertThat(data.get("item_id")).isNotNull();
        assertThat(data.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void testCreateBenefitItem_duplicateName_rejected() {
        Long appId = createApp().getId();

        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        req.setName("重复名称项");

        extractData(tenantService.postBenefitItems(appId, req));

        PostBenefitItemsRequest req2 = new PostBenefitItemsRequest();
        req2.setName("重复名称项");

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.postBenefitItems(appId, req2);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testUpdateBenefitItem_success() {
        Long appId = createApp().getId();
        UbmaBenefitItem item = createBenefitItem(appId);

        PutBenefitItemsItemIdRequest req = new PutBenefitItemsItemIdRequest();
        req.setName("更新后的名称");
        req.setDescription("新描述");

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.putBenefitItemsItemId(appId, String.valueOf(item.getId()), req);
        assertThat(resp.isSuccess()).isTrue();

        UbmaBenefitItem updated = benefitItemMapper.selectById(item.getId());
        assertThat(updated.getName()).isEqualTo("更新后的名称");
        assertThat(updated.getDescription()).isEqualTo("新描述");
    }

    @Test
    void testDeleteBenefitItem_noSubscriptionRef_success() {
        Long appId = createApp().getId();
        UbmaBenefitItem item = createBenefitItem(appId);

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.deleteBenefitItemsItemId(appId, String.valueOf(item.getId()));
        assertThat(resp.isSuccess()).isTrue();

        // Verify soft delete (@TableLogic)
        UbmaBenefitItem deleted = benefitItemMapper.selectById(item.getId());
        assertThat(deleted).isNull(); // selectById filters is_deleted=1
    }

    @Test
    void testDeleteBenefitItem_hasSubscriptionRef_rejected() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, itemId).getId();
        createSubscription(appId, "user-001", setId);

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.deleteBenefitItemsItemId(appId, String.valueOf(itemId));
        assertThat(resp.isFail()).isTrue();
    }

    // === Benefit Sets CRUD ===

    @Test
    void testCreateBenefitSet_withItemRefs_success() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();

        PostBenefitSetsRequest req = new PostBenefitSetsRequest();
        req.setName("标准VIP套餐");
        req.setDuration(1);
        req.setDurationUnit("month");
        req.setQuota(30);
        req.setPriority(10);
        PostBenefitSetsRequest.BenefitSetItemRef ref = new PostBenefitSetsRequest.BenefitSetItemRef();
        ref.setItemId(String.valueOf(itemId));
        ref.setQuota(10);
        ref.setRefreshCycle(1);
        ref.setRefreshCycleUnit("day");
        req.setItems(List.of(ref));

        Map<String, Object> data = extractData(tenantService.postBenefitSets(appId, req));
        assertThat(data.get("set_id")).isNotNull();
        assertThat(data.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void testUpdateBenefitSet_replaceItemRefs_success() {
        Long appId = createApp().getId();
        Long itemId1 = createBenefitItem(appId, "Item1").getId();
        Long itemId2 = createBenefitItem(appId, "Item2").getId();
        Long setId = createBenefitSet(appId, itemId1).getId();

        // Replace refs: remove item1, add item2
        PutBenefitSetsSetIdRequest req = new PutBenefitSetsSetIdRequest();
        req.setName("更新套餐");
        PostBenefitSetsRequest.BenefitSetItemRef ref = new PostBenefitSetsRequest.BenefitSetItemRef();
        ref.setItemId(String.valueOf(itemId2));
        ref.setQuota(20);
        ref.setRefreshCycle(1);
        ref.setRefreshCycleUnit("day");
        req.setItems(List.of(ref));

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.putBenefitSetsSetId(appId, String.valueOf(setId), req);
        assertThat(resp.isSuccess()).isTrue();

        // Verify old refs deleted, new ref inserted
        List<UbmaBenefitRef> refs = benefitRefMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaBenefitRef>()
                        .eq(UbmaBenefitRef::getSetId, setId));
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).getItemId()).isEqualTo(itemId2);
        assertThat(refs.get(0).getQuota()).isEqualTo(20);
    }

    @Test
    void testDeleteBenefitSet_noActiveSubscriptions_success() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, itemId).getId();

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.deleteBenefitSetsSetId(appId, String.valueOf(setId));
        assertThat(resp.isSuccess()).isTrue();

        // Verify soft delete
        UbmaBenefitSet deleted = benefitSetMapper.selectById(setId);
        assertThat(deleted).isNull();
    }

    @Test
    void testDeleteBenefitSet_hasActiveSubscriptions_rejected() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, itemId).getId();
        createSubscription(appId, "user-001", setId);

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.deleteBenefitSetsSetId(appId, String.valueOf(setId));
        assertThat(resp.isFail()).isTrue();
    }

    // === Compensation ===

    @Test
    void testCompensationAdd_createsNewBucket() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 30, 10, 30, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        UbmaSubscribe sub = readSubscribe(subId);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        Long originalSubsItemId = items.get(0).getId();

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId(subId);
        req.setSubsItemId(String.valueOf(originalSubsItemId));
        req.setItemId(String.valueOf(itemId));
        req.setAdjustNum(10);
        req.setAdjustType("ADD");
        req.setSourceType("COMPENSATION");
        req.setPriority(0);

        Map<String, Object> data = extractData(tenantService.postCompensations(appId, req));
        assertThat(data.get("adjust_type")).isEqualTo("ADD");

        // 父订阅总额 (含补偿) 累加
        UbmaSubscribe updatedSub = readSubscribe(subId);
        assertThat(updatedSub.getQuotaLimit()).isEqualTo(40); // 30 + 10

        // 原桶不变 (30)
        UbmaSubscribeItem originalItem = readSubscribeItem(originalSubsItemId);
        assertThat(originalItem.getQuotaLimit()).isEqualTo(30);
        assertThat(originalItem.getSourceType()).isEqualTo("SUBSCRIPTION");

        // 新桶被创建, quotaLimit=10, source=COMPENSATION
        List<UbmaSubscribeItem> allItems = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        assertThat(allItems).hasSize(2);
        UbmaSubscribeItem newBucket = allItems.stream()
                .filter(b -> !b.getId().equals(originalSubsItemId))
                .findFirst().orElseThrow();
        assertThat(newBucket.getQuotaLimit()).isEqualTo(10);
        assertThat(newBucket.getSourceType()).isEqualTo("COMPENSATION");
        assertThat(newBucket.getBucketPriority()).isEqualTo(0);
        assertThat(newBucket.getExpiresAt()).isNull();
    }

    @Test
    void testPostSubscriptions_bucketHasSetDefaults() {
        // V1.2.0 多源桶: Tenant postSubscriptions 创建订阅时应把
        // source_type=SUBSCRIPTION / bucket_priority=set.priority / expires_at=subscribe.dateEnd 拷到桶上
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        int setPriority = 42;
        Long setId = createBenefitSet(appId, 100, setPriority, 10, itemId).getId();
        String subId = createSubscription(appId, "bucket-defaults-user-" + uniqueAppid(), setId);

        UbmaSubscribe sub = readSubscribe(subId);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        assertThat(items).hasSize(1);
        UbmaSubscribeItem bucket = items.get(0);

        assertThat(bucket.getSourceType()).isEqualTo("SUBSCRIPTION");
        assertThat(bucket.getBucketPriority()).isEqualTo(setPriority);
        assertThat(bucket.getExpiresAt()).isNotNull();
        // expires_at 应等于 subscribe.dateEnd (整体过期)
        assertThat(bucket.getExpiresAt()).isEqualTo(sub.getDateEnd());
    }

    @Test
    void testCompensationReduce_decreasesQuotaLimit() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 30, 10, 30, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        UbmaSubscribe sub = readSubscribe(subId);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        Long subsItemId = items.get(0).getId();

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId(subId);
        req.setSubsItemId(String.valueOf(subsItemId));
        req.setItemId(String.valueOf(itemId));
        req.setAdjustNum(10);
        req.setAdjustType("REDUCE");

        extractData(tenantService.postCompensations(appId, req));

        UbmaSubscribe updatedSub = readSubscribe(subId);
        assertThat(updatedSub.getQuotaLimit()).isEqualTo(20); // 30 - 10

        UbmaSubscribeItem updatedItem = readSubscribeItem(subsItemId);
        assertThat(updatedItem.getQuotaLimit()).isEqualTo(20); // 30 - 10
    }

    @Test
    void testCompensationReduce_belowConsumed_rejected() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 30, 10, 30, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        // Consume 20
        PostConsumesDirectRequest consumeReq = new PostConsumesDirectRequest();
        consumeReq.setUserid("user-001");
        consumeReq.setItemId(String.valueOf(itemId));
        consumeReq.setExternalOrderId("ext-comp-" + uniqueAppid());
        consumeReq.setConsumeNum(20);
        extractData(runtimeService.postConsumesDirect(appId, consumeReq));

        UbmaSubscribe sub = readSubscribe(subId);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        Long subsItemId = items.get(0).getId();

        // Try to reduce 15 → 30-15=15 < 20 consumed → rejected
        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId(subId);
        req.setSubsItemId(String.valueOf(subsItemId));
        req.setItemId(String.valueOf(itemId));
        req.setAdjustNum(15);
        req.setAdjustType("REDUCE");

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.postCompensations(appId, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCompensationReduce_belowZero_rejected() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 30, 10, 30, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        UbmaSubscribe sub = readSubscribe(subId);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        Long subsItemId = items.get(0).getId();

        // Try to reduce 50 → 30-50=-20 < 0 → rejected
        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId(subId);
        req.setSubsItemId(String.valueOf(subsItemId));
        req.setItemId(String.valueOf(itemId));
        req.setAdjustNum(50);
        req.setAdjustType("REDUCE");

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.postCompensations(appId, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCompensation_onExhaustedSubscription_success() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 5, 10, 5, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        // Consume all to exhaust
        PostConsumesDirectRequest consumeReq = new PostConsumesDirectRequest();
        consumeReq.setUserid("user-001");
        consumeReq.setItemId(String.valueOf(itemId));
        consumeReq.setExternalOrderId("ext-compexh-" + uniqueAppid());
        consumeReq.setConsumeNum(5);
        extractData(runtimeService.postConsumesDirect(appId, consumeReq));

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("EXHAUSTED");

        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        Long subsItemId = items.get(0).getId();

        // ADD compensation on EXHAUSTED subscription
        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId(subId);
        req.setSubsItemId(String.valueOf(subsItemId));
        req.setItemId(String.valueOf(itemId));
        req.setAdjustNum(10);
        req.setAdjustType("ADD");

        Map<String, Object> data = extractData(tenantService.postCompensations(appId, req));
        assertThat(data.get("adjust_type")).isEqualTo("ADD");

        UbmaSubscribe updatedSub = readSubscribe(subId);
        assertThat(updatedSub.getQuotaLimit()).isEqualTo(15); // 5 + 10
    }

    @Test
    void testCompensation_onCanceledSubscription_rejected() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 30, 10, 30, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        // Cancel subscription
        PostSubscriptionsCancelRequest cancelReq = new PostSubscriptionsCancelRequest();
        cancelReq.setSubscribeId(subId);
        cancelReq.setExternalOrderId("ext-compcan-" + uniqueAppid());
        extractData(runtimeService.postSubscriptionsCancel(appId, cancelReq));

        UbmaSubscribe sub = readSubscribe(subId);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmaSubscribeItem>()
                        .eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        Long subsItemId = items.get(0).getId();

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId(subId);
        req.setSubsItemId(String.valueOf(subsItemId));
        req.setItemId(String.valueOf(itemId));
        req.setAdjustNum(10);
        req.setAdjustType("ADD");

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.postCompensations(appId, req);
        assertThat(resp.isFail()).isTrue();
    }

    // === Disable ===

    @Test
    void testDisableSubscription_activeToDisabled() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("违规操作");

        Map<String, Object> data = extractData(tenantService.postSubscriptionsSubscribeIdDisable(appId, subId, req));
        assertThat(data.get("status")).isEqualTo("DISABLED");

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void testDisableSubscription_exhaustedToDisabled() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, 5, 10, 5, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        // Consume all to exhaust
        PostConsumesDirectRequest consumeReq = new PostConsumesDirectRequest();
        consumeReq.setUserid("user-001");
        consumeReq.setItemId(String.valueOf(itemId));
        consumeReq.setExternalOrderId("ext-disexh-" + uniqueAppid());
        consumeReq.setConsumeNum(5);
        extractData(runtimeService.postConsumesDirect(appId, consumeReq));

        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("手动禁用");

        Map<String, Object> data = extractData(tenantService.postSubscriptionsSubscribeIdDisable(appId, subId, req));
        assertThat(data.get("status")).isEqualTo("DISABLED");
    }

    @Test
    void testDisableSubscription_withFrozenQuota_rejected() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        // Reserve some quota
        PostConsumesReserveRequest reserveReq = new PostConsumesReserveRequest();
        reserveReq.setUserid("user-001");
        reserveReq.setItemId(String.valueOf(itemId));
        reserveReq.setExternalOrderId("ext-disfrz-" + uniqueAppid());
        reserveReq.setConsumeNum(1);
        extractData(runtimeService.postConsumesReserve(appId, reserveReq));

        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("尝试禁用");

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.postSubscriptionsSubscribeIdDisable(appId, subId, req);
        assertThat(resp.isFail()).isTrue();

        UbmaSubscribe sub = readSubscribe(subId);
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void testDisableSubscription_alreadyCanceled_rejected() {
        Long appId = createApp().getId();
        Long itemId = createBenefitItem(appId).getId();
        Long setId = createBenefitSet(appId, itemId).getId();
        String subId = createSubscription(appId, "user-001", setId);

        // Cancel first
        PostSubscriptionsCancelRequest cancelReq = new PostSubscriptionsCancelRequest();
        cancelReq.setSubscribeId(subId);
        cancelReq.setExternalOrderId("ext-discan-" + uniqueAppid());
        extractData(runtimeService.postSubscriptionsCancel(appId, cancelReq));

        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("尝试禁用已取消");

        ApiResponse<?> resp = (ApiResponse<?>) tenantService.postSubscriptionsSubscribeIdDisable(appId, subId, req);
        assertThat(resp.isFail()).isTrue();
    }
}
