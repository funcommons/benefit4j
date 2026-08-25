package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.assets.dto.OpsAssetRequest;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
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

    @PostMapping("/assets")
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
    public ApiResponse<UbmxAsset> patchAsset(@PathVariable("code") String code,
            @RequestBody OpsAssetRequest req) {
        return ApiResponse.success(registry.patchAsset(code, toEntity(req)));
    }

    @PostMapping("/assets/{code}/suspend")
    public ApiResponse<Void> suspend(@PathVariable("code") String code) {
        registry.suspend(code);
        return ApiResponse.success();
    }

    @PostMapping("/assets/{code}/resume")
    public ApiResponse<Void> resume(@PathVariable("code") String code) {
        registry.resume(code);
        return ApiResponse.success();
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
