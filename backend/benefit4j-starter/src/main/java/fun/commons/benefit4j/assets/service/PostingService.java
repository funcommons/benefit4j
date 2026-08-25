package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.dto.PostingResult;
import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.entity.UbmxPosting;
import fun.commons.benefit4j.assets.entity.UbmxTxOrder;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxAccountMapper;
import fun.commons.benefit4j.assets.mapper.UbmxPostingMapper;
import fun.commons.benefit4j.assets.mapper.UbmxTxOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 复式记账引擎(assets-design §3.1,O10/O11/O12 定案版):
 *
 *   TransactionTemplate(单事务)
 *     0. O10 幂等抢占: INSERT ubmx_tx_order;冲突→同参回放快照 / 异参抛冲突
 *     1. 腿解析: 账户引用 lazy 开户,识别 BOUNDARY 边界户
 *     2. O12 行锁: 仅 NORMAL 户,按 id 升序 FOR UPDATE(防死锁);边界户不锁
 *     3. 校验: Σ腿变动后 balance + credit_limit >= 0(NORMAL 户),FROZEN/CLOSED 拒绝
 *     4. 执行: 仅 NORMAL 户 balance±amount + version+1(纯审计,O12);逐腿写 posting(SUCCESS)
 *     5. 快照回填 result_snapshot(供幂等重放原样返回)
 *
 * 失败 = 整笔回滚(含 tx_order 占位,幂等键不残留,调用方可安全重试)。
 * RetryTemplate 只兜死锁 victim 与锁超便(O12:version 不参与条件,无乐观冲突可重试)。
 */
@Slf4j
@Service
public class PostingService {

    private static final com.fasterxml.jackson.databind.ObjectMapper OUTBOX_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final UbmxTxOrderMapper txOrderMapper;
    private final UbmxPostingMapper postingMapper;
    private final UbmxAccountMapper accountMapper;
    private final AccountService accountService;
    private final AssetsLimitGuard limitGuard;
    private final TransactionTemplate txTemplate;
    private final RetryTemplate retry;

    public PostingService(UbmxTxOrderMapper txOrderMapper,
                          UbmxPostingMapper postingMapper,
                          UbmxAccountMapper accountMapper,
                          AccountService accountService,
                          AssetsLimitGuard limitGuard,
                          PlatformTransactionManager txManager) {
        this.txOrderMapper = txOrderMapper;
        this.postingMapper = postingMapper;
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.limitGuard = limitGuard;
        this.txTemplate = new TransactionTemplate(txManager);
        // O2/O12: 指数退避 50ms→200ms→800ms,仅可安全重试的两类异常
        this.retry = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(50, 2.0, 800)
                .retryOn(DeadlockLoserDataAccessException.class)
                .retryOn(CannotAcquireLockException.class)
                .build();
    }

    public PostingResult commitTx(PostingCommand cmd) {
        validate(cmd);
        return retry.execute(ctx -> txTemplate.execute(status -> doCommitTx(cmd)));
    }

    // ---------- 引擎主体 ----------

    private PostingResult doCommitTx(PostingCommand cmd) {
        // 0. 幂等抢占(O10)
        String fingerprint = fingerprint(cmd);
        Long txId = IdWorker.getId();
        UbmxTxOrder gate = new UbmxTxOrder();
        gate.setId(IdWorker.getId());
        gate.setAppId(cmd.getAppId());
        gate.setExtOrderId(cmd.getExtOrderId());
        gate.setTxType(cmd.getTxType());
        gate.setTxId(txId);
        gate.setStatus("SUCCESS");
        // PG: 事务内撞唯一键会 abort 事务,ON CONFLICT DO NOTHING 判行数(0=已占用)
        if (txOrderMapper.insertIgnore(gate) == 0) {
            return replayOrConflict(cmd, fingerprint);
        }

        // 1. 腿解析(lazy 开户,识别边界户)
        List<PostingCommand.LegSpec> specs = cmd.getLegs();
        int n = specs.size();
        UbmxAccount[] src = new UbmxAccount[n];
        UbmxAccount[] dst = new UbmxAccount[n];
        Map<Long, UbmxAccount> normalAccounts = new TreeMap<>();  // id 升序天然
        Map<Long, BigDecimal> delta = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            PostingCommand.LegSpec leg = specs.get(i);
            src[i] = accountService.resolveRef(cmd.getAppId(), leg.getSrc(), leg.getAssetCode());
            dst[i] = accountService.resolveRef(cmd.getAppId(), leg.getDst(), leg.getAssetCode());
            // 资产恒等: 腿两侧账户必须是同一资产(引擎兜底校验)
            if (!leg.getAssetCode().equals(src[i].getAssetCode())
                    || !leg.getAssetCode().equals(dst[i].getAssetCode())) {
                throw new AssetsException(AssetsException.ASSET_INVALID,
                        "腿[" + i + "]资产恒等校验失败");
            }
            if (!"BOUNDARY".equals(src[i].getAccountType())) {
                normalAccounts.put(src[i].getId(), src[i]);
                delta.merge(src[i].getId(), leg.getAmount().negate(), BigDecimal::add);
            }
            if (!"BOUNDARY".equals(dst[i].getAccountType())) {
                normalAccounts.put(dst[i].getId(), dst[i]);
                delta.merge(dst[i].getId(), leg.getAmount(), BigDecimal::add);
            }
        }

        // 2. 行锁: 仅 NORMAL,按 id 升序(O11/O12)
        if (!normalAccounts.isEmpty()) {
            List<UbmxAccount> locked = accountMapper.lockByIds(normalAccounts.keySet());
            for (UbmxAccount acc : locked) {
                if (!"ACTIVE".equals(acc.getStatus())) {
                    throw new AssetsException(AssetsException.ASSET_INVALID,
                            "账户状态不可用: id=" + acc.getId() + " status=" + acc.getStatus());
                }
                normalAccounts.put(acc.getId(), acc);  // 锁定行覆盖(latest balance)
            }
        }

        // 3. 校验: 变动后余额不穿透授信下限(DB CHECK 同语义,应用层先拦给出明确错误码)
        for (var entry : delta.entrySet()) {
            UbmxAccount acc = normalAccounts.get(entry.getKey());
            BigDecimal after = acc.getBalance().add(entry.getValue());
            if (after.add(acc.getCreditLimit()).signum() < 0) {
                throw new AssetsException(AssetsException.INSUFFICIENT_BALANCE,
                        "余额不足: accountId=" + acc.getId() + " balance=" + acc.getBalance()
                                + " credit=" + acc.getCreditLimit()
                                + " delta=" + entry.getValue());
            }
        }

        // 3.5 B4 限额校验(腿级: src 出账 / dst 入账;BOUNDARY 跳过;未配 limit_policy 不拦)
        for (int i = 0; i < n; i++) {
            limitGuard.checkLeg(src[i], dst[i], specs.get(i).getAssetCode(), specs.get(i).getAmount());
        }

        // 4. 执行: 更新 NORMAL 户 + 逐腿写 posting(running 记 balance_after)
        Map<Long, BigDecimal> running = new LinkedHashMap<>();
        normalAccounts.forEach((id, acc) -> running.put(id, acc.getBalance()));

        List<PostingResult.LegView> views = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            PostingCommand.LegSpec leg = specs.get(i);
            BigDecimal after = null;
            if (!"BOUNDARY".equals(src[i].getAccountType())) {
                accountMapper.adjustBalance(src[i].getId(), leg.getAmount().negate());
                after = running.merge(src[i].getId(), leg.getAmount().negate(), BigDecimal::add);
            }
            if (!"BOUNDARY".equals(dst[i].getAccountType())) {
                accountMapper.adjustBalance(dst[i].getId(), leg.getAmount());
                after = running.merge(dst[i].getId(), leg.getAmount(), BigDecimal::add);
            }

            UbmxPosting p = new UbmxPosting();
            p.setAppId(cmd.getAppId());
            p.setTxId(txId);
            p.setTxType(cmd.getTxType());
            p.setExtOrderId(cmd.getExtOrderId());
            p.setLegSeq(i);
            p.setSrcAccountId(src[i].getId());
            p.setDstAccountId(dst[i].getId());
            p.setAssetCode(leg.getAssetCode());
            p.setAmount(leg.getAmount());
            p.setDirection("OUT");  // src 视角
            p.setBalanceAfter(after);
            p.setStatus("SUCCESS");
            p.setExt(cmd.getExt());
            postingMapper.insert(p);

            PostingResult.LegView v = new PostingResult.LegView();
            v.setLegSeq(i);
            v.setSrcAccountId(src[i].getId());
            v.setDstAccountId(dst[i].getId());
            v.setAssetCode(leg.getAssetCode());
            v.setAmount(leg.getAmount());
            v.setBalanceAfter(after);
            views.add(v);
        }

        // 5. 快照回填(O10: request 指纹 + response 原样),直接复用抢占行 id
        PostingResult result = new PostingResult();
        result.setTxId(txId);
        result.setTxType(cmd.getTxType());
        result.setLegs(views);
        fillSnapshot(gate.getId(), fingerprint, result);
        writeOutbox(cmd, result);
        return result;
    }

    /** B6: 事务内写 outbox 事件(复用 ubmp_outbox,消费端按 txId 幂等) */
    private void writeOutbox(PostingCommand cmd, PostingResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("txId", result.getTxId());
        payload.put("txType", result.getTxType());
        payload.put("extOrderId", cmd.getExtOrderId());
        List<Map<String, Object>> legs = new ArrayList<>();
        for (PostingResult.LegView v : result.getLegs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("legSeq", v.getLegSeq());
            m.put("assetCode", v.getAssetCode());
            m.put("amount", v.getAmount().toPlainString());
            m.put("srcAccountId", v.getSrcAccountId());
            m.put("dstAccountId", v.getDstAccountId());
            legs.add(m);
        }
        payload.put("legs", legs);
        try {
            String json = OUTBOX_JSON.writeValueAsString(payload);
            postingMapper.insertOutbox(IdWorker.getId(), result.getTxId(), json);
        } catch (Exception e) {
            log.error("[assets][outbox] 事件序列化失败(不阻断记账): txId={}", result.getTxId(), e);
        }
    }

    // ---------- 幂等回放(O10) ----------

    @SuppressWarnings("unchecked")
    private PostingResult replayOrConflict(PostingCommand cmd, String fingerprint) {
        UbmxTxOrder old = txOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UbmxTxOrder>()
                        .eq(UbmxTxOrder::getAppId, cmd.getAppId())
                        .eq(UbmxTxOrder::getExtOrderId, cmd.getExtOrderId()));
        if (old == null) {
            // 并发窗口极小: 唯一键冲突但行不可见,按冲突处理
            throw new AssetsException(AssetsException.IDEMPOTENCY_CONFLICT,
                    "幂等键冲突: " + cmd.getExtOrderId());
        }
        if ("FAILED".equals(old.getStatus())) {
            // O6: 已释放键永久隔离,同号禁复用(防双记账);新单必须换号
            throw new AssetsException(AssetsException.IDEMPOTENCY_CONFLICT,
                    "幂等键已被 OPS 释放隔离,禁止复用,请更换订单号: " + cmd.getExtOrderId());
        }
        Object snapshot = old.getResultSnapshot();
        if (snapshot instanceof Map<?, ?> map
                && Objects.equals(fingerprint, ((Map<String, Object>) map).get("request"))) {
            return buildResultFromSnapshot((Map<String, Object>) map);
        }
        throw new AssetsException(AssetsException.IDEMPOTENCY_CONFLICT,
                "幂等键已被占用且参数不同(防挪用): " + cmd.getExtOrderId());
    }

    @SuppressWarnings("unchecked")
    private PostingResult buildResultFromSnapshot(Map<String, Object> snapshot) {
        Map<String, Object> response = (Map<String, Object>) snapshot.get("response");
        PostingResult r = new PostingResult();
        r.setTxId(((Number) response.get("txId")).longValue());
        r.setTxType((String) response.get("txType"));
        List<PostingResult.LegView> legs = new ArrayList<>();
        Object rawLegs = response.get("legs");
        if (rawLegs instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> m = (Map<String, Object>) o;
                PostingResult.LegView v = new PostingResult.LegView();
                v.setLegSeq(((Number) m.get("legSeq")).intValue());
                v.setSrcAccountId(((Number) m.get("srcAccountId")).longValue());
                v.setDstAccountId(((Number) m.get("dstAccountId")).longValue());
                v.setAssetCode((String) m.get("assetCode"));
                v.setAmount(new BigDecimal(String.valueOf(m.get("amount"))));
                Object after = m.get("balanceAfter");
                v.setBalanceAfter(after == null ? null : new BigDecimal(String.valueOf(after)));
                legs.add(v);
            }
        }
        r.setLegs(legs);
        return r;
    }

    private void fillSnapshot(Long gateRowId, String fingerprint, PostingResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("txId", result.getTxId());
        response.put("txType", result.getTxType());
        List<Map<String, Object>> legs = new ArrayList<>();
        for (PostingResult.LegView v : result.getLegs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("legSeq", v.getLegSeq());
            m.put("srcAccountId", v.getSrcAccountId());
            m.put("dstAccountId", v.getDstAccountId());
            m.put("assetCode", v.getAssetCode());
            m.put("amount", v.getAmount().toPlainString());
            m.put("balanceAfter", v.getBalanceAfter() == null ? null : v.getBalanceAfter().toPlainString());
            legs.add(m);
        }
        response.put("legs", legs);

        UbmxTxOrder patch = new UbmxTxOrder();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("request", fingerprint);
        snapshot.put("response", response);
        patch.setId(gateRowId);
        patch.setResultSnapshot(snapshot);
        txOrderMapper.updateById(patch);
    }

    // ---------- 校验与指纹 ----------

    private void validate(PostingCommand cmd) {
        if (cmd.getAppId() == null || cmd.getExtOrderId() == null || cmd.getExtOrderId().isBlank()) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "appId/extOrderId 必填");
        }
        if (cmd.getTxType() == null || cmd.getTxType().isBlank()) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "txType 必填");
        }
        if (cmd.getLegs() == null || cmd.getLegs().isEmpty()) {
            throw new AssetsException(AssetsException.ASSET_INVALID, "legs 不能为空");
        }
        for (PostingCommand.LegSpec leg : cmd.getLegs()) {
            if (leg.getSrc() == null || leg.getDst() == null || leg.getAssetCode() == null
                    || leg.getAmount() == null || leg.getAmount().signum() <= 0) {
                throw new AssetsException(AssetsException.ASSET_INVALID,
                        "腿参数非法(引用/资产/金额必填且金额>0)");
            }
        }
    }

    /** 规范化请求指纹: 同键异参判据(Stripe request fingerprint 思路) */
    private String fingerprint(PostingCommand cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append(cmd.getAppId()).append('|')
                .append(cmd.getTxType()).append('|');
        for (PostingCommand.LegSpec leg : cmd.getLegs()) {
            sb.append(leg.getSrc()).append('>')
                    .append(leg.getDst()).append(':')
                    .append(leg.getAssetCode()).append(':')
                    .append(leg.getAmount().stripTrailingZeros().toPlainString()).append(';');
        }
        return sb.toString();
    }
}
