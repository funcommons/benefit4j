package fun.commons.benefit4j.client;

public interface BenefitRuntimeClient {
    // 业务触发: 发放/订阅权益产品
    Object postSubscriptions(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.RequestBody fun.commons.benefit4j.dto.PostSubscriptionsRequest req);

    // 业务触发: 业务侧主动退订/回收资产
    Object postSubscriptionsCancel(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.RequestBody fun.commons.benefit4j.dto.PostSubscriptionsCancelRequest req);

    // 业务侧联机查询: 特定订阅单详情
    Object getSubscriptionsSubscribeId(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.PathVariable("subscribe_id") String subscribeId);

    // 核心: 权益直接核销扣减 (一阶段)
    Object postConsumesDirect(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.RequestBody fun.commons.benefit4j.dto.PostConsumesDirectRequest req);

    // 核心: 预扣减/冻结额度 (TCC Try)
    Object postConsumesReserve(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.RequestBody fun.commons.benefit4j.dto.PostConsumesReserveRequest req);

    // 核心: 确认预扣减 (TCC Confirm)
    Object postConsumesCommit(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.RequestBody fun.commons.benefit4j.dto.PostConsumesCommitRequest req);

    // 核心: 释放预扣减/解冻 (TCC Cancel)
    Object postConsumesRelease(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.RequestBody fun.commons.benefit4j.dto.PostConsumesReleaseRequest req);

    // 业务触发: 已核销完成资产的反向退回
    Object postRefunds(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.RequestBody fun.commons.benefit4j.dto.PostRefundsRequest req);

    // 联机查询: 获取用户可用大盘视图
    Object getUsersUseridAssets(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.PathVariable("userid") String userid);

    // 联机查询: 分页拉取用户消耗流水
    Object getUsersUseridConsumes(@org.springframework.web.bind.annotation.RequestHeader(value="X-App-Id", required=false) Long appId, @org.springframework.web.bind.annotation.PathVariable("userid") String userid);

}