package fun.commons.benefit4j.it;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.benefit4j.dto.PostCompensationsRequest;
import fun.commons.benefit4j.dto.PostConsumesDirectRequest;
import fun.commons.benefit4j.dto.PostSubscriptionsRequest;
import fun.commons.benefit4j.entity.UbmaSubscribe;
import fun.commons.benefit4j.entity.UbmaSubscribeItem;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多源额度批次集成测试 (V1.2.0).
 *
 * 验证:
 * - testMultiSourceBucketConsume_drainsByPriority: 多源桶按 priority 排空
 * - testExpiredBucketSkipped: expires_at 过期的桶被跳过
 * - testCompensationAdd_createsNewBucket_withPriority: 补偿创建带优先级的永不过期桶
 * - testInsufficientBalance_rejectsWhenStrict: 严格模式总额不足整笔拒绝
 * - testInsufficientBalance_partialAllowedDrainsAvailable: partialAllowed=true 扣到能扣为止
 */
public class QuotaBucketMultiSourceIT extends BaseServiceTest {

    @Test
    void testMultiSourceBucketConsume_drainsByPriority() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        // 高优先级 set (priority=20) — 月度桶
        Long monthlySetId = createBenefitSet(tenantId, 10000, 20, 10000, itemId).getId();
        // 低优先级 set (priority=10) — 充值桶
        Long topUpSetId = createBenefitSet(tenantId, 50000, 10, 50000, itemId).getId();

        String subId1 = createSubscription(tenantId, "user-001", monthlySetId);
        String subId2 = createSubscription(tenantId, "user-001", topUpSetId);

        // 请求扣 12000: 月度桶先扣 (priority=20 > 10), 月度耗尽 10000 + 充值桶扣 2000
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-multisrc-" + uniqueTenantid());
        req.setConsumeNum(12000);
        Map<String, Object> data = extractData(runtimeService.postConsumesDirect(tenantId, req));
        assertThat(data.get("consume_num")).isEqualTo(12000);

        // 月度桶耗尽
        UbmaSubscribe sub1 = readSubscribe(subId1);
        assertThat(sub1.getPeriodConsumed()).isEqualTo(10000);
        // 充值桶扣 2000
        UbmaSubscribe sub2 = readSubscribe(subId2);
        assertThat(sub2.getPeriodConsumed()).isEqualTo(2000);

        // 验证桶字段已正确拷贝
        List<UbmaSubscribeItem> monthlyBuckets = subscribeItemMapper.selectList(
                new LambdaQueryWrapper<UbmaSubscribeItem>().eq(UbmaSubscribeItem::getSubscribeId, sub1.getId()));
        assertThat(monthlyBuckets).hasSize(1);
        assertThat(monthlyBuckets.get(0).getBucketPriority()).isEqualTo(20);
        assertThat(monthlyBuckets.get(0).getSourceType()).isEqualTo("SUBSCRIPTION");

        List<UbmaSubscribeItem> topUpBuckets = subscribeItemMapper.selectList(
                new LambdaQueryWrapper<UbmaSubscribeItem>().eq(UbmaSubscribeItem::getSubscribeId, sub2.getId()));
        assertThat(topUpBuckets).hasSize(1);
        assertThat(topUpBuckets.get(0).getBucketPriority()).isEqualTo(10);
    }

    @Test
    void testExpiredBucketSkipped() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long monthlySetId = createBenefitSet(tenantId, 10000, 20, 10000, itemId).getId();
        Long topUpSetId = createBenefitSet(tenantId, 50000, 10, 50000, itemId).getId();

        String subId1 = createSubscription(tenantId, "user-001", monthlySetId);
        String subId2 = createSubscription(tenantId, "user-001", topUpSetId);

        // 把月度桶 expires_at 设为过去
        UbmaSubscribe sub1 = readSubscribe(subId1);
        List<UbmaSubscribeItem> monthlyBuckets = subscribeItemMapper.selectList(
                new LambdaQueryWrapper<UbmaSubscribeItem>().eq(UbmaSubscribeItem::getSubscribeId, sub1.getId()));
        Long monthlyBucketId = monthlyBuckets.get(0).getId();
        OffsetDateTime yesterday = OffsetDateTime.now().minusDays(1);
        LambdaUpdateWrapper<UbmaSubscribeItem> expireUpdate = new LambdaUpdateWrapper<>();
        expireUpdate.eq(UbmaSubscribeItem::getId, monthlyBucketId)
                .set(UbmaSubscribeItem::getExpiresAt, yesterday);
        subscribeItemMapper.update(null, expireUpdate);

        // 请求 5000 — 月度桶已被过期过滤, 只扣充值桶 (priority=10)
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-exp-" + uniqueTenantid());
        req.setConsumeNum(5000);
        Map<String, Object> data = extractData(runtimeService.postConsumesDirect(tenantId, req));
        assertThat(data.get("consume_num")).isEqualTo(5000);

        // 月度桶未扣
        UbmaSubscribeItem monthlyAfter = readSubscribeItem(monthlyBucketId);
        assertThat(monthlyAfter.getPeriodConsumed()).isEqualTo(0);

        // 充值桶扣 5000
        UbmaSubscribe sub2 = readSubscribe(subId2);
        assertThat(sub2.getPeriodConsumed()).isEqualTo(5000);
    }

    @Test
    void testCompensationAdd_createsNewBucket_withPriority() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long setId = createBenefitSet(tenantId, 10000, 10, 10000, itemId).getId();
        String subId = createSubscription(tenantId, "user-001", setId);

        UbmaSubscribe sub = readSubscribe(subId);
        List<UbmaSubscribeItem> originalBuckets = subscribeItemMapper.selectList(
                new LambdaQueryWrapper<UbmaSubscribeItem>().eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        Long originalSubsItemId = originalBuckets.get(0).getId();

        // 独立充值补偿: 优先级 5 (低), 永不过期
        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId(subId);
        req.setSubsItemId(String.valueOf(originalSubsItemId));
        req.setItemId(String.valueOf(itemId));
        req.setAdjustNum(5000);
        req.setAdjustType("ADD");
        req.setSourceType("TOPUP");
        req.setPriority(5);
        // 不设 expiresAt = 永不过期
        Map<String, Object> data = extractData(tenantService.postCompensations(tenantId, req));
        assertThat(data.get("source_type")).isEqualTo("TOPUP");
        assertThat(data.get("bucket_priority")).isEqualTo(5);

        // 验证桶结构
        List<UbmaSubscribeItem> allBuckets = subscribeItemMapper.selectList(
                new LambdaQueryWrapper<UbmaSubscribeItem>().eq(UbmaSubscribeItem::getSubscribeId, sub.getId()));
        assertThat(allBuckets).hasSize(2);

        UbmaSubscribeItem topupBucket = allBuckets.stream()
                .filter(b -> !b.getId().equals(originalSubsItemId))
                .findFirst().orElseThrow();
        assertThat(topupBucket.getSourceType()).isEqualTo("TOPUP");
        assertThat(topupBucket.getBucketPriority()).isEqualTo(5);
        assertThat(topupBucket.getQuotaLimit()).isEqualTo(5000);
        assertThat(topupBucket.getExpiresAt()).isNull();

        // 父订阅总额累加 (10000 + 5000 = 15000)
        assertThat(readSubscribe(subId).getQuotaLimit()).isEqualTo(15000);
    }

    @Test
    void testInsufficientBalance_rejectsWhenStrict() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        // 月度桶 10 (priority=20), 充值桶 0 (priority=10) — 总 10
        Long monthlySetId = createBenefitSet(tenantId, 10, 20, 10, itemId).getId();
        Long topUpSetId = createBenefitSet(tenantId, 0, 10, 0, itemId).getId();
        createSubscription(tenantId, "user-001", monthlySetId);
        createSubscription(tenantId, "user-001", topUpSetId);

        // 严格模式 (默认): 请求 20, 总额仅 10 → 整笔拒绝
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-strict-" + uniqueTenantid());
        req.setConsumeNum(20);
        ApiResponse<?> resp = (ApiResponse<?>) runtimeService.postConsumesDirect(tenantId, req);
        assertThat(resp.isFail()).isTrue();
        assertThat(resp.getMessage()).isEqualTo("INSUFFICIENT_BALANCE");

        // 桶字段未被修改
        List<UbmaSubscribeItem> allItems = subscribeItemMapper.selectList(
                new LambdaQueryWrapper<UbmaSubscribeItem>().eq(UbmaSubscribeItem::getTenantId, tenantId));
        for (UbmaSubscribeItem b : allItems) {
            assertThat(b.getPeriodConsumed()).isEqualTo(0);
        }
    }

    @Test
    void testInsufficientBalance_partialAllowedDrainsAvailable() {
        Long tenantId = createTenant().getId();
        Long itemId = createBenefitItem(tenantId).getId();
        Long monthlySetId = createBenefitSet(tenantId, 10, 20, 10, itemId).getId();
        Long topUpSetId = createBenefitSet(tenantId, 0, 10, 0, itemId).getId();
        String subIdMonthly = createSubscription(tenantId, "user-001", monthlySetId);
        createSubscription(tenantId, "user-001", topUpSetId);

        // 部分允许: 请求 20, 实际只扣到 10
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId(String.valueOf(itemId));
        req.setExternalOrderId("ext-partial-" + uniqueTenantid());
        req.setConsumeNum(20);
        req.setPartialAllowed(true);
        Map<String, Object> data = extractData(runtimeService.postConsumesDirect(tenantId, req));
        assertThat(data.get("consume_num")).isEqualTo(10);

        // 月度桶扣满
        UbmaSubscribe sub = readSubscribe(subIdMonthly);
        assertThat(sub.getPeriodConsumed()).isEqualTo(10);
    }
}