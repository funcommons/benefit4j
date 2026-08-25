package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.dto.PreConsumeRequest;
import fun.commons.benefit4j.assets.dto.PreConsumeView;
import fun.commons.benefit4j.assets.dto.SettleRequest;
import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.entity.UbmxPosting;
import fun.commons.benefit4j.assets.entity.UbmxPreConsume;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxAccountMapper;
import fun.commons.benefit4j.assets.mapper.UbmxPostingMapper;
import fun.commons.benefit4j.assets.mapper.UbmxPreConsumeMapper;
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
import java.util.Map;

/**
 * TCC 三阶段(assets-design §4.4 / §2.4):
 *
 *   pre-consume: balance-=est, frozen+=est(总资产不变);写 RESERVED 单 + FREEZE 自环腿
 *   settle:      frozen-=est;diff = est - actual → 回补/补扣;补扣不足 → PARTIAL_SETTLED(扣至授信下限,差额挂账待 P2 差错池)
 *   refund:      frozen-=est, balance+=est(全额回滚)
 *   expireOnce:  过期 RESERVED → EXPIRED(= refund 语义),scheduler 驱动
 *
 * O15 终态竞态 guard: 终态迁移 UPDATE 带 WHERE status='RESERVED',先抢到者赢,
 * 后到方按幂等语义返回当前终态,不二次变更。
 */
@Slf4j
@Service
public class PreConsumeService {

    static final int DEFAULT_EXPIRE_SECONDS = 1800;

    private final UbmxPreConsumeMapper preConsumeMapper;
    private final UbmxPostingMapper postingMapper;
    private final UbmxAccountMapper accountMapper;
    private final AccountService accountService;
    private final AssetsLimitGuard limitGuard;
    private final TransactionTemplate txTemplate;
    private final RetryTemplate retry;

    public PreConsumeService(UbmxPreConsumeMapper preConsumeMapper,
                             UbmxPostingMapper postingMapper,
                             UbmxAccountMapper accountMapper,
                             AccountService accountService,
                             AssetsLimitGuard limitGuard,
                             PlatformTransactionManager txManager) {
        this.preConsumeMapper = preConsumeMapper;
        this.postingMapper = postingMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.limitGuard = limitGuard;
        this.txTemplate = new TransactionTemplate(txManager);
        this.retry = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(50, 2.0, 800)
                .retryOn(DeadlockLoserDataAccessException.class)
                .retryOn(CannotAcquireLockException.class)
                .build();
    }

    // ---------- ① 预扣 ----------

    public PreConsumeView preConsume(PreConsumeRequest req) {
        if (req.getAppId() == null || req.getRequestId() == null || req.getRequestId().isBlank()
                || req.getAccountRef() == null || req.getAssetCode() == null
                || req.getEstimated() == null || req.getEstimated().signum() <= 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "预扣参数非法(appId/requestId/accountRef/assetCode/estimated>0)");
        }
        return retry.execute(ctx -> txTemplate.execute(status -> doPreConsume(req)));
    }

    private PreConsumeView doPreConsume(PreConsumeRequest req) {
        // 幂等抢占(requestId 贯穿三阶段): 已有单据直接返回当前状态,不重复扣
        UbmxPreConsume pc = new UbmxPreConsume();
        pc.setId(IdWorker.getId());
        pc.setAppId(req.getAppId());
        pc.setRequestId(req.getRequestId());
        pc.setTxId(IdWorker.getId());
        pc.setChargeMode("SOLO");
        pc.setAssetCode(req.getAssetCode());
        pc.setEstimated(req.getEstimated());
        int expireSeconds = req.getExpireSeconds() != null && req.getExpireSeconds() > 0
                ? req.getExpireSeconds() : DEFAULT_EXPIRE_SECONDS;
        pc.setExpireTime(OffsetDateTime.now().plusSeconds(expireSeconds));
        if (preConsumeMapper.insertIgnore(pc) == 0) {
            return viewOf(loadByRequest(req.getAppId(), req.getRequestId()));
        }
        pc.setStatus("RESERVED");   // SQL 字面量不回填,视图用

        // 锁定预扣账户(O12: FOR UPDATE)
        UbmxAccount acc = accountService.resolveRef(req.getAppId(), req.getAccountRef(), req.getAssetCode());
        List<UbmxAccount> locked = accountMapper.lockByIds(List.of(acc.getId()));
        if (locked.isEmpty() || !"ACTIVE".equals(locked.get(0).getStatus())) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "预扣账户不可用: " + req.getAccountRef());
        }
        acc = locked.get(0);
        if (acc.getBalance().add(acc.getCreditLimit()).compareTo(req.getEstimated()) < 0) {
            throw new AssetsException(AssetsException.INSUFFICIENT_BALANCE,
                    "预扣余额不足: balance=" + acc.getBalance() + " credit=" + acc.getCreditLimit()
                            + " estimated=" + req.getEstimated());
        }
        limitGuard.checkOut(acc, req.getAssetCode(), req.getEstimated());   // B4 单笔/累计限额
        accountMapper.adjustBalanceAndFrozen(acc.getId(), req.getEstimated().negate(), req.getEstimated());
        pc.setUserAccountId(acc.getId());

        // FREEZE 自环腿(审计;src=dst 同户,delta 自抵,不重复记账)
        insertPosting(pc.getTxId(), "FREEZE", req, acc, acc, req.getEstimated(),
                acc.getBalance().subtract(req.getEstimated()), 0, null);

        // 回填账户关联(单据已在库,update 关联列)
        UbmxPreConsume patch = new UbmxPreConsume();
        patch.setId(pc.getId());
        patch.setUserAccountId(acc.getId());
        preConsumeMapper.updateById(patch);
        return viewOf(pc);
    }

    // ---------- ①' DUAL 双账户预扣(§3.3) ----------

    public PreConsumeView preConsumeDual(fun.commons.benefit4j.assets.dto.PreConsumeDualRequest req) {
        if (req.getAppId() == null || req.getRequestId() == null || req.getRequestId().isBlank()
                || req.getUserAccountRef() == null || req.getTenantAccountRef() == null
                || req.getAssetCode() == null
                || req.getEstimated() == null || req.getEstimated().signum() <= 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID,
                    "双扣参数非法(appId/requestId/双侧账户引用/assetCode/estimated>0)");
        }
        return retry.execute(ctx -> txTemplate.execute(status -> {
            UbmxPreConsume pc = new UbmxPreConsume();
            pc.setId(IdWorker.getId());
            pc.setAppId(req.getAppId());
            pc.setRequestId(req.getRequestId());
            pc.setTxId(IdWorker.getId());
            pc.setChargeMode("DUAL");
            pc.setAssetCode(req.getAssetCode());
            pc.setEstimated(req.getEstimated());
            int expireSeconds = req.getExpireSeconds() != null && req.getExpireSeconds() > 0
                    ? req.getExpireSeconds() : DEFAULT_EXPIRE_SECONDS;
            pc.setExpireTime(OffsetDateTime.now().plusSeconds(expireSeconds));
            if (preConsumeMapper.insertIgnore(pc) == 0) {
                return viewOf(loadByRequest(req.getAppId(), req.getRequestId()));
            }
            pc.setStatus("RESERVED");

            UbmxAccount userAcc = accountService.resolveRef(req.getAppId(), req.getUserAccountRef(), req.getAssetCode());
            UbmxAccount tenantAcc = accountService.resolveRef(req.getAppId(), req.getTenantAccountRef(), req.getAssetCode());
            // 锁两账户(SQL 内 ORDER BY id 升序,防死锁);任一不足 → 整笔回滚
            List<UbmxAccount> locked = accountMapper.lockByIds(
                    List.of(userAcc.getId(), tenantAcc.getId()));
            if (locked.size() != 2 || locked.stream().anyMatch(a -> !"ACTIVE".equals(a.getStatus()))) {
                throw new AssetsException(AssetsException.ASSET_INVALID, "双扣账户不可用");
            }
            for (UbmxAccount acc : locked) {
                if (acc.getBalance().add(acc.getCreditLimit()).compareTo(req.getEstimated()) < 0) {
                    throw new AssetsException(AssetsException.INSUFFICIENT_BALANCE,
                            "双扣余额不足: accountId=" + acc.getId()
                                    + " balance=" + acc.getBalance() + " credit=" + acc.getCreditLimit()
                                    + " estimated=" + req.getEstimated());
                }
            }
            // B4 双侧限额 + 双侧同额挪移 balance→frozen(无中间户,§3.3 定案)
            limitGuard.checkOut(userAcc, req.getAssetCode(), req.getEstimated());
            limitGuard.checkOut(tenantAcc, req.getAssetCode(), req.getEstimated());
            accountMapper.adjustBalanceAndFrozen(userAcc.getId(), req.getEstimated().negate(), req.getEstimated());
            accountMapper.adjustBalanceAndFrozen(tenantAcc.getId(), req.getEstimated().negate(), req.getEstimated());
            insertPosting(pc.getTxId(), "FREEZE", buildReq(req.getAppId(), req.getRequestId(), req.getAssetCode()),
                    userAcc, userAcc, req.getEstimated(),
                    userAcc.getBalance().subtract(req.getEstimated()), 0, null);
            insertPosting(pc.getTxId(), "FREEZE", buildReq(req.getAppId(), req.getRequestId(), req.getAssetCode()),
                    tenantAcc, tenantAcc, req.getEstimated(),
                    tenantAcc.getBalance().subtract(req.getEstimated()), 1, null);

            UbmxPreConsume patch = new UbmxPreConsume();
            patch.setId(pc.getId());
            patch.setUserAccountId(userAcc.getId());
            patch.setTenantAccountId(tenantAcc.getId());
            preConsumeMapper.updateById(patch);
            return viewOf(pc);
        }));
    }

    // ---------- ② 结算 ----------

    public PreConsumeView settle(SettleRequest req) {
        if (req.getActual() == null || req.getActual().signum() <= 0) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "结算金额必须 > 0");
        }
        return retry.execute(ctx -> txTemplate.execute(status -> {
            UbmxPreConsume pc = loadByRequest(req.getAppId(), req.getRequestId());
            if (pc == null) {
                throw new AssetsException(AssetsException.PRE_CONSUME_NOT_FOUND,
                        "预扣单不存在: " + req.getRequestId());
            }
            if (!"RESERVED".equals(pc.getStatus())) {
                return viewOf(pc);   // 终态幂等返回
            }
            List<UbmxAccount> accounts = lockTargets(pc);   // SOLO=1 户 / DUAL=2 户

            BigDecimal est = pc.getEstimated();
            BigDecimal actual = req.getActual();
            BigDecimal diff = est.subtract(actual);   // >0 退回 / <0 补扣
            boolean partial = false;
            BigDecimal settled = actual;
            List<BigDecimal> deltas = new java.util.ArrayList<>();

            for (UbmxAccount acc : accounts) {
                BigDecimal balanceDelta;
                if (diff.signum() >= 0) {
                    balanceDelta = diff;
                } else {
                    BigDecimal need = diff.negate();
                    BigDecimal affordable = acc.getBalance().add(acc.getCreditLimit());
                    if (need.compareTo(affordable) <= 0) {
                        balanceDelta = need.negate();
                    } else {
                        // O15: 补扣不足 → 尽扣至授信下限,差额挂账(差错池),终态放行
                        balanceDelta = affordable.negate();
                        partial = true;
                        log.warn("PARTIAL_SETTLED 差额挂账: requestId={} accountId={} need={} affordable={}",
                                req.getRequestId(), acc.getId(), need, affordable);
                    }
                }
                accountMapper.adjustBalanceAndFrozen(acc.getId(), balanceDelta, est.negate());
                deltas.add(balanceDelta);
            }
            // SOLO 保持原语义: partial 时 settled = est + affordable(尽扣额)
            if (partial && accounts.size() == 1) {
                settled = est.add(accounts.get(0).getBalance().add(accounts.get(0).getCreditLimit()));
            }
            String nextStatus = partial ? "PARTIAL_SETTLED" : "SETTLED";
            guardToTerminal(pc, nextStatus, settled);

            // CONSUME 腿: 每账户一条,流向 fee 边界户(BOUNDARY 不记账)
            for (int i = 0; i < accounts.size(); i++) {
                UbmxAccount fee = accountService.getOrCreateBoundaryAccount(
                        pc.getAppId(), "fee:" + pc.getAssetCode(), pc.getAssetCode());
                insertPosting(IdWorker.getId(), "CONSUME",
                        buildReq(pc.getAppId(), pc.getRequestId(), pc.getAssetCode()),
                        accounts.get(i), fee, actual,
                        accounts.get(i).getBalance().add(deltas.get(i)), i, null);
            }

            pc.setStatus(nextStatus);
            pc.setSettledAmount(settled);
            return viewOf(pc);
        }));
    }

    // ---------- ③ 退款 / 过期 ----------

    public PreConsumeView refund(Long appId, String requestId) {
        return retry.execute(ctx -> txTemplate.execute(status -> {
            UbmxPreConsume pc = loadByRequest(appId, requestId);
            if (pc == null) {
                throw new AssetsException(AssetsException.PRE_CONSUME_NOT_FOUND, "预扣单不存在: " + requestId);
            }
            if (!"RESERVED".equals(pc.getStatus())) {
                return viewOf(pc);   // 终态幂等返回(含 EXPIRED)
            }
            return doRelease(pc, "REFUNDED");
        }));
    }

    /** scheduler 驱动: 过期 RESERVED → EXPIRED(refund 同语义) */
    public int expireOnce(int limit) {
        List<UbmxPreConsume> expired = preConsumeMapper.scanExpired(limit);
        int n = 0;
        for (UbmxPreConsume pc : expired) {
            try {
                PreConsumeView v = retry.execute(ctx -> txTemplate.execute(status -> {
                    UbmxPreConsume fresh = preConsumeMapper.selectById(pc.getId());
                    if (fresh == null || !"RESERVED".equals(fresh.getStatus())) {
                        return null;   // 已被并发 settle/refund
                    }
                    return doRelease(fresh, "EXPIRED");
                }));
                if (v != null) n++;
            } catch (Exception e) {
                log.error("过期回收失败: id={} requestId={}", pc.getId(), pc.getRequestId(), e);
            }
        }
        return n;
    }

    private PreConsumeView doRelease(UbmxPreConsume pc, String terminalStatus) {
        List<UbmxAccount> accounts = lockTargets(pc);
        BigDecimal est = pc.getEstimated();
        for (int i = 0; i < accounts.size(); i++) {
            UbmxAccount acc = accounts.get(i);
            accountMapper.adjustBalanceAndFrozen(acc.getId(), est, est.negate());
            insertPosting(IdWorker.getId(), "REFUND",
                    buildReq(pc.getAppId(), pc.getRequestId(), pc.getAssetCode()),
                    acc, acc, est, acc.getBalance().add(est), i,
                    "EXPIRED".equals(terminalStatus) ? Map.of("expired", true) : null);
        }
        guardToTerminal(pc, terminalStatus, null);
        pc.setStatus(terminalStatus);
        return viewOf(pc);
    }

    // ---------- helpers ----------

    private PreConsumeRequest buildReq(Long appId, String requestId, String assetCode) {
        PreConsumeRequest r = new PreConsumeRequest();
        r.setAppId(appId);
        r.setRequestId(requestId);
        r.setAssetCode(assetCode);
        return r;
    }

    private UbmxAccount lockedAccount(Long accountId) {
        List<UbmxAccount> locked = accountMapper.lockByIds(List.of(accountId));
        if (locked.isEmpty() || !"ACTIVE".equals(locked.get(0).getStatus())) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "账户不可用: " + accountId);
        }
        return locked.get(0);
    }

    /** SOLO=1 户 / DUAL=2 户;锁定按 id 升序(防死锁),返回按 user→tenant 语义序(与腿序一致) */
    private List<UbmxAccount> lockTargets(UbmxPreConsume pc) {
        List<Long> ids = "DUAL".equals(pc.getChargeMode())
                ? List.of(pc.getUserAccountId(), pc.getTenantAccountId())
                : List.of(pc.getUserAccountId());
        List<UbmxAccount> locked = accountMapper.lockByIds(ids);
        if (locked.size() != ids.size()
                || locked.stream().anyMatch(a -> !"ACTIVE".equals(a.getStatus()))) {
            throw new AssetsException(AssetsException.ASSET_INVALID,
                    "预扣账户不可用: requestId=" + pc.getRequestId());
        }
        java.util.Map<Long, UbmxAccount> byId = new java.util.HashMap<>();
        locked.forEach(a -> byId.put(a.getId(), a));
        return ids.stream().map(byId::get).toList();
    }

    /** O15 guard: RESERVED → 终态单向迁移,rows==0 = 并发已被迁移(由外层重读返回) */
    private void guardToTerminal(UbmxPreConsume pc, String status, BigDecimal settled) {
        if (preConsumeMapper.casToTerminal(pc.getId(), status, settled) == 0) {
            throw new AssetsException(AssetsException.IDEMPOTENCY_CONFLICT,
                    "预扣单已被并发迁移: " + pc.getRequestId());
        }
    }

    private void insertPosting(Long txId, String txType, PreConsumeRequest reqMeta,
                               UbmxAccount src, UbmxAccount dst, BigDecimal amount, BigDecimal balanceAfter,
                               int legSeq, Map<String, Object> ext) {
        UbmxPosting p = new UbmxPosting();
        p.setAppId(reqMeta.getAppId());
        p.setTxId(txId);
        p.setTxType(txType);
        p.setExtOrderId(reqMeta.getRequestId());
        p.setLegSeq(legSeq);
        p.setSrcAccountId(src.getId());
        p.setDstAccountId(dst.getId());
        p.setAssetCode(reqMeta.getAssetCode());
        p.setAmount(amount);
        p.setDirection("OUT");
        p.setBalanceAfter(balanceAfter);
        p.setStatus("SUCCESS");
        p.setExt(ext);
        postingMapper.insert(p);
    }

    private UbmxPreConsume loadByRequest(Long appId, String requestId) {
        return preConsumeMapper.selectOne(new LambdaQueryWrapper<UbmxPreConsume>()
                .eq(UbmxPreConsume::getAppId, appId)
                .eq(UbmxPreConsume::getRequestId, requestId));
    }

    private PreConsumeView viewOf(UbmxPreConsume pc) {
        PreConsumeView v = new PreConsumeView();
        v.setId(pc.getId());
        v.setRequestId(pc.getRequestId());
        v.setStatus(pc.getStatus());
        v.setEstimated(pc.getEstimated());
        v.setSettledAmount(pc.getSettledAmount());
        v.setExpireTime(pc.getExpireTime());
        return v;
    }
}
