package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.assets.dto.PostIssueRequest;
import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.dto.PreConsumeRequest;
import fun.commons.benefit4j.assets.dto.RefundRequest;
import fun.commons.benefit4j.assets.dto.SettleRequest;
import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.AssetsQueryService;
import fun.commons.benefit4j.assets.service.PostingService;
import fun.commons.benefit4j.assets.service.PreConsumeService;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.ratelimit.annotation.RateLimit;
import fun.commons.framework4j.signature.annotation.RequiresSignature;
import fun.commons.framework4j.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * assets 域 runtime API(assets-design §4.2/§4.3/§4.4/§4.5):
 * 对外记账入口(issue/三阶段)+ 账户/流水查询。
 * 安全: tenantId 一律取 TokenContext,body 携带的 tenantId 被忽略(多租户隔离不可伪造)。
 */
@RestController
@RequestMapping("/benefit/api/v1/assets")
@RequiredArgsConstructor
@RequiresToken(value = "APP", type = "access")
@RequiresSignature
public class BenefitAssetsRuntimeController {

    /** 租户域强校验(§5.3/§6.2 L1): 真实租户身份(tenant_id>0)才可操作/记账;平台身份(0)拒绝 */
    @org.springframework.web.bind.annotation.ModelAttribute
    void requireTenantIdentity() {
        fun.commons.benefit4j.security.TenantIdentityGuard.requireTenant();
    }


    private final PostingService postingService;
    private final PreConsumeService preConsumeService;
    private final AccountService accountService;
    private final AssetsQueryService queryService;
    private final fun.commons.benefit4j.assets.service.AssetsExchangeService exchangeService;
    private final fun.commons.benefit4j.assets.service.AssetsFreezeService freezeService;

    /** 入账/通用记账(钱包中台 issue 模板 / 运营发奖,§4.3.1) */
    @PostMapping("/runtime/issue")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_ISSUE", targetType = "tx_order", targetIdSpel = "#req.extOrderId")
    public ApiResponse<Map<String, Object>> postIssue(@Valid @RequestBody PostIssueRequest req) {
        PostingCommand cmd = new PostingCommand();
        cmd.setTenantId(tenantId());
        cmd.setExtOrderId(req.getExtOrderId());
        cmd.setTxType(req.getTxType() == null ? "ISSUE" : req.getTxType());
        cmd.setExt(req.getExt());
        cmd.setLegs(req.getLegs().stream().map(in -> {
            PostingCommand.LegSpec l = new PostingCommand.LegSpec();
            l.setSrc(in.getSrc());
            l.setDst(in.getDst());
            l.setAssetCode(in.getAssetCode());
            l.setAmount(in.getAmount());
            return l;
        }).toList());
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("txId", postingService.commitTx(cmd).getTxId());
        data.put("txType", cmd.getTxType());
        return ApiResponse.success(data);
    }

    /** 预扣(§4.4) */
    @PostMapping("/runtime/pre-consume")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_PRECONSUME", targetType = "pre_consume", targetIdSpel = "#req.requestId")
    public ApiResponse<Object> postPreConsume(@Valid @RequestBody PreConsumeRequest req) {
        req.setTenantId(tenantId());   // token 覆盖 body
        return ApiResponse.success(preConsumeService.preConsume(req));
    }

    /** DUAL 双账户原子预扣(§3.3: user+tenant 同额,接 MMagiX 场景 2/3) */
    @PostMapping("/runtime/pre-consume:dual")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_PRECONSUME_DUAL", targetType = "pre_consume", targetIdSpel = "#req.requestId")
    public ApiResponse<Object> postPreConsumeDual(
            @Valid @RequestBody fun.commons.benefit4j.assets.dto.PreConsumeDualRequest req) {
        req.setTenantId(tenantId());
        return ApiResponse.success(preConsumeService.preConsumeDual(req));
    }

    /** 业务冻结(§4.3: 钱包中台提现申请/售后/风控,reason 分桶) */
    @PostMapping("/runtime/freeze")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_FREEZE", targetType = "freeze", targetIdSpel = "#req.freezeNo")
    public ApiResponse<Object> postFreeze(
            @Valid @RequestBody fun.commons.benefit4j.assets.dto.FreezeRequest req) {
        req.setTenantId(tenantId());
        return ApiResponse.success(freezeService.freeze(req));
    }

    /** 解冻(§4.3: RELEASE 回余额 / CONSUME 提现成功出账;支持部分释放) */
    @PostMapping("/runtime/unfreeze")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_UNFREEZE", targetType = "freeze", targetIdSpel = "#req.freezeNo")
    public ApiResponse<Object> postUnfreeze(
            @Valid @RequestBody fun.commons.benefit4j.assets.dto.UnfreezeRequest req) {
        req.setTenantId(tenantId());
        return ApiResponse.success(freezeService.unfreeze(req));
    }

    /** 兑换(§3.2: 双腿原子,汇率由调用方定价) */
    @PostMapping("/runtime/exchange")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_EXCHANGE", targetType = "tx_order", targetIdSpel = "#req.orderId")
    public ApiResponse<Object> postExchange(
            @Valid @RequestBody fun.commons.benefit4j.assets.dto.ExchangeRequest req) {
        req.setTenantId(tenantId());
        return ApiResponse.success(exchangeService.exchange(req));
    }

    /** 还款(F1: credit:{asset} → user 授信释放,负余额回正;credit 为 BOUNDARY 不记账) */
    @PostMapping("/runtime/repay")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_REPAY", targetType = "tx_order", targetIdSpel = "#req.requestId")
    public ApiResponse<Map<String, Object>> postRepay(
            @Valid @RequestBody fun.commons.benefit4j.assets.dto.RepayRequest req) {
        PostingCommand cmd = new PostingCommand();
        cmd.setTenantId(tenantId());
        cmd.setExtOrderId(req.getRequestId());
        cmd.setTxType("REPAY");
        PostingCommand.LegSpec l = new PostingCommand.LegSpec();
        l.setSrc("credit:" + req.getAssetCode());
        l.setDst(req.getAccountRef());
        l.setAssetCode(req.getAssetCode());
        l.setAmount(req.getAmount());
        cmd.setLegs(List.of(l));
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("txId", postingService.commitTx(cmd).getTxId());
        return ApiResponse.success(data);
    }

    /** 结算(confirm 实际额,diff 自动回补/补扣) */
    @PostMapping("/runtime/settle")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_SETTLE", targetType = "pre_consume", targetIdSpel = "#req.requestId")
    public ApiResponse<Object> postSettle(@Valid @RequestBody SettleRequest req) {
        req.setTenantId(tenantId());
        return ApiResponse.success(preConsumeService.settle(req));
    }

    /** 退款(全额释放预扣) */
    @PostMapping("/runtime/refund")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    @Auditable(action = "ASSETS_REFUND", targetType = "pre_consume", targetIdSpel = "#req.requestId")
    public ApiResponse<Object> postRefund(@Valid @RequestBody RefundRequest req) {
        return ApiResponse.success(preConsumeService.refund(tenantId(), req.getRequestId()));
    }

    /** 主体名下所有资产账户(§4.2) */
    @GetMapping("/accounts")
    @RateLimit(limit = 300, window = "1m", scope = "APP")
    public ApiResponse<List<UbmxAccount>> getAccounts(
            @RequestParam("owner_type") String ownerType,
            @RequestParam("owner_id") Long ownerId) {
        return ApiResponse.success(queryService.listAccounts(tenantId(), ownerType, ownerId, null));
    }

    /** 账户流水分页(§4.5,按账户引用 + 资产) */
    @GetMapping("/postings")
    @RateLimit(limit = 300, window = "1m", scope = "APP")
    public ApiResponse<List<Map<String, Object>>> getPostings(
            @RequestParam("account_ref") String accountRef,
            @RequestParam("asset_code") String assetCode,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        UbmxAccount acc = accountService.findRef(tenantId(), accountRef, assetCode);
        if (acc == null) {
            return ApiResponse.success(List.of());   // 只读解析: 不存在不开户,直接空
        }
        var rows = queryService.listPostings(tenantId(), acc.getId(), page, size);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (var p : rows) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("txId", p.getTxId());
            m.put("txType", p.getTxType());
            m.put("extOrderId", p.getExtOrderId());
            m.put("legSeq", p.getLegSeq());
            m.put("srcAccountId", p.getSrcAccountId());
            m.put("dstAccountId", p.getDstAccountId());
            m.put("assetCode", p.getAssetCode());
            m.put("amount", p.getAmount());
            m.put("direction", p.getDirection());
            m.put("balanceAfter", p.getBalanceAfter() == null ? "" : p.getBalanceAfter());
            m.put("status", p.getStatus());
            m.put("createdAt", p.getCreatedAt() == null ? "" : p.getCreatedAt().toString());
            out.add(m);
        }
        return ApiResponse.success(out);
    }

    private Long tenantId() {
        Object claim = TokenContext.getClaim("tenant_id");
        if (claim == null) return null;
        if (claim instanceof Long l) return l;
        if (claim instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(claim));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
