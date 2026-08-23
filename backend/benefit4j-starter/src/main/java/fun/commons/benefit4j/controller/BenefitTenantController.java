package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.client.BenefitTenantClient;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.id.util.IdObfuscator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequiresToken(value = "APP", type = "access")
public class BenefitTenantController {

    private final BenefitTenantClient client;

    @PostMapping("/benefit/api/v1/tenant/benefit-items")
    public Object postBenefitItems(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostBenefitItemsRequest req) {
        return client.postBenefitItems(appId(), req);
    }

    @GetMapping("/benefit/api/v1/tenant/benefit-items")
    public Object getBenefitItems() {
        return client.getBenefitItems(appId());
    }

    @GetMapping("/benefit/api/v1/tenant/benefit-items/{item_id}")
    public Object getBenefitItemsItemId(
            @PathVariable("item_id") String itemId) {
        return client.getBenefitItemsItemId(appId(), itemId);
    }

    @PutMapping("/benefit/api/v1/tenant/benefit-items/{item_id}")
    public Object putBenefitItemsItemId(
            @PathVariable("item_id") String itemId,
            @Valid @RequestBody fun.commons.benefit4j.dto.PutBenefitItemsItemIdRequest req) {
        return client.putBenefitItemsItemId(appId(), itemId, req);
    }

    @DeleteMapping("/benefit/api/v1/tenant/benefit-items/{item_id}")
    public Object deleteBenefitItemsItemId(
            @PathVariable("item_id") String itemId) {
        return client.deleteBenefitItemsItemId(appId(), itemId);
    }

    @GetMapping("/benefit/api/v1/tenant/benefit-templates")
    public Object getBenefitTemplates() {
        return client.getBenefitTemplates(appId());
    }

    @PostMapping("/benefit/api/v1/tenant/benefit-sets")
    public Object postBenefitSets(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostBenefitSetsRequest req) {
        return client.postBenefitSets(appId(), req);
    }

    @GetMapping("/benefit/api/v1/tenant/benefit-sets")
    public Object getBenefitSets() {
        return client.getBenefitSets(appId());
    }

    @GetMapping("/benefit/api/v1/tenant/benefit-sets/{set_id}")
    public Object getBenefitSetsSetId(
            @PathVariable("set_id") String setId) {
        return client.getBenefitSetsSetId(appId(), setId);
    }

    @PutMapping("/benefit/api/v1/tenant/benefit-sets/{set_id}")
    public Object putBenefitSetsSetId(
            @PathVariable("set_id") String setId,
            @Valid @RequestBody fun.commons.benefit4j.dto.PutBenefitSetsSetIdRequest req) {
        return client.putBenefitSetsSetId(appId(), setId, req);
    }

    @DeleteMapping("/benefit/api/v1/tenant/benefit-sets/{set_id}")
    public Object deleteBenefitSetsSetId(
            @PathVariable("set_id") String setId) {
        return client.deleteBenefitSetsSetId(appId(), setId);
    }

    @GetMapping("/benefit/api/v1/tenant/users/{userid}/assets")
    public Object getUsersUseridAssets(
            @PathVariable("userid") String userid) {
        return client.getUsersUseridAssets(appId(), userid);
    }

    @GetMapping("/benefit/api/v1/tenant/users/{userid}/consumes")
    public Object getUsersUseridConsumes(
            @PathVariable("userid") String userid) {
        return client.getUsersUseridConsumes(appId(), userid);
    }

    @PostMapping("/benefit/api/v1/tenant/subscriptions")
    public Object postSubscriptions(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostSubscriptionsRequest req) {
        return client.postSubscriptions(appId(), req);
    }

    /** 批量发放订阅 (运营活动 N 用户一次, 循环 + 幂等 external_order_id) */
    @PostMapping("/benefit/api/v1/tenant/subscriptions/batch")
    public Object postSubscriptionsBatch(
            @RequestBody java.util.List<fun.commons.benefit4j.dto.PostSubscriptionsRequest> requests) {
        Long appId = appId();
        java.util.List<Object> results = new java.util.ArrayList<>();
        int success = 0, failed = 0;
        for (fun.commons.benefit4j.dto.PostSubscriptionsRequest req : requests) {
            try {
                results.add(client.postSubscriptions(appId, req));
                success++;
            } catch (Exception e) {
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("external_order_id", req.getExternalOrderId());
                err.put("error", e.getMessage());
                results.add(err);
                failed++;
            }
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", requests.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("results", results);
        return fun.commons.framework4j.web.ApiResponse.success(result);
    }

    @PostMapping("/benefit/api/v1/tenant/subscriptions/{subscribe_id}/disable")
    public Object postSubscriptionsSubscribeIdDisable(
            @PathVariable("subscribe_id") String subscribeId,
            @Valid @RequestBody fun.commons.benefit4j.dto.PostSubscriptionsSubscribeIdDisableRequest req) {
        return client.postSubscriptionsSubscribeIdDisable(appId(), subscribeId, req);
    }

    @GetMapping("/benefit/api/v1/tenant/subscriptions/{subscribe_id}/items")
    public Object getSubscriptionsSubscribeIdItems(
            @PathVariable("subscribe_id") String subscribeId,
            @RequestParam(value = "item_id", required = false) String itemId) {
        return client.getSubscriptionsSubscribeIdItems(appId(), subscribeId, itemId);
    }

    @PostMapping("/benefit/api/v1/tenant/compensations")
    public Object postCompensations(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostCompensationsRequest req) {
        return client.postCompensations(appId(), req);
    }

    @GetMapping("/benefit/api/v1/tenant/subscriptions")
    public Object getSubscriptions(
            @RequestParam(value = "userid", required = false) String userid,
            @RequestParam(value = "set_id", required = false) String setId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "external_order_id", required = false) String externalOrderId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "date_begin_start", required = false) java.time.OffsetDateTime dateBeginStart,
            @RequestParam(value = "date_begin_end", required = false) java.time.OffsetDateTime dateBeginEnd,
            @RequestParam(value = "created_at_start", required = false) java.time.OffsetDateTime createdAtStart,
            @RequestParam(value = "created_at_end", required = false) java.time.OffsetDateTime createdAtEnd,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        return client.getSubscriptions(appId(), userid, setId, status, externalOrderId, keyword, dateBeginStart, dateBeginEnd, createdAtStart, createdAtEnd, page, size);
    }

    @GetMapping("/benefit/api/v1/tenant/consumes")
    public Object getConsumes(
            @RequestParam(value = "userid", required = false) String userid,
            @RequestParam(value = "subs_item_id", required = false) String subsItemId,
            @RequestParam(value = "item_id", required = false) String itemId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "external_order_id", required = false) String externalOrderId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "consume_num_min", required = false) Integer consumeNumMin,
            @RequestParam(value = "consume_num_max", required = false) Integer consumeNumMax,
            @RequestParam(value = "consume_time_start", required = false) java.time.OffsetDateTime consumeTimeStart,
            @RequestParam(value = "consume_time_end", required = false) java.time.OffsetDateTime consumeTimeEnd,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        return client.getConsumes(appId(), userid, subsItemId, itemId, status, externalOrderId, keyword, consumeNumMin, consumeNumMax, consumeTimeStart, consumeTimeEnd, page, size);
    }

    @PostMapping("/benefit/api/v1/tenant/consumes/{consume_id}/refund")
    public Object postConsumesIdRefund(
            @PathVariable("consume_id") String consumeId,
            @Valid @RequestBody fun.commons.benefit4j.dto.PostConsumesIdRefundRequest req) {
        return client.postConsumesIdRefund(appId(), consumeId, req);
    }

    private Long appId() {
        Object claim = TokenContext.getClaim("app_id");
        if (claim == null) return null;
        if (claim instanceof Long l) return l;
        if (claim instanceof Number n) return n.longValue();
        return IdObfuscator.fromOpenId(String.valueOf(claim));
    }
}