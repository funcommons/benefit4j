package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitRuntimeClient;
import fun.commons.benefit4j.service.BenefitRuntimeService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalBenefitRuntimeClient implements BenefitRuntimeClient {

    private final BenefitRuntimeService service;

    @Override
    public Object postSubscriptions(Long appId, fun.commons.benefit4j.dto.PostSubscriptionsRequest req) {
        return service.postSubscriptions(appId, req);
    }

    @Override
    public Object postSubscriptionsCancel(Long appId, fun.commons.benefit4j.dto.PostSubscriptionsCancelRequest req) {
        return service.postSubscriptionsCancel(appId, req);
    }

    @Override
    public Object getSubscriptionsSubscribeId(Long appId, String subscribeId) {
        return service.getSubscriptionsSubscribeId(appId, subscribeId);
    }

    @Override
    public Object postConsumesDirect(Long appId, fun.commons.benefit4j.dto.PostConsumesDirectRequest req) {
        return service.postConsumesDirect(appId, req);
    }

    @Override
    public Object postConsumesReserve(Long appId, fun.commons.benefit4j.dto.PostConsumesReserveRequest req) {
        return service.postConsumesReserve(appId, req);
    }

    @Override
    public Object postConsumesCommit(Long appId, fun.commons.benefit4j.dto.PostConsumesCommitRequest req) {
        return service.postConsumesCommit(appId, req);
    }

    @Override
    public Object postConsumesRelease(Long appId, fun.commons.benefit4j.dto.PostConsumesReleaseRequest req) {
        return service.postConsumesRelease(appId, req);
    }

    @Override
    public Object postRefunds(Long appId, fun.commons.benefit4j.dto.PostRefundsRequest req) {
        return service.postRefunds(appId, req);
    }

    @Override
    public Object getUsersUseridAssets(Long appId, String userid) {
        return service.getUsersUseridAssets(appId, userid);
    }

    @Override
    public Object getUsersUseridConsumes(Long appId, String userid) {
        return service.getUsersUseridConsumes(appId, userid);
    }

}