package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitRuntimeClient;
import fun.commons.benefit4j.service.BenefitRuntimeService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalBenefitRuntimeClient implements BenefitRuntimeClient {

    private final BenefitRuntimeService service;

    @Override
    public Object postSubscriptions(Long tenantId, fun.commons.benefit4j.dto.PostSubscriptionsRequest req) {
        return service.postSubscriptions(tenantId, req);
    }

    @Override
    public Object postSubscriptionsCancel(Long tenantId, fun.commons.benefit4j.dto.PostSubscriptionsCancelRequest req) {
        return service.postSubscriptionsCancel(tenantId, req);
    }

    @Override
    public Object getSubscriptionsSubscribeId(Long tenantId, String subscribeId) {
        return service.getSubscriptionsSubscribeId(tenantId, subscribeId);
    }

    @Override
    public Object postConsumesDirect(Long tenantId, fun.commons.benefit4j.dto.PostConsumesDirectRequest req) {
        return service.postConsumesDirect(tenantId, req);
    }

    @Override
    public Object postConsumesReserve(Long tenantId, fun.commons.benefit4j.dto.PostConsumesReserveRequest req) {
        return service.postConsumesReserve(tenantId, req);
    }

    @Override
    public Object postConsumesCommit(Long tenantId, fun.commons.benefit4j.dto.PostConsumesCommitRequest req) {
        return service.postConsumesCommit(tenantId, req);
    }

    @Override
    public Object postConsumesRelease(Long tenantId, fun.commons.benefit4j.dto.PostConsumesReleaseRequest req) {
        return service.postConsumesRelease(tenantId, req);
    }

    @Override
    public Object postRefunds(Long tenantId, fun.commons.benefit4j.dto.PostRefundsRequest req) {
        return service.postRefunds(tenantId, req);
    }

    @Override
    public Object getUsersUseridAssets(Long tenantId, String userid) {
        return service.getUsersUseridAssets(tenantId, userid);
    }

    @Override
    public Object getUsersUseridConsumes(Long tenantId, String userid) {
        return service.getUsersUseridConsumes(tenantId, userid);
    }

}