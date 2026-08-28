package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitTenantClient;
import fun.commons.benefit4j.service.BenefitTenantService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalBenefitTenantClient implements BenefitTenantClient {

    private final BenefitTenantService service;

    @Override
    public Object postBenefitItems(Long tenantId, fun.commons.benefit4j.dto.PostBenefitItemsRequest req) {
        return service.postBenefitItems(tenantId, req);
    }

    @Override
    public Object getBenefitItems(Long tenantId) {
        return service.getBenefitItems(tenantId);
    }

    @Override
    public Object getBenefitItemsItemId(Long tenantId, String itemId) {
        return service.getBenefitItemsItemId(tenantId, itemId);
    }

    @Override
    public Object putBenefitItemsItemId(Long tenantId, String itemId, fun.commons.benefit4j.dto.PutBenefitItemsItemIdRequest req) {
        return service.putBenefitItemsItemId(tenantId, itemId, req);
    }

    @Override
    public Object deleteBenefitItemsItemId(Long tenantId, String itemId) {
        return service.deleteBenefitItemsItemId(tenantId, itemId);
    }

    @Override
    public Object getBenefitTemplates(Long tenantId) {
        return service.getBenefitTemplates(tenantId);
    }

    @Override
    public Object postBenefitSets(Long tenantId, fun.commons.benefit4j.dto.PostBenefitSetsRequest req) {
        return service.postBenefitSets(tenantId, req);
    }

    @Override
    public Object getBenefitSets(Long tenantId) {
        return service.getBenefitSets(tenantId);
    }

    @Override
    public Object getBenefitSetsSetId(Long tenantId, String setId) {
        return service.getBenefitSetsSetId(tenantId, setId);
    }

    @Override
    public Object putBenefitSetsSetId(Long tenantId, String setId, fun.commons.benefit4j.dto.PutBenefitSetsSetIdRequest req) {
        return service.putBenefitSetsSetId(tenantId, setId, req);
    }

    @Override
    public Object deleteBenefitSetsSetId(Long tenantId, String setId) {
        return service.deleteBenefitSetsSetId(tenantId, setId);
    }

    @Override
    public Object getUsersUseridAssets(Long tenantId, String userid) {
        return service.getUsersUseridAssets(tenantId, userid);
    }

    @Override
    public Object getUsersUseridConsumes(Long tenantId, String userid) {
        return service.getUsersUseridConsumes(tenantId, userid);
    }

    @Override
    public Object postSubscriptions(Long tenantId, fun.commons.benefit4j.dto.PostSubscriptionsRequest req) {
        return service.postSubscriptions(tenantId, req);
    }

    @Override
    public Object postSubscriptionsSubscribeIdDisable(Long tenantId, String subscribeId, fun.commons.benefit4j.dto.PostSubscriptionsSubscribeIdDisableRequest req) {
        return service.postSubscriptionsSubscribeIdDisable(tenantId, subscribeId, req);
    }

    @Override
    public Object postCompensations(Long tenantId, fun.commons.benefit4j.dto.PostCompensationsRequest req) {
        return service.postCompensations(tenantId, req);
    }

    @Override
    public Object getSubscriptions(Long tenantId, String userid, String setId, String status, String externalOrderId, String keyword, java.time.OffsetDateTime dateBeginStart, java.time.OffsetDateTime dateBeginEnd, java.time.OffsetDateTime createdAtStart, java.time.OffsetDateTime createdAtEnd, Integer page, Integer size) {
        return service.getSubscriptions(tenantId, userid, setId, status, externalOrderId, keyword, dateBeginStart, dateBeginEnd, createdAtStart, createdAtEnd, page, size);
    }

    @Override
    public Object getConsumes(Long tenantId, String userid, String subsItemId, String itemId, String status, String externalOrderId, String keyword, Integer consumeNumMin, Integer consumeNumMax, java.time.OffsetDateTime consumeTimeStart, java.time.OffsetDateTime consumeTimeEnd, Integer page, Integer size) {
        return service.getConsumes(tenantId, userid, subsItemId, itemId, status, externalOrderId, keyword, consumeNumMin, consumeNumMax, consumeTimeStart, consumeTimeEnd, page, size);
    }

    @Override
    public Object postConsumesIdRefund(Long tenantId, String consumeId, fun.commons.benefit4j.dto.PostConsumesIdRefundRequest req) {
        return service.postConsumesIdRefund(tenantId, consumeId, req);
    }

    @Override
    public Object getSubscriptionsSubscribeIdItems(Long tenantId, String subscribeId, String itemId) {
        return service.getSubscriptionsSubscribeIdItems(tenantId, subscribeId, itemId);
    }

}