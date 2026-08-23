package fun.commons.benefit4j.it;

import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.benefit4j.service.BenefitRuntimeService;
import fun.commons.benefit4j.service.BenefitTenantService;
import fun.commons.framework4j.web.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.Map;

public abstract class BaseServiceTest extends BaseMapperTest {

    @Autowired protected BenefitRuntimeService runtimeService;
    @Autowired protected BenefitTenantService tenantService;
    @Autowired protected UbmaBenefitItemMapper benefitItemMapper;
    @Autowired protected UbmaBenefitSetMapper benefitSetMapper;
    @Autowired protected UbmaBenefitRefMapper benefitRefMapper;
    @Autowired protected UbmaSubscribeMapper subscribeMapper;
    @Autowired protected UbmaSubscribeItemMapper subscribeItemMapper;
    @Autowired protected UbmaConsumeMapper consumeMapper;
    @Autowired protected UbmaRefundMapper refundMapper;
    @Autowired protected UbmaCompensationMapper compensationMapper;
    @Autowired protected UbmaUnsubscribeMapper unsubscribeMapper;

    protected UbmaBenefitItem createBenefitItem(Long appId) {
        return createBenefitItem(appId, "Item-" + uniqueAppid());
    }

    protected UbmaBenefitItem createBenefitItem(Long appId, String name) {
        UbmaBenefitItem item = new UbmaBenefitItem();
        item.setAppId(appId);
        item.setName(name);
        item.setStatus("ACTIVE");
        item.setDefaultDeduction(1);
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());
        benefitItemMapper.insert(item);
        return item;
    }

    protected UbmaBenefitSet createBenefitSet(Long appId, Long... itemIds) {
        return createBenefitSet(appId, 30, 10, itemIds);
    }

    protected UbmaBenefitSet createBenefitSet(Long appId, int quota, int priority, Long... itemIds) {
        return createBenefitSet(appId, quota, priority, 10, itemIds);
    }

    protected UbmaBenefitSet createBenefitSet(Long appId, int quota, int priority, int refQuota, Long... itemIds) {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setAppId(appId);
        set.setName("Set-" + uniqueAppid());
        set.setDuration(1);
        set.setDurationUnit("month");
        set.setQuota(quota);
        set.setPriority(priority);
        set.setRefreshCycle(1);
        set.setRefreshCycleUnit("day");
        set.setStatus("ACTIVE");
        set.setCreatedAt(OffsetDateTime.now());
        set.setUpdatedAt(OffsetDateTime.now());
        benefitSetMapper.insert(set);

        for (Long itemId : itemIds) {
            UbmaBenefitRef ref = new UbmaBenefitRef();
            ref.setAppId(appId);
            ref.setSetId(set.getId());
            ref.setItemId(itemId);
            ref.setQuota(refQuota);
            ref.setRefreshCycle(1);
            ref.setRefreshCycleUnit("day");
            ref.setCreatedAt(OffsetDateTime.now());
            ref.setUpdatedAt(OffsetDateTime.now());
            benefitRefMapper.insert(ref);
        }
        return set;
    }

    protected String createSubscription(Long appId, String userid, Long setId) {
        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid(userid);
        req.setSetId(String.valueOf(setId));
        req.setExternalOrderId("ext-sub-" + uniqueAppid());

        Map<String, Object> data = extractData(runtimeService.postSubscriptions(appId, req));
        return (String) data.get("subscribe_id");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractData(Object result) {
        ApiResponse<?> resp = (ApiResponse<?>) result;
        assert resp.isSuccess() : "Expected success but got: code=" + resp.getCode() + " msg=" + resp.getMessage();
        return (Map<String, Object>) resp.getData();
    }

    protected ApiResponse<?> assertFail(Object result) {
        ApiResponse<?> resp = (ApiResponse<?>) result;
        assert resp.isFail() : "Expected failure but got success";
        return resp;
    }

    protected UbmaSubscribe readSubscribe(Object id) {
        Long longId = toLongId(id);
        return subscribeMapper.selectById(longId);
    }

    protected UbmaSubscribeItem readSubscribeItem(Object id) {
        Long longId = toLongId(id);
        return subscribeItemMapper.selectById(longId);
    }

    private Long toLongId(Object id) {
        if (id == null) return null;
        if (id instanceof Long l) return l;
        if (id instanceof Number n) return n.longValue();
        String s = String.valueOf(id);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fun.commons.framework4j.id.util.IdObfuscator.fromOpenId(s);
        }
    }
}
