package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.dto.ExchangeRequest;
import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.dto.PostingResult;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 兑换(assets-design §3.2): 10 CNY → 1000 GOLD = 2 腿
 *   ① user:{uid}:FROM → exchange:FROM(FROM amount)
 *   ② exchange:TO → user:{uid}:TO(TO amount)
 * exchange:* 为 BOUNDARY 边界户(O11 豁免锁与记账);汇率由调用方定价,资产域只记账不定价。
 */
@Service
@RequiredArgsConstructor
public class AssetsExchangeService {

    private final AssetRegistryService registry;
    private final PostingService postingService;

    public PostingResult exchange(ExchangeRequest req) {
        validate(req);
        checkCapability(req.getFromAssetCode());
        checkCapability(req.getToAssetCode());

        PostingCommand cmd = new PostingCommand();
        cmd.setTenantId(req.getTenantId());
        cmd.setExtOrderId(req.getOrderId());
        cmd.setTxType("EXCHANGE");
        cmd.setLegs(List.of(
                leg(req.getOwnerRef(), "exchange:" + req.getFromAssetCode(),
                        req.getFromAssetCode(), req.getFromAmount()),
                leg("exchange:" + req.getToAssetCode(), req.getOwnerRef(),
                        req.getToAssetCode(), req.getToAmount())));
        return postingService.commitTx(cmd);
    }

    private void checkCapability(String assetCode) {
        UbmxAsset asset = registry.getActiveRequired(assetCode);
        if (!Boolean.TRUE.equals(asset.getCanExchange())) {
            throw new AssetsException(AssetsException.ASSET_PRIVILEGE_DENIED,
                    "资产未开放兑换: " + assetCode);
        }
    }

    private void validate(ExchangeRequest req) {
        if (req.getTenantId() == null || req.getOrderId() == null || req.getOrderId().isBlank()
                || req.getOwnerRef() == null
                || req.getFromAssetCode() == null || req.getToAssetCode() == null
                || req.getFromAssetCode().equals(req.getToAssetCode())
                || req.getFromAmount() == null || req.getFromAmount().signum() <= 0
                || req.getToAmount() == null || req.getToAmount().signum() <= 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID,
                    "兑换参数非法(ownerRef/from≠to/两侧金额>0)");
        }
    }

    private PostingCommand.LegSpec leg(String src, String dst, String asset, BigDecimal amount) {
        PostingCommand.LegSpec l = new PostingCommand.LegSpec();
        l.setSrc(src);
        l.setDst(dst);
        l.setAssetCode(asset);
        l.setAmount(amount);
        return l;
    }
}
