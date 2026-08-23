package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.client.BenefitRuntimeClient;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.ratelimit.annotation.RateLimit;
import fun.commons.framework4j.signature.annotation.RequiresSignature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequiresToken(value = "APP", type = "access")
@RequiresSignature   // 对外 runtime API 强制 HMAC-SHA256 签名防重放 (X-Access-Key/X-Timestamp/X-Nonce/X-Signature)
public class BenefitRuntimeController {

    private final BenefitRuntimeClient client;

    // 消费/资金类: 50 次/分/App (严格, 防刷)
    @PostMapping("/benefit/api/v1/runtime/subscriptions")
    @RateLimit(limit = 50, window = "1m", scope = "APP")
    public Object postSubscriptions(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostSubscriptionsRequest req) {
        return client.postSubscriptions(appId(), req);
    }

    @PostMapping("/benefit/api/v1/runtime/subscriptions/cancel")
    @RateLimit(limit = 50, window = "1m", scope = "APP")
    public Object postSubscriptionsCancel(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostSubscriptionsCancelRequest req) {
        return client.postSubscriptionsCancel(appId(), req);
    }

    @GetMapping("/benefit/api/v1/runtime/subscriptions/{subscribe_id}")
    @RateLimit(limit = 300, window = "1m", scope = "APP")
    public Object getSubscriptionsSubscribeId(
            @PathVariable("subscribe_id") String subscribeId) {
        return client.getSubscriptionsSubscribeId(appId(), subscribeId);
    }

    @PostMapping("/benefit/api/v1/runtime/consumes/direct")
    @RateLimit(limit = 50, window = "1m", scope = "APP")
    public Object postConsumesDirect(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostConsumesDirectRequest req) {
        return client.postConsumesDirect(appId(), req);
    }

    @PostMapping("/benefit/api/v1/runtime/consumes/reserve")
    @RateLimit(limit = 50, window = "1m", scope = "APP")
    public Object postConsumesReserve(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostConsumesReserveRequest req) {
        return client.postConsumesReserve(appId(), req);
    }

    @PostMapping("/benefit/api/v1/runtime/consumes/commit")
    @RateLimit(limit = 50, window = "1m", scope = "APP")
    public Object postConsumesCommit(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostConsumesCommitRequest req) {
        return client.postConsumesCommit(appId(), req);
    }

    @PostMapping("/benefit/api/v1/runtime/consumes/release")
    @RateLimit(limit = 50, window = "1m", scope = "APP")
    public Object postConsumesRelease(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostConsumesReleaseRequest req) {
        return client.postConsumesRelease(appId(), req);
    }

    @PostMapping("/benefit/api/v1/runtime/refunds")
    @RateLimit(limit = 50, window = "1m", scope = "APP")
    public Object postRefunds(
            @Valid @RequestBody fun.commons.benefit4j.dto.PostRefundsRequest req) {
        return client.postRefunds(appId(), req);
    }

    // 查询类: 300 次/分/App (宽松)
    @GetMapping("/benefit/api/v1/runtime/users/{userid}/assets")
    @RateLimit(limit = 300, window = "1m", scope = "APP")
    public Object getUsersUseridAssets(
            @PathVariable("userid") String userid) {
        return client.getUsersUseridAssets(appId(), userid);
    }

    @GetMapping("/benefit/api/v1/runtime/users/{userid}/consumes")
    @RateLimit(limit = 300, window = "1m", scope = "APP")
    public Object getUsersUseridConsumes(
            @PathVariable("userid") String userid) {
        return client.getUsersUseridConsumes(appId(), userid);
    }

    private Long appId() {
        Object claim = TokenContext.getClaim("app_id");
        if (claim == null) return null;
        if (claim instanceof Long l) return l;
        if (claim instanceof Number n) return n.longValue();
        return IdObfuscator.fromOpenId(String.valueOf(claim));
    }
}