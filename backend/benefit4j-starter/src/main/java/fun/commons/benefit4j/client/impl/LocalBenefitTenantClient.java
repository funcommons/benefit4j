package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitTenantClient;
import fun.commons.benefit4j.service.BenefitTenantService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalBenefitTenantClient implements BenefitTenantClient {

    private final BenefitTenantService service;

    @Override
    public Object postBenefitItems(Long appId, fun.commons.benefit4j.dto.PostBenefitItemsRequest req) {
        return service.postBenefitItems(appId, req);
    }

    @Override
    public Object getBenefitItems(Long appId) {
        return service.getBenefitItems(appId);
    }

    @Override
    public Object getBenefitItemsItemId(Long appId, String itemId) {
        return service.getBenefitItemsItemId(appId, itemId);
    }

    @Override
    public Object putBenefitItemsItemId(Long appId, String itemId, fun.commons.benefit4j.dto.PutBenefitItemsItemIdRequest req) {
        return service.putBenefitItemsItemId(appId, itemId, req);
    }

    @Override
    public Object deleteBenefitItemsItemId(Long appId, String itemId) {
        return service.deleteBenefitItemsItemId(appId, itemId);
    }

    @Override
    public Object getBenefitTemplates(Long appId) {
        return service.getBenefitTemplates(appId);
    }

    @Override
    public Object postBenefitSets(Long appId, fun.commons.benefit4j.dto.PostBenefitSetsRequest req) {
        return service.postBenefitSets(appId, req);
    }

    @Override
    public Object getBenefitSets(Long appId) {
        return service.getBenefitSets(appId);
    }

    @Override
    public Object getBenefitSetsSetId(Long appId, String setId) {
        return service.getBenefitSetsSetId(appId, setId);
    }

    @Override
    public Object putBenefitSetsSetId(Long appId, String setId, fun.commons.benefit4j.dto.PutBenefitSetsSetIdRequest req) {
        return service.putBenefitSetsSetId(appId, setId, req);
    }

    @Override
    public Object deleteBenefitSetsSetId(Long appId, String setId) {
        return service.deleteBenefitSetsSetId(appId, setId);
    }

    @Override
    public Object getUsersUseridAssets(Long appId, String userid) {
        return service.getUsersUseridAssets(appId, userid);
    }

    @Override
    public Object getUsersUseridConsumes(Long appId, String userid) {
        return service.getUsersUseridConsumes(appId, userid);
    }

    @Override
    public Object postSubscriptions(Long appId, fun.commons.benefit4j.dto.PostSubscriptionsRequest req) {
        return service.postSubscriptions(appId, req);
    }

    @Override
    public Object postSubscriptionsSubscribeIdDisable(Long appId, String subscribeId, fun.commons.benefit4j.dto.PostSubscriptionsSubscribeIdDisableRequest req) {
        return service.postSubscriptionsSubscribeIdDisable(appId, subscribeId, req);
    }

    @Override
    public Object postCompensations(Long appId, fun.commons.benefit4j.dto.PostCompensationsRequest req) {
        return service.postCompensations(appId, req);
    }

    @Override
    public Object getSubscriptions(Long appId, String userid, String setId, String status, String externalOrderId, String keyword, java.time.OffsetDateTime dateBeginStart, java.time.OffsetDateTime dateBeginEnd, java.time.OffsetDateTime createdAtStart, java.time.OffsetDateTime createdAtEnd, Integer page, Integer size) {
        return service.getSubscriptions(appId, userid, setId, status, externalOrderId, keyword, dateBeginStart, dateBeginEnd, createdAtStart, createdAtEnd, page, size);
    }

    @Override
    public Object getConsumes(Long appId, String userid, String subsItemId, String itemId, String status, String externalOrderId, String keyword, Integer consumeNumMin, Integer consumeNumMax, java.time.OffsetDateTime consumeTimeStart, java.time.OffsetDateTime consumeTimeEnd, Integer page, Integer size) {
        return service.getConsumes(appId, userid, subsItemId, itemId, status, externalOrderId, keyword, consumeNumMin, consumeNumMax, consumeTimeStart, consumeTimeEnd, page, size);
    }

    @Override
    public Object postConsumesIdRefund(Long appId, String consumeId, fun.commons.benefit4j.dto.PostConsumesIdRefundRequest req) {
        return service.postConsumesIdRefund(appId, consumeId, req);
    }

    @Override
    public Object getSubscriptionsSubscribeIdItems(Long appId, String subscribeId, String itemId) {
        return service.getSubscriptionsSubscribeIdItems(appId, subscribeId, itemId);
    }

}