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
 * 安全: appId 一律取 TokenContext,body 携带的 appId 被忽略(多租户隔离不可伪造)。
 */
@RestController
@RequestMapping("/benefit/api/v1/assets")
@RequiredArgsConstructor
@RequiresToken(value = "APP", type = "access")
@RequiresSignature
public class BenefitAssetsRuntimeController {

    private final PostingService postingService;
    private final PreConsumeService preConsumeService;
    private final AccountService accountService;
    private final AssetsQueryService queryService;

    /** 入账/通用记账(钱包中台 issue 模板 / 运营发奖,§4.3.1) */
    @PostMapping("/runtime/issue")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    public ApiResponse<Map<String, Object>> postIssue(@Valid @RequestBody PostIssueRequest req) {
        PostingCommand cmd = new PostingCommand();
        cmd.setAppId(appId());
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
    public ApiResponse<Object> postPreConsume(@Valid @RequestBody PreConsumeRequest req) {
        req.setAppId(appId());   // token 覆盖 body
        return ApiResponse.success(preConsumeService.preConsume(req));
    }

    /** 结算(confirm 实际额,diff 自动回补/补扣) */
    @PostMapping("/runtime/settle")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    public ApiResponse<Object> postSettle(@Valid @RequestBody SettleRequest req) {
        req.setAppId(appId());
        return ApiResponse.success(preConsumeService.settle(req));
    }

    /** 退款(全额释放预扣) */
    @PostMapping("/runtime/refund")
    @RateLimit(limit = 100, window = "1m", scope = "APP")
    public ApiResponse<Object> postRefund(@Valid @RequestBody RefundRequest req) {
        return ApiResponse.success(preConsumeService.refund(appId(), req.getRequestId()));
    }

    /** 主体名下所有资产账户(§4.2) */
    @GetMapping("/accounts")
    @RateLimit(limit = 300, window = "1m", scope = "APP")
    public ApiResponse<List<UbmxAccount>> getAccounts(
            @RequestParam("owner_type") String ownerType,
            @RequestParam("owner_id") Long ownerId) {
        return ApiResponse.success(queryService.listAccounts(appId(), ownerType, ownerId));
    }

    /** 账户流水分页(§4.5,按账户引用 + 资产) */
    @GetMapping("/postings")
    @RateLimit(limit = 300, window = "1m", scope = "APP")
    public ApiResponse<List<Map<String, Object>>> getPostings(
            @RequestParam("account_ref") String accountRef,
            @RequestParam("asset_code") String assetCode,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        UbmxAccount acc = accountService.findRef(appId(), accountRef, assetCode);
        if (acc == null) {
            return ApiResponse.success(List.of());   // 只读解析: 不存在不开户,直接空
        }
        var rows = queryService.listPostings(appId(), acc.getId(), page, size);
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

    private Long appId() {
        Object claim = TokenContext.getClaim("app_id");
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
