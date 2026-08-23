package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitPlatformClient;
import fun.commons.benefit4j.service.BenefitPlatformService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalBenefitPlatformClient implements BenefitPlatformClient {

    private final BenefitPlatformService service;

    @Override
    public Object postApplications(fun.commons.benefit4j.dto.PostApplicationsRequest req) {
        return service.postApplications(req);
    }

    @Override
    public Object getApplications() {
        return service.getApplications();
    }

    @Override
    public Object putApplicationsAppId(Long appId, fun.commons.benefit4j.dto.PutApplicationsAppIdRequest req) {
        return service.putApplicationsAppId(appId, req);
    }

    @Override
    public Object postApplicationsAppIdSecret(Long appId) {
        return service.postApplicationsAppIdSecret(appId);
    }

    @Override
    public Object getApplicationsAppIdSecret(Long appId) {
        return service.getApplicationsAppIdSecret(appId);
    }

    @Override
    public Object postGlobalTemplates(fun.commons.benefit4j.dto.PostGlobalTemplatesRequest req) {
        return service.postGlobalTemplates(req);
    }

    @Override
    public Object getGlobalTemplates() {
        return service.getGlobalTemplates();
    }

    @Override
    public Object putGlobalTemplatesTmplId(Long tmplId, fun.commons.benefit4j.dto.PutGlobalTemplatesTmplIdRequest req) {
        return service.putGlobalTemplatesTmplId(tmplId, req);
    }

    @Override
    public Object deleteGlobalTemplatesTmplId(Long tmplId) {
        return service.deleteGlobalTemplatesTmplId(tmplId);
    }

    @Override
    public Object getStatisticsLiabilities() {
        return service.getStatisticsLiabilities();
    }

    @Override
    public Object getPlatformItems(Long appId, String status, String keyword, java.time.OffsetDateTime createdAtStart, java.time.OffsetDateTime createdAtEnd, Integer page, Integer size) {
        return service.getPlatformItems(appId, status, keyword, createdAtStart, createdAtEnd, page, size);
    }

    @Override
    public Object getPlatformBenefitSets(Long appId, String status, String keyword, Integer priorityMin, Integer priorityMax, java.time.OffsetDateTime createdAtStart, java.time.OffsetDateTime createdAtEnd, Integer page, Integer size) {
        return service.getPlatformBenefitSets(appId, status, keyword, priorityMin, priorityMax, createdAtStart, createdAtEnd, page, size);
    }

    @Override
    public Object postItemTemplates(fun.commons.benefit4j.dto.PostItemTemplatesRequest req) {
        return service.postItemTemplates(req);
    }

    @Override
    public Object getItemTemplates() {
        return service.getItemTemplates();
    }

    @Override
    public Object putItemTemplatesItemId(Long itemId, fun.commons.benefit4j.dto.PutItemTemplatesItemIdRequest req) {
        return service.putItemTemplatesItemId(itemId, req);
    }

    @Override
    public Object deleteItemTemplatesItemId(Long itemId) {
        return service.deleteItemTemplatesItemId(itemId);
    }

    @Override
    public Object getPlatformSubscriptions(Long appId, String userid, String setId, String status, String externalOrderId, String keyword, java.time.OffsetDateTime dateBeginStart, java.time.OffsetDateTime dateBeginEnd, java.time.OffsetDateTime createdAtStart, java.time.OffsetDateTime createdAtEnd, Integer page, Integer size) {
        return service.getPlatformSubscriptions(appId, userid, setId, status, externalOrderId, keyword, dateBeginStart, dateBeginEnd, createdAtStart, createdAtEnd, page, size);
    }

    @Override
    public Object getPlatformConsumes(Long appId, String userid, String subsItemId, String itemId, String status, String externalOrderId, String keyword, Integer consumeNumMin, Integer consumeNumMax, java.time.OffsetDateTime consumeTimeStart, java.time.OffsetDateTime consumeTimeEnd, Integer page, Integer size) {
        return service.getPlatformConsumes(appId, userid, subsItemId, itemId, status, externalOrderId, keyword, consumeNumMin, consumeNumMax, consumeTimeStart, consumeTimeEnd, page, size);
    }

    @Override
    public Object postPlatformConsumesIdRefund(Long appId, String consumeId, fun.commons.benefit4j.dto.PostConsumesIdRefundRequest req) {
        return service.postPlatformConsumesIdRefund(appId, consumeId, req);
    }

    @Override
    public Object getPlatformSubscriptionsSubscribeIdItems(Long appId, String subscribeId, String itemId) {
        return service.getPlatformSubscriptionsSubscribeIdItems(appId, subscribeId, itemId);
    }

}