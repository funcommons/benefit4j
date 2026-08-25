package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxAssetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 资产注册中心(assets-design §2.1 / §4.1,OPS 运营端)。
 * 新增资产 = INSERT 一行,账本零 schema 迁移;code 字符串主键(ADR-0008)。
 */
@Service
@RequiredArgsConstructor
public class AssetRegistryService {

    /** 资产码: 大写字母/数字/下划线,≤32(与 DDL varchar(32) 对齐) */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{1,32}$");

    private final UbmxAssetMapper assetMapper;
    private final AssetsFiatGuard fiatGuard;

    public UbmxAsset createAsset(UbmxAsset asset) {
        validate(asset);
        fiatGuard.assertCreationAllowed(asset.getAssetType());
        if (!StringUtils.hasText(asset.getStatus())) {
            asset.setStatus("ACTIVE");
        }
        assetMapper.insert(asset);
        return getRequired(asset.getCode());
    }

    /** 不存在 → ASSET_NOT_FOUND */
    public UbmxAsset getRequired(String code) {
        UbmxAsset a = assetMapper.selectById(code);
        if (a == null) {
            throw new AssetsException(AssetsException.ASSET_NOT_FOUND, "资产不存在: " + code);
        }
        return a;
    }

    /** 业务路径专用: 不存在或非 ACTIVE 都拒绝 */
    public UbmxAsset getActiveRequired(String code) {
        UbmxAsset a = getRequired(code);
        if (!"ACTIVE".equals(a.getStatus())) {
            throw new AssetsException(AssetsException.ASSET_SUSPENDED, "资产已停用: " + code);
        }
        return a;
    }

    public List<UbmxAsset> list(String assetType, String status) {
        LambdaQueryWrapper<UbmxAsset> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(assetType)) q.eq(UbmxAsset::getAssetType, assetType);
        if (StringUtils.hasText(status)) q.eq(UbmxAsset::getStatus, status);
        q.orderByAsc(UbmxAsset::getCode);
        return assetMapper.selectList(q);
    }

    /** 局部更新: 仅允许运营可改字段,null 字段跳过;code/asset_type 不可改 */
    public UbmxAsset patchAsset(String code, UbmxAsset patch) {
        UbmxAsset current = getRequired(code);
        if (StringUtils.hasText(patch.getName())) current.setName(patch.getName());
        if (patch.getPrecision() != null) {
            if (patch.getPrecision() < 0 || patch.getPrecision() > 6) {
                throw new AssetsException(AssetsException.ASSET_INVALID, "precision 必须在 0-6");
            }
            current.setPrecision(patch.getPrecision());
        }
        if (patch.getCanRecharge() != null) current.setCanRecharge(patch.getCanRecharge());
        if (patch.getCanWithdraw() != null) current.setCanWithdraw(patch.getCanWithdraw());
        if (patch.getCanPay() != null) current.setCanPay(patch.getCanPay());
        if (patch.getCanTransfer() != null) current.setCanTransfer(patch.getCanTransfer());
        if (patch.getCanExchange() != null) current.setCanExchange(patch.getCanExchange());
        if (patch.getCanCredit() != null) current.setCanCredit(patch.getCanCredit());
        if (patch.getIssueMode() != null) current.setIssueMode(patch.getIssueMode());
        if (patch.getExpirePolicy() != null) current.setExpirePolicy(patch.getExpirePolicy());
        if (patch.getLimitPolicy() != null) current.setLimitPolicy(patch.getLimitPolicy());
        if (patch.getDescription() != null) current.setDescription(patch.getDescription());
        assetMapper.updateById(current);
        return getRequired(code);
    }

    /** 停用: 已有账户不冻结,新充值/发放禁止(§4.1) */
    public void suspend(String code) {
        updateStatus(code, "SUSPEND");
    }

    public void resume(String code) {
        updateStatus(code, "ACTIVE");
    }

    private void updateStatus(String code, String status) {
        UbmxAsset a = getRequired(code);
        a.setStatus(status);
        assetMapper.updateById(a);
    }

    private void validate(UbmxAsset asset) {
        if (asset.getCode() == null || !CODE_PATTERN.matcher(asset.getCode()).matches()) {
            throw new AssetsException(AssetsException.ASSET_INVALID,
                    "资产码必须为大写字母/数字/下划线且 ≤32 位");
        }
        if (!StringUtils.hasText(asset.getName())) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "资产名不能为空");
        }
        if (!"FIAT".equals(asset.getAssetType()) && !"VIRTUAL".equals(asset.getAssetType())) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "asset_type 必须为 FIAT|VIRTUAL");
        }
        if (asset.getPrecision() == null || asset.getPrecision() < 0 || asset.getPrecision() > 6) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "precision 必须在 0-6");
        }
    }
}
