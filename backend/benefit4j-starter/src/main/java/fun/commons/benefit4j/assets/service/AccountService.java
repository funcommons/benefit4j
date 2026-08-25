package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 账户服务: lazy 开户 + 查询(assets-design §2.2)。
 * 边界户(O11): account_type='BOUNDARY',不参与 FOR UPDATE、不更新 balance;
 * owner_type 统一 PLATFORM,owner_id 用固定编码表区分边界种类。
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    /** 边界户 owner_id 编码表(与唯一键共同保证 (app × kind × asset) 唯一) */
    static long boundaryOwnerIdOf(String ref) {
        switch (ref) {
            case "issue": return 1L;
            case "fee": return 2L;
            case "exchange": return 3L;
            case "credit": return 4L;
            case "world:wechat": return 101L;
            case "world:alipay": return 102L;
            case "world:union": return 103L;
            default:
                throw new AssetsException(AssetsException.ASSET_INVALID, "未知的边界户引用: " + ref);
        }
    }

    private final UbmxAccountMapper accountMapper;
    private final AssetRegistryService registry;

    /** 主体户 lazy 开户: 不存在则建(单语句幂等,并发下撞唯一键重读) */
    @Transactional
    public UbmxAccount getOrCreateAccount(Long appId, String ownerType, Long ownerId, String assetCode) {
        registry.getActiveRequired(assetCode);
        UbmxAccount existing = selectByUniqueKey(appId, ownerType, ownerId, assetCode);
        if (existing != null) return existing;

        UbmxAccount acc = baseAccount(appId, ownerType, ownerId, assetCode);
        acc.setAccountType("NORMAL");
        acc.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        // PG: 事务内撞唯一键会 abort 整个事务,必须 ON CONFLICT DO NOTHING 判行数
        if (accountMapper.insertIgnore(acc, null) == 0) {
            return selectByUniqueKey(appId, ownerType, ownerId, assetCode);
        }
        return acc;
    }

    /** 边界户 lazy 开户: issue:{asset} / fee:{asset} / world:{channel} 等 */
    @Transactional
    public UbmxAccount getOrCreateBoundaryAccount(Long appId, String boundaryRef, String assetCode) {
        registry.getActiveRequired(assetCode);
        String kind = boundaryRef.startsWith("world:") ? boundaryRef : boundaryRef.split(":")[0];
        long ownerId = boundaryOwnerIdOf(boundaryRef.contains(":") && kind.equals(boundaryRef)
                ? boundaryRef : kind);
        UbmxAccount existing = selectByUniqueKey(appId, "PLATFORM", ownerId, assetCode);
        if (existing != null) return existing;

        UbmxAccount acc = baseAccount(appId, "PLATFORM", ownerId, assetCode);
        acc.setAccountType("BOUNDARY");
        acc.setExt(Map.of("boundary", kind));
        acc.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        String extJson = "{\"boundary\":\"" + kind + "\"}";
        if (accountMapper.insertIgnore(acc, extJson) == 0) {
            return selectByUniqueKey(appId, "PLATFORM", ownerId, assetCode);
        }
        return acc;
    }

    public UbmxAccount getAccount(Long id) {
        UbmxAccount acc = accountMapper.selectById(id);
        if (acc == null) {
            throw new AssetsException(AssetsException.ASSET_NOT_FOUND, "账户不存在: " + id);
        }
        return acc;
    }

    /** 授信额度调整(F1,OPS 调额入口,Step5 接双签审计) */
    public void updateCreditLimit(Long appId, Long accountId, BigDecimal creditLimit) {
        if (creditLimit == null || creditLimit.signum() < 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "授信额度不可为负");
        }
        UbmxAccount acc = getAccount(accountId);
        acc.setCreditLimit(creditLimit);
        accountMapper.updateById(acc);
    }

    /** 只读解析(查询 API 用): 不 lazy 开户,不存在返回 null,杜绝查询写副作用 */
    public UbmxAccount findRef(Long appId, String ref, String assetCode) {
        int colon = ref.indexOf(':');
        if (colon < 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "非法账户引用: " + ref);
        }
        String head = ref.substring(0, colon);
        long ownerId;
        try {
            ownerId = Long.parseLong(ref.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "非法账户引用: " + ref);
        }
        if ("issue".equals(head) || "fee".equals(head) || "exchange".equals(head)
                || "credit".equals(head) || "world".equals(head)) {
            String kind = head.equals("world") ? ref : head;
            return selectByUniqueKey(appId, "PLATFORM", boundaryOwnerIdOf(kind), assetCode);
        }
        return selectByUniqueKey(appId, head.toUpperCase(), ownerId, assetCode);
    }

    /** 引擎解析腿时调用;独立调用(非事务)自开事务 */
    public UbmxAccount resolveRef(Long appId, String ref, String assetCode) {
        int colon = ref.indexOf(':');
        if (colon < 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "非法账户引用: " + ref);
        }
        String head = ref.substring(0, colon);
        switch (head) {
            case "user":
            case "tenant":
            case "merchant":
            case "platform":
            case "external": {
                long ownerId;
                try {
                    ownerId = Long.parseLong(ref.substring(colon + 1));
                } catch (NumberFormatException e) {
                    throw new AssetsException(AssetsException.ASSET_INVALID, "非法账户引用: " + ref);
                }
                return getOrCreateAccount(appId, head.toUpperCase(), ownerId, assetCode);
            }
            case "issue":
            case "fee":
            case "exchange":
            case "credit":
                return getOrCreateBoundaryAccount(appId, ref, assetCode);
            case "world":
                return getOrCreateBoundaryAccount(appId, ref, assetCode);
            default:
                throw new AssetsException(AssetsException.ASSET_INVALID, "非法账户引用: " + ref);
        }
    }

    private UbmxAccount selectByUniqueKey(Long appId, String ownerType, Long ownerId, String assetCode) {
        return accountMapper.selectOne(new LambdaQueryWrapper<UbmxAccount>()
                .eq(UbmxAccount::getAppId, appId)
                .eq(UbmxAccount::getOwnerType, ownerType)
                .eq(UbmxAccount::getOwnerId, ownerId)
                .eq(UbmxAccount::getAssetCode, assetCode));
    }

    private UbmxAccount baseAccount(Long appId, String ownerType, Long ownerId, String assetCode) {
        UbmxAccount acc = new UbmxAccount();
        acc.setAppId(appId);
        acc.setOwnerType(ownerType);
        acc.setOwnerId(ownerId);
        acc.setAssetCode(assetCode);
        acc.setBalance(BigDecimal.ZERO);
        acc.setCreditLimit(BigDecimal.ZERO);
        acc.setFrozen(BigDecimal.ZERO);
        acc.setVersion(0);
        acc.setStatus("ACTIVE");
        return acc;
    }
}
