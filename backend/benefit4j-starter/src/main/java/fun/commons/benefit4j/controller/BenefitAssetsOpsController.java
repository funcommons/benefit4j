package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.assets.dto.OpsAssetRequest;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * assets 域 OPS API(assets-design §4.1): 资产注册中心管理(运营端)。
 * 调账(ADJUST)双签审计为 P2;FIAT 创建受 AssetsFiatGuard 运行时拦截。
 */
@RestController
@RequestMapping("/benefit/api/v1/assets/ops")
@RequiredArgsConstructor
@RequiresToken(value = "OPS", type = "access")
public class BenefitAssetsOpsController {

    private final AssetRegistryService registry;
    private final fun.commons.benefit4j.assets.service.AssetsReconcileService reconcileService;
    private final fun.commons.benefit4j.assets.service.AssetsIdempotencyService idempotencyService;
    private final fun.commons.benefit4j.assets.service.AccountService accountService;

    @PostMapping("/assets")
    @Auditable(action = "ASSETS_CREATE", targetType = "asset", targetIdSpel = "#req.code")
    public ApiResponse<UbmxAsset> postAssets(@Valid @RequestBody OpsAssetRequest req) {
        return ApiResponse.success(registry.createAsset(toEntity(req)));
    }

    @GetMapping("/assets")
    public ApiResponse<List<UbmxAsset>> getAssets(
            @RequestParam(value = "asset_type", required = false) String assetType,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(registry.list(assetType, status));
    }

    @GetMapping("/assets/{code}")
    public ApiResponse<UbmxAsset> getAsset(@PathVariable("code") String code) {
        return ApiResponse.success(registry.getRequired(code));
    }

    /** 局部更新(白名单字段,null 跳过) */
    @PatchMapping("/assets/{code}")
    @Auditable(action = "ASSETS_PATCH", targetType = "asset", targetIdSpel = "#code")
    public ApiResponse<UbmxAsset> patchAsset(@PathVariable("code") String code,
            @RequestBody OpsAssetRequest req) {
        return ApiResponse.success(registry.patchAsset(code, toEntity(req)));
    }

    @PostMapping("/assets/{code}/suspend")
    @Auditable(action = "ASSETS_SUSPEND", targetType = "asset", targetIdSpel = "#code")
    public ApiResponse<Void> suspend(@PathVariable("code") String code) {
        registry.suspend(code);
        return ApiResponse.success();
    }

    @PostMapping("/assets/{code}/resume")
    public ApiResponse<Void> resume(@PathVariable("code") String code) {
        registry.resume(code);
        return ApiResponse.success();
    }

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

    private UbmxAsset toEntity(OpsAssetRequest req) {
        UbmxAsset a = new UbmxAsset();
        a.setCode(req.getCode());
        a.setName(req.getName());
        a.setAssetType(req.getAssetType());
        a.setPrecision(req.getPrecision());
        a.setCanRecharge(req.getCanRecharge());
        a.setCanWithdraw(req.getCanWithdraw());
        a.setCanPay(req.getCanPay());
        a.setCanTransfer(req.getCanTransfer());
        a.setCanExchange(req.getCanExchange());
        a.setCanCredit(req.getCanCredit());
        a.setIssueMode(req.getIssueMode());
        a.setExpirePolicy(req.getExpirePolicy());
        a.setLimitPolicy(req.getLimitPolicy());
        a.setDescription(req.getDescription());
        return a;
    }
}
