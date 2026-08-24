package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitTenantClient;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.framework4j.transport.HttpTransport;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * remote 模式 tenant client (跨进程调远端 benefit4j tenant 域)。
 */
public class RemoteBenefitTenantClient extends AbstractRemoteBenefitClient implements BenefitTenantClient {

    public RemoteBenefitTenantClient(Benefit4jProperties properties, HttpTransport transport) {
        super(properties, transport);
    }

    @Override
    public Object postBenefitItems(Long appId, PostBenefitItemsRequest req) {
        return invoke("/benefit/api/v1/tenant/benefit-items", "POST", appId, req);
    }

    @Override
    public Object getBenefitItems(Long appId) {
        return invoke("/benefit/api/v1/tenant/benefit-items", "GET", appId, null);
    }

    @Override
    public Object getBenefitItemsItemId(Long appId, String itemId) {
        return invoke("/benefit/api/v1/tenant/benefit-items/" + itemId, "GET", appId, null);
    }

    @Override
    public Object putBenefitItemsItemId(Long appId, String itemId, PutBenefitItemsItemIdRequest req) {
        return invoke("/benefit/api/v1/tenant/benefit-items/" + itemId, "PUT", appId, req);
    }

    @Override
    public Object deleteBenefitItemsItemId(Long appId, String itemId) {
        return invoke("/benefit/api/v1/tenant/benefit-items/" + itemId, "DELETE", appId, null);
    }

    @Override
    public Object getBenefitTemplates(Long appId) {
        return invoke("/benefit/api/v1/tenant/benefit-templates", "GET", appId, null);
    }

    @Override
    public Object postBenefitSets(Long appId, PostBenefitSetsRequest req) {
        return invoke("/benefit/api/v1/tenant/benefit-sets", "POST", appId, req);
    }

    @Override
    public Object getBenefitSets(Long appId) {
        return invoke("/benefit/api/v1/tenant/benefit-sets", "GET", appId, null);
    }

    @Override
    public Object getBenefitSetsSetId(Long appId, String setId) {
        return invoke("/benefit/api/v1/tenant/benefit-sets/" + setId, "GET", appId, null);
    }

    @Override
    public Object putBenefitSetsSetId(Long appId, String setId, PutBenefitSetsSetIdRequest req) {
        return invoke("/benefit/api/v1/tenant/benefit-sets/" + setId, "PUT", appId, req);
    }

    @Override
    public Object deleteBenefitSetsSetId(Long appId, String setId) {
        return invoke("/benefit/api/v1/tenant/benefit-sets/" + setId, "DELETE", appId, null);
    }

    @Override
    public Object getUsersUseridAssets(Long appId, String userid) {
        return invoke("/benefit/api/v1/tenant/users/" + userid + "/assets", "GET", appId, null);
    }

    @Override
    public Object getUsersUseridConsumes(Long appId, String userid) {
        return invoke("/benefit/api/v1/tenant/users/" + userid + "/consumes", "GET", appId, null);
    }

    @Override
    public Object postSubscriptions(Long appId, PostSubscriptionsRequest req) {
        return invoke("/benefit/api/v1/tenant/subscriptions", "POST", appId, req);
    }

    @Override
    public Object postSubscriptionsSubscribeIdDisable(Long appId, String subscribeId, PostSubscriptionsSubscribeIdDisableRequest req) {
        return invoke("/benefit/api/v1/tenant/subscriptions/" + subscribeId + "/disable", "POST", appId, req);
    }

    @Override
    public Object postCompensations(Long appId, PostCompensationsRequest req) {
        return invoke("/benefit/api/v1/tenant/compensations", "POST", appId, req);
    }

    @Override
    public Object getSubscriptions(Long appId, String userid, String setId, String status,
                                   String externalOrderId, String keyword,
                                   OffsetDateTime dateBeginStart, OffsetDateTime dateBeginEnd,
                                   OffsetDateTime createdAtStart, OffsetDateTime createdAtEnd,
                                   Integer page, Integer size) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("userid", userid); q.put("set_id", setId); q.put("status", status);
        q.put("external_order_id", externalOrderId); q.put("keyword", keyword);
        q.put("date_begin_start", toStr(dateBeginStart)); q.put("date_begin_end", toStr(dateBeginEnd));
        q.put("created_at_start", toStr(createdAtStart)); q.put("created_at_end", toStr(createdAtEnd));
        q.put("page", toStr(page)); q.put("size", toStr(size));
        return invoke("/benefit/api/v1/tenant/subscriptions", "GET", appId, null, q);
    }

    @Override
    public Object getSubscriptionsSubscribeIdItems(Long appId, String subscribeId, String itemId) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("item_id", itemId);
        return invoke("/benefit/api/v1/tenant/subscriptions/" + subscribeId + "/items", "GET", appId, null, q);
    }

    @Override
    public Object getConsumes(Long appId, String userid, String subsItemId, String itemId, String status,
                              String externalOrderId, String keyword, Integer consumeNumMin, Integer consumeNumMax,
                              OffsetDateTime consumeTimeStart, OffsetDateTime consumeTimeEnd,
                              Integer page, Integer size) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("userid", userid); q.put("subs_item_id", subsItemId); q.put("item_id", itemId);
        q.put("status", status); q.put("external_order_id", externalOrderId); q.put("keyword", keyword);
        q.put("consume_num_min", toStr(consumeNumMin)); q.put("consume_num_max", toStr(consumeNumMax));
        q.put("consume_time_start", toStr(consumeTimeStart)); q.put("consume_time_end", toStr(consumeTimeEnd));
        q.put("page", toStr(page)); q.put("size", toStr(size));
        return invoke("/benefit/api/v1/tenant/consumes", "GET", appId, null, q);
    }

    @Override
    public Object postConsumesIdRefund(Long appId, String consumeId, PostConsumesIdRefundRequest req) {
        return invoke("/benefit/api/v1/tenant/consumes/" + consumeId + "/refund", "POST", appId, req);
    }

    private static String toStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
