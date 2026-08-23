package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitRuntimeClient;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.benefit4j.transport.HttpTransport;

/**
 * remote 模式: 业务方跨进程调用独立部署的 benefit4j。
 * <p>
 * 通过 {@link HttpTransport} 发 HTTP, 自动带 Idempotency-Key (写操作)。
 * S2S JWT / 签名由业务方在 HttpTransport 拦截器层注入 (可替换 Bean)。
 */
public class RemoteBenefitRuntimeClient extends AbstractRemoteBenefitClient implements BenefitRuntimeClient {

    public RemoteBenefitRuntimeClient(Benefit4jProperties properties, HttpTransport transport) {
        super(properties, transport);
    }

    @Override
    public Object postSubscriptions(Long appId, PostSubscriptionsRequest req) {
        return invoke("/benefit/api/v1/runtime/subscriptions", "POST", appId, req);
    }

    @Override
    public Object postSubscriptionsCancel(Long appId, PostSubscriptionsCancelRequest req) {
        return invoke("/benefit/api/v1/runtime/subscriptions/cancel", "POST", appId, req);
    }

    @Override
    public Object getSubscriptionsSubscribeId(Long appId, String subscribeId) {
        return invoke("/benefit/api/v1/runtime/subscriptions/" + subscribeId, "GET", appId, null);
    }

    @Override
    public Object postConsumesDirect(Long appId, PostConsumesDirectRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/direct", "POST", appId, req);
    }

    @Override
    public Object postConsumesReserve(Long appId, PostConsumesReserveRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/reserve", "POST", appId, req);
    }

    @Override
    public Object postConsumesCommit(Long appId, PostConsumesCommitRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/commit", "POST", appId, req);
    }

    @Override
    public Object postConsumesRelease(Long appId, PostConsumesReleaseRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/release", "POST", appId, req);
    }

    @Override
    public Object postRefunds(Long appId, PostRefundsRequest req) {
        return invoke("/benefit/api/v1/runtime/refunds", "POST", appId, req);
    }

    @Override
    public Object getUsersUseridAssets(Long appId, String userid) {
        return invoke("/benefit/api/v1/runtime/users/" + userid + "/assets", "GET", appId, null);
    }

    @Override
    public Object getUsersUseridConsumes(Long appId, String userid) {
        return invoke("/benefit/api/v1/runtime/users/" + userid + "/consumes", "GET", appId, null);
    }
}
