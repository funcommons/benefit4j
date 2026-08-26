package fun.commons.benefit4j.controller;

import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * assets 域运维通道(assets-design §4.1): 手动对账 / 幂等键释放 / 授信调额。
 * OPS 型 token(由运维签发,登录端点不发)—— 运营页面用的资产 CRUD/查询
 * 在 {@link BenefitAssetsPlatformController}(APP 型,平台登录可用)。
 * 调账(ADJUST)双签审计为 P2;FIAT 创建受 AssetsFiatGuard 运行时拦截。
 */
@RestController
@RequestMapping("/benefit/api/v1/assets/ops")
@RequiredArgsConstructor
@RequiresToken(value = "OPS", type = "access")
public class BenefitAssetsOpsController {

    private final fun.commons.benefit4j.assets.service.AssetsReconcileService reconcileService;
    private final fun.commons.benefit4j.assets.service.AssetsIdempotencyService idempotencyService;
    private final fun.commons.benefit4j.assets.service.AccountService accountService;

    /** 手动触发单资产对账(B5,恒等式+O14+快照;T+1 由 scheduler 自动跑) */
    @PostMapping("/reconcile/run")
    @Auditable(action = "ASSETS_RECONCILE", targetType = "reconcile", targetIdSpel = "#req.assetCode")
    public ApiResponse<Object> runReconcile(@RequestBody ReconcileRunRequest req) {
        return ApiResponse.success(reconcileService.runOnce(req.getAppId(), req.getAssetCode()));
    }

    /** F1 授信调额(资产需开 can_credit;双签审计为后续项,当前 OPS token + @Auditable) */
    @PatchMapping("/accounts/{account_id}/credit-limit")
    @Auditable(action = "ASSETS_CREDIT_LIMIT", targetType = "account", targetIdSpel = "#account_id")
    public ApiResponse<Void> patchCreditLimit(@PathVariable("account_id") Long accountId,
            @RequestBody CreditLimitRequest req) {
        accountService.updateCreditLimit(req.getAppId(), accountId, req.getCreditLimit());
        return ApiResponse.success();
    }

    /** 调额请求 */
    public static class CreditLimitRequest {
        private Long appId;
        private java.math.BigDecimal creditLimit;

        public Long getAppId() { return appId; }
        public void setAppId(Long appId) { this.appId = appId; }
        public java.math.BigDecimal getCreditLimit() { return creditLimit; }
        public void setCreditLimit(java.math.BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    }

    /** 幂等键释放(O6,运维纠错;释放后同号永久禁用,新单必须换号) */
    @PostMapping("/idempotency/release")
    @Auditable(action = "ASSETS_IDEMPOTENCY_RELEASE", targetType = "tx_order", targetIdSpel = "#req.extOrderId")
    public ApiResponse<Object> releaseIdempotency(@RequestBody IdempotencyReleaseRequest req) {
        return ApiResponse.success(idempotencyService.release(req.getAppId(), req.getExtOrderId(), req.getReason()));
    }

    /** 对账手动触发请求 */
    public static class ReconcileRunRequest {
        private Long appId;
        private String assetCode;

        public Long getAppId() { return appId; }
        public void setAppId(Long appId) { this.appId = appId; }
        public String getAssetCode() { return assetCode; }
        public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    }

    /** 幂等释放请求 */
    public static class IdempotencyReleaseRequest {
        private Long appId;
        private String extOrderId;
        private String reason;

        public Long getAppId() { return appId; }
        public void setAppId(Long appId) { this.appId = appId; }
        public String getExtOrderId() { return extOrderId; }
        public void setExtOrderId(String extOrderId) { this.extOrderId = extOrderId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
