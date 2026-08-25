package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxAssetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * FIAT 合规 fail-fast(assets-design §7.1):
 * 资金自持需提前完成合规评估(支付牌照/备付金存管);
 * 环境未授权(assets.fiat-allowed=false)时,启动拦存量、运行时拦新增。
 */
@Component
@RequiredArgsConstructor
public class AssetsFiatGuard {

    private final UbmxAssetMapper assetMapper;

    @Getter
    @Value("${assets.fiat-allowed:false}")
    private boolean fiatAllowed;

    /** 启动检查: 库里已有 FIAT 资产但环境未授权 → fail-fast */
    @PostConstruct
    void startupCheck() {
        checkFiatCount(countFiat(), fiatAllowed);
    }

    /** 可测化核心判断(参数注入,IT 直接覆盖三分支) */
    public void checkFiatCount(long fiatCount, boolean allowed) {
        if (fiatCount > 0 && !allowed) {
            throw new IllegalStateException(
                    "[Assets] FIAT assets detected but assets.fiat-allowed=false. " +
                    "资金自持需提前完成合规评估(支付牌照 / 备付金存管). " +
                    "评估通过后,设置 assets.fiat-allowed=true 启用.");
        }
    }

    /** 运行时检查: 创建 FIAT 资产需环境授权 */
    public void assertCreationAllowed(String assetType) {
        if ("FIAT".equals(assetType) && !fiatAllowed) {
            throw new AssetsException(AssetsException.FIAT_NOT_ALLOWED,
                    "FIAT 资产未启用: 资金自持需先完成合规评估(assets.fiat-allowed)");
        }
    }

    private long countFiat() {
        return assetMapper.selectCount(
                new LambdaQueryWrapper<UbmxAsset>().eq(UbmxAsset::getAssetType, "FIAT"));
    }
}
