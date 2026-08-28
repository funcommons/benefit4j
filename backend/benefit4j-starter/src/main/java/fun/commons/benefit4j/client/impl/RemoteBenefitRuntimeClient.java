package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitRuntimeClient;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.framework4j.transport.HttpTransport;

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
    public Object postSubscriptions(Long tenantId, PostSubscriptionsRequest req) {
        return invoke("/benefit/api/v1/runtime/subscriptions", "POST", tenantId, req);
    }

    @Override
    public Object postSubscriptionsCancel(Long tenantId, PostSubscriptionsCancelRequest req) {
        return invoke("/benefit/api/v1/runtime/subscriptions/cancel", "POST", tenantId, req);
    }

    @Override
    public Object getSubscriptionsSubscribeId(Long tenantId, String subscribeId) {
        return invoke("/benefit/api/v1/runtime/subscriptions/" + subscribeId, "GET", tenantId, null);
    }

    @Override
    public Object postConsumesDirect(Long tenantId, PostConsumesDirectRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/direct", "POST", tenantId, req);
    }

    @Override
    public Object postConsumesReserve(Long tenantId, PostConsumesReserveRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/reserve", "POST", tenantId, req);
    }

    @Override
    public Object postConsumesCommit(Long tenantId, PostConsumesCommitRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/commit", "POST", tenantId, req);
    }

    @Override
    public Object postConsumesRelease(Long tenantId, PostConsumesReleaseRequest req) {
        return invoke("/benefit/api/v1/runtime/consumes/release", "POST", tenantId, req);
    }

    @Override
    public Object postRefunds(Long tenantId, PostRefundsRequest req) {
        return invoke("/benefit/api/v1/runtime/refunds", "POST", tenantId, req);
    }

    @Override
    public Object getUsersUseridAssets(Long tenantId, String userid) {
        return invoke("/benefit/api/v1/runtime/users/" + userid + "/assets", "GET", tenantId, null);
    }

    @Override
    public Object getUsersUseridConsumes(Long tenantId, String userid) {
        return invoke("/benefit/api/v1/runtime/users/" + userid + "/consumes", "GET", tenantId, null);
    }
}
