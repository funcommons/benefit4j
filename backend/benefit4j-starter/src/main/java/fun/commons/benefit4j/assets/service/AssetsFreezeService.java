package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.dto.FreezeRequest;
import fun.commons.benefit4j.assets.dto.FreezeView;
import fun.commons.benefit4j.assets.dto.UnfreezeRequest;
import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.entity.UbmxFreeze;
import fun.commons.benefit4j.assets.entity.UbmxPosting;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxAccountMapper;
import fun.commons.benefit4j.assets.mapper.UbmxFreezeMapper;
import fun.commons.benefit4j.assets.mapper.UbmxPostingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 业务冻结(assets-design §2.5,O5 分桶 / O14 单源):
 *
 *   freeze:   balance→frozen 挪移 + ubmx_freeze(ACTIVE);freezeNo 幂等;
 *             只认正余额(不透支授信)
 *   unfreeze: RELEASE(回余额,部分用 used_amount 跟踪)/ CONSUME(提现成功,
 *             frozen 清零 balance 不回,出账腿 user→credit:{asset})
 *   O14:      account.frozen 是缓存聚合,主数据 = Σ(amount - used WHERE ACTIVE);
 *             checkConsistency 供 T+1 对账每日校验
 *
 * 并发: 所有金额操作先锁账户行(FOR UPDATE)串行化,天然防双释。
 */
@Slf4j
@Service
public class AssetsFreezeService {

    static final Set<String> REASONS = Set.of("WITHDRAW", "AFTER_SALE", "RISK", "PRE_CONSUME", "OTHER");

    private final UbmxFreezeMapper freezeMapper;
    private final UbmxAccountMapper accountMapper;
    private final UbmxPostingMapper postingMapper;
    private final AccountService accountService;
    private final TransactionTemplate txTemplate;
    private final RetryTemplate retry;

    public AssetsFreezeService(UbmxFreezeMapper freezeMapper,
                               UbmxAccountMapper accountMapper,
                               UbmxPostingMapper postingMapper,
                               AccountService accountService,
                               PlatformTransactionManager txManager) {
        this.freezeMapper = freezeMapper;
        this.accountMapper = accountMapper;
        this.postingMapper = postingMapper;
        this.accountService = accountService;
        this.txTemplate = new TransactionTemplate(txManager);
        this.retry = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(50, 2.0, 800)
                .retryOn(DeadlockLoserDataAccessException.class)
                .retryOn(CannotAcquireLockException.class)
                .build();
    }

    public FreezeView freeze(FreezeRequest req) {
        if (req.getTenantId() == null || req.getFreezeNo() == null || req.getFreezeNo().isBlank()
                || req.getAccountRef() == null || req.getAssetCode() == null
                || req.getAmount() == null || req.getAmount().signum() <= 0
                || req.getReason() == null || !REASONS.contains(req.getReason())) {
            throw new AssetsException(AssetsException.ASSET_INVALID,
                    "冻结参数非法(freezeNo/accountRef/assetCode/amount>0/reason∈" + REASONS + ")");
        }
        return retry.execute(ctx -> txTemplate.execute(status -> doFreeze(req)));
    }

    private FreezeView doFreeze(FreezeRequest req) {
        // 先锁账户(account_id NOT NULL,抢占行须带账户);重放时账户必已存在
        UbmxAccount resolved = accountService.resolveRef(req.getTenantId(), req.getAccountRef(), req.getAssetCode());
        List<UbmxAccount> locked = accountMapper.lockByIds(List.of(resolved.getId()));
        if (locked.isEmpty() || !"ACTIVE".equals(locked.get(0).getStatus())) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "冻结账户不可用");
        }
        UbmxAccount acc = locked.get(0);

        // freezeNo 幂等(uk tenant_id+freeze_no): 已有单据返回当前状态
        UbmxFreeze row = new UbmxFreeze();
        row.setId(IdWorker.getId());
        row.setTenantId(req.getTenantId());
        row.setAccountId(acc.getId());
        row.setFreezeNo(req.getFreezeNo());
        row.setReason(req.getReason());
        row.setAmount(req.getAmount());
        row.setUsedAmount(BigDecimal.ZERO);
        row.setStatus("ACTIVE");
        if (req.getExpireSeconds() != null && req.getExpireSeconds() > 0) {
            row.setExpireTime(OffsetDateTime.now().plusSeconds(req.getExpireSeconds()));
        }
        if (freezeMapper.insertIgnore(row) == 0) {
            return viewOf(loadByNo(req.getTenantId(), req.getFreezeNo()));
        }

        if (acc.getBalance().compareTo(req.getAmount()) < 0) {
            throw new AssetsException(AssetsException.INSUFFICIENT_BALANCE,
                    "可冻结余额不足: balance=" + acc.getBalance() + " amount=" + req.getAmount());
        }
        accountMapper.adjustBalanceAndFrozen(acc.getId(), req.getAmount().negate(), req.getAmount());

        // FREEZE 自环腿(审计)
        insertPosting(IdWorker.getId(), "FREEZE", req.getTenantId(), req.getFreezeNo(), req.getAssetCode(),
                acc, acc, req.getAmount(), acc.getBalance().subtract(req.getAmount()));
        return viewOf(row);
    }

    public FreezeView unfreeze(UnfreezeRequest req) {
        if (req.getTenantId() == null || req.getFreezeNo() == null
                || !"RELEASE".equals(req.getMode()) && !"CONSUME".equals(req.getMode())) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "解冻参数非法(mode∈RELEASE|CONSUME)");
        }
        if (req.getAmount() != null && req.getAmount().signum() <= 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "释放额必须 > 0");
        }
        return retry.execute(ctx -> txTemplate.execute(status -> doUnfreeze(req)));
    }

    private FreezeView doUnfreeze(UnfreezeRequest req) {
        UbmxFreeze row = loadByNo(req.getTenantId(), req.getFreezeNo());
        if (row == null) {
            throw new AssetsException(AssetsException.FREEZE_NOT_FOUND, "冻结单不存在: " + req.getFreezeNo());
        }
        if (!"ACTIVE".equals(row.getStatus())) {
            return viewOf(row);   // 终态幂等返回
        }

        List<UbmxAccount> locked = accountMapper.lockByIds(List.of(row.getAccountId()));
        if (locked.isEmpty()) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "冻结账户不存在");
        }
        UbmxAccount acc = locked.get(0);

        BigDecimal remaining = row.getAmount().subtract(row.getUsedAmount());
        BigDecimal release = req.getAmount() == null ? remaining : req.getAmount().min(remaining);

        // 账户侧挪回
        if ("RELEASE".equals(req.getMode())) {
            accountMapper.adjustBalanceAndFrozen(acc.getId(), release, release.negate());
            insertPosting(IdWorker.getId(), "UNFREEZE", req.getTenantId(), req.getFreezeNo(), acc.getAssetCode(),
                    acc, acc, release, acc.getBalance().add(release));
        } else {
            // CONSUME: 提现成功语义,frozen 清,balance 不回;出账腿 user→credit:{asset}(BOUNDARY)
            accountMapper.adjustBalanceAndFrozen(acc.getId(), BigDecimal.ZERO, release.negate());
            UbmxAccount credit = accountService.getOrCreateBoundaryAccount(
                    req.getTenantId(), "credit:" + acc.getAssetCode(), acc.getAssetCode());
            insertPosting(IdWorker.getId(), "CONSUME", req.getTenantId(), req.getFreezeNo(), acc.getAssetCode(),
                    acc, credit, release, acc.getBalance());
        }

        // 冻结单侧: used 累计,放尽转终态
        boolean exhausted = row.getUsedAmount().add(release).compareTo(row.getAmount()) >= 0;
        String nextStatus = exhausted
                ? ("RELEASE".equals(req.getMode()) ? "RELEASED" : "CONSUMED")
                : "ACTIVE";
        UbmxFreeze patch = new UbmxFreeze();
        patch.setId(row.getId());
        patch.setUsedAmount(row.getUsedAmount().add(release));
        patch.setStatus(nextStatus);
        freezeMapper.updateById(patch);
        row.setUsedAmount(patch.getUsedAmount());
        row.setStatus(nextStatus);
        return viewOf(row);
    }

    /** O14 一致性: account.frozen(缓存)== Σ(amount - used WHERE ACTIVE)(主数据) */
    public boolean checkConsistency(Long tenantId, Long accountId) {
        List<UbmxFreeze> actives = freezeMapper.selectList(new LambdaQueryWrapper<UbmxFreeze>()
                .eq(UbmxFreeze::getTenantId, tenantId)
                .eq(UbmxFreeze::getAccountId, accountId)
                .eq(UbmxFreeze::getStatus, "ACTIVE"));
        BigDecimal expected = actives.stream()
                .map(f -> f.getAmount().subtract(f.getUsedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        UbmxAccount acc = accountMapper.selectById(accountId);
        return acc != null && acc.getFrozen().compareTo(expected) == 0;
    }

    // ---------- helpers ----------

    private UbmxFreeze loadByNo(Long tenantId, String freezeNo) {
        return freezeMapper.selectOne(new LambdaQueryWrapper<UbmxFreeze>()
                .eq(UbmxFreeze::getTenantId, tenantId)
                .eq(UbmxFreeze::getFreezeNo, freezeNo));
    }

    private void insertPosting(Long txId, String txType, Long tenantId, String orderId, String assetCode,
                               UbmxAccount src, UbmxAccount dst, BigDecimal amount, BigDecimal balanceAfter) {
        UbmxPosting p = new UbmxPosting();
        p.setTenantId(tenantId);
        p.setTxId(txId);
        p.setTxType(txType);
        p.setExtOrderId(orderId);
        p.setLegSeq(0);
        p.setSrcAccountId(src.getId());
        p.setDstAccountId(dst.getId());
        p.setAssetCode(assetCode);
        p.setAmount(amount);
        p.setDirection("OUT");
        p.setBalanceAfter(balanceAfter);
        p.setStatus("SUCCESS");
        postingMapper.insert(p);
    }

    private FreezeView viewOf(UbmxFreeze row) {
        FreezeView v = new FreezeView();
        v.setId(row.getId());
        v.setFreezeNo(row.getFreezeNo());
        v.setReason(row.getReason());
        v.setStatus(row.getStatus());
        v.setAmount(row.getAmount());
        v.setUsedAmount(row.getUsedAmount());
        v.setExpireTime(row.getExpireTime());
        return v;
    }
}
