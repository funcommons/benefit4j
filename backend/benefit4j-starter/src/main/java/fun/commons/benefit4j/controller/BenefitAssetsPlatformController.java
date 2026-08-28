package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.assets.dto.OpsAssetRequest;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.benefit4j.assets.service.AssetsQueryService;
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
import java.util.Set;

/**
 * assets 域平台运营面(P3 前端): 资产注册中心 CRUD + 运营查询。
 * 与既有 platform 控制器同为 APP 型 token —— 平台登录(fvUQ…/PLATFORM 凭据)与租户登录同型,
 * 区别仅 tenant_id claim(平台=0);查询按「平台视角」跨 app,tenant_id 参数可选收窄。
 * 运维通道(对账/幂等释放/授信调额)在 {@link BenefitAssetsOpsController}(OPS 型)。
 */
@RestController
@RequestMapping("/benefit/api/v1/platform/assets")
@RequiredArgsConstructor
@RequiresToken(value = "APP", type = "access")
public class BenefitAssetsPlatformController {

    private static final Set<String> OWNER_TYPES = Set.of("USER", "TENANT", "MERCHANT", "PLATFORM", "EXTERNAL");

    private final AssetRegistryService registry;
    private final AssetsQueryService queryService;

    @PostMapping
    @Auditable(action = "ASSETS_CREATE", targetType = "asset", targetIdSpel = "#req.code")
    public ApiResponse<UbmxAsset> postAssets(@Valid @RequestBody OpsAssetRequest req) {
        return ApiResponse.success(registry.createAsset(toEntity(req)));
    }

    @GetMapping
    public ApiResponse<List<UbmxAsset>> getAssets(
            @RequestParam(value = "asset_type", required = false) String assetType,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(registry.list(assetType, status));
    }

    @GetMapping("/{code}")
    public ApiResponse<UbmxAsset> getAsset(@PathVariable("code") String code) {
        return ApiResponse.success(registry.getRequired(code));
    }

    /** 局部更新(白名单字段,null 跳过) */
    @PatchMapping("/{code}")
    @Auditable(action = "ASSETS_PATCH", targetType = "asset", targetIdSpel = "#code")
    public ApiResponse<UbmxAsset> patchAsset(@PathVariable("code") String code,
            @RequestBody OpsAssetRequest req) {
        return ApiResponse.success(registry.patchAsset(code, toEntity(req)));
    }

    @PostMapping("/{code}/suspend")
    @Auditable(action = "ASSETS_SUSPEND", targetType = "asset", targetIdSpel = "#code")
    public ApiResponse<Void> suspend(@PathVariable("code") String code) {
        registry.suspend(code);
        return ApiResponse.success();
    }

    @PostMapping("/{code}/resume")
    public ApiResponse<Void> resume(@PathVariable("code") String code) {
        registry.resume(code);
        return ApiResponse.success();
    }

    /** 运营查主体资产账户(平台视角跨 app;同 owner 各 app 账户全部返回;不开户) */
    @GetMapping("/accounts")
    public ApiResponse<List<fun.commons.benefit4j.assets.entity.UbmxAccount>> getAccounts(
            @RequestParam("owner_type") String ownerType,
            @RequestParam("owner_id") Long ownerId,
            @RequestParam(value = "asset_code", required = false) String assetCode,
            @RequestParam(value = "tenant_id", required = false) Long tenantId) {
        return ApiResponse.success(queryService.listAccountsByOwner(ownerType, ownerId, assetCode, tenantId));
    }

    /** 运营查账户流水(平台视角;account_ref 如 user:123;账户不存在返回空) */
    @GetMapping("/postings")
    public ApiResponse<Object> getPostings(
            @RequestParam("account_ref") String accountRef,
            @RequestParam("asset_code") String assetCode,
            @RequestParam(value = "tenant_id", required = false) Long tenantId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String[] ref = parseRef(accountRef);
        return ApiResponse.success(queryService.listPostingsByOwner(
                ref[0], Long.parseLong(ref[1]), assetCode, tenantId, page, size));
    }

    /** user:123 → ["USER","123"];运营面只接受主体引用,边界户(issue:*)不在本面 */
    private String[] parseRef(String ref) {
        int colon = ref.indexOf(':');
        if (colon <= 0 || colon == ref.length() - 1) {
            throw invalidRef(ref);
        }
        String ownerType = ref.substring(0, colon).toUpperCase();
        if (!OWNER_TYPES.contains(ownerType)) {
            throw invalidRef(ref);
        }
        String ownerId = ref.substring(colon + 1);
        try {
            Long.parseLong(ownerId);
        } catch (NumberFormatException e) {
            throw invalidRef(ref);
        }
        return new String[]{ownerType, ownerId};
    }

    private fun.commons.benefit4j.assets.exception.AssetsException invalidRef(String ref) {
        return new fun.commons.benefit4j.assets.exception.AssetsException(
                fun.commons.benefit4j.assets.exception.AssetsException.ASSET_INVALID, "非法账户引用: " + ref);
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
