package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PreConsumeRequest;
import fun.commons.benefit4j.assets.dto.PreConsumeView;
import fun.commons.benefit4j.assets.dto.SettleRequest;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.benefit4j.assets.service.PostingService;
import fun.commons.benefit4j.assets.service.PreConsumeService;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 A8: Controller 层(runtime issue / 三阶段 / 查询 / OPS 资产)+ 异常映射
 * 鉴权链路(@RequiresToken/@RequiresSignature)由框架与既有 runtime controller 验证,
 * 本 IT 直调方法验证: tenantId 一律取 TokenContext(不信任 body)+ 响应封装 + 错误码映射。
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class AssetsControllerIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private fun.commons.benefit4j.controller.BenefitAssetsRuntimeController runtimeController;

    @Autowired
    private fun.commons.benefit4j.controller.BenefitAssetsPlatformController platformController;

    @Autowired
    private PostingService postingService;

    @Autowired
    private PreConsumeService preConsumeService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AssetRegistryService registry;

    private Long tenantId;

    @BeforeEach
    void setUpToken() {
        if (tenantId == null) tenantId = createTenant().getId();
        TokenContext.set("APP", Map.of("tenant_id", tenantId));
    }

    @AfterEach
    void clearToken() {
        TokenContext.clear();
    }

    private fun.commons.benefit4j.assets.dto.PostIssueRequest issueReq(String orderId, String src, String dst,
            String asset, String amount, Long fakeTenantId) {
        fun.commons.benefit4j.assets.dto.PostIssueRequest r = new fun.commons.benefit4j.assets.dto.PostIssueRequest();
        r.setTenantId(fakeTenantId);   // 故意传假 tenantId,controller 必须以 token 为准
        r.setExtOrderId(orderId);
        r.setTxType("ISSUE");
        fun.commons.benefit4j.assets.dto.PostIssueRequest.LegIn leg = new fun.commons.benefit4j.assets.dto.PostIssueRequest.LegIn();
        leg.setSrc(src);
        leg.setDst(dst);
        leg.setAssetCode(asset);
        leg.setAmount(new BigDecimal(amount));
        r.setLegs(List.of(leg));
        return r;
    }

    @Test
    public void testIssue_tenantIdTakenFromTokenNotBody() {
        String order = "IT-CTRL-1-" + uniqueTenantid();
        Long uid = uniqueLongId();

        ApiResponse<?> resp = runtimeController.postIssue(
                issueReq(order, "issue:POINTS", "user:" + uid, "POINTS", "42", 999999L));

        assertThat(resp.isSuccess()).isTrue();
        // 账户开在 token 的 tenantId 下(body 里的 999999 被忽略)
        var acc = accountService.getOrCreateAccount(tenantId, "USER", uid, "POINTS");
        assertThat(acc.getBalance()).isEqualByComparingTo("42");
    }

    @Test
    public void testIssue_idempotentAtControllerLevel() {
        String order = "IT-CTRL-2-" + uniqueTenantid();
        Long uid = uniqueLongId();
        var req = issueReq(order, "issue:POINTS", "user:" + uid, "POINTS", "10", null);

        ApiResponse<?> first = runtimeController.postIssue(req);
        ApiResponse<?> second = runtimeController.postIssue(req);

        Map<String, Object> d1 = (Map<String, Object>) first.getData();
        Map<String, Object> d2 = (Map<String, Object>) second.getData();
        assertThat(d2.get("txId")).isEqualTo(d1.get("txId"));
        assertThat(accountService.getOrCreateAccount(tenantId, "USER", uid, "POINTS").getBalance())
                .isEqualByComparingTo("10");
    }

    @Test
    public void testThreePhaseThroughController() {
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-CTRL-3-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "100")));

        PreConsumeRequest pre = new PreConsumeRequest();
        pre.setTenantId(1L);   // 假 tenantId
        pre.setRequestId("IT-CTRL-3-" + uniqueTenantid());
        pre.setAccountRef("user:" + uid);
        pre.setAssetCode("POINTS");
        pre.setEstimated(new BigDecimal("30"));
        ApiResponse<?> preResp = runtimeController.postPreConsume(pre);
        assertThat(preResp.isSuccess()).isTrue();

        SettleRequest settle = new SettleRequest();
        settle.setTenantId(1L);
        settle.setRequestId(pre.getRequestId());
        settle.setActual(new BigDecimal("25"));
        ApiResponse<?> settleResp = runtimeController.postSettle(settle);
        assertThat(settleResp.isSuccess()).isTrue();
        assertThat(((PreConsumeView) settleResp.getData()).getStatus()).isEqualTo("SETTLED");

        var acc = accountService.getOrCreateAccount(tenantId, "USER", uid, "POINTS");
        assertThat(acc.getBalance()).isEqualByComparingTo("75");   // 100 - 25
        assertThat(acc.getFrozen()).isEqualByComparingTo("0");
    }

    @Test
    public void testRefundThroughController() {
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-CTRL-4-F-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "100")));
        PreConsumeRequest pre = new PreConsumeRequest();
        pre.setRequestId("IT-CTRL-4-" + uniqueTenantid());
        pre.setAccountRef("user:" + uid);
        pre.setAssetCode("POINTS");
        pre.setEstimated(new BigDecimal("40"));
        runtimeController.postPreConsume(pre);

        fun.commons.benefit4j.assets.dto.RefundRequest refund = new fun.commons.benefit4j.assets.dto.RefundRequest();
        refund.setTenantId(null);
        refund.setRequestId(pre.getRequestId());
        ApiResponse<?> resp = runtimeController.postRefund(refund);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(((PreConsumeView) resp.getData()).getStatus()).isEqualTo("REFUNDED");
        var acc = accountService.getOrCreateAccount(tenantId, "USER", uid, "POINTS");
        assertThat(acc.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    public void testListAccountsByOwner() {
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-CTRL-5-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "7"),
                leg("issue:GOLD", "user:" + uid, "GOLD", "3")));

        ApiResponse<?> resp = runtimeController.getAccounts("USER", uid);
        List<fun.commons.benefit4j.assets.entity.UbmxAccount> rows =
                (List<fun.commons.benefit4j.assets.entity.UbmxAccount>) resp.getData();
        assertThat(rows).extracting(fun.commons.benefit4j.assets.entity.UbmxAccount::getAssetCode)
                .contains("POINTS", "GOLD");
    }

    @Test
    public void testListPostingsByAccount() {
        Long uid = uniqueLongId();
        String order = "IT-CTRL-6-" + uniqueTenantid();
        postingService.commitTx(cmd(order, "ISSUE", leg("issue:POINTS", "user:" + uid, "POINTS", "5")));

        ApiResponse<?> resp = runtimeController.getPostings("user:" + uid, "POINTS", 1, 20);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) resp.getData();
        assertThat(rows).extracting(r -> r.get("extOrderId")).contains(order);
    }

    @Test
    public void testPlatformAssetCrud() {
        String code = "TOP" + uniqueTenantid().substring(0, 6).toUpperCase();
        fun.commons.benefit4j.assets.dto.OpsAssetRequest create =
                new fun.commons.benefit4j.assets.dto.OpsAssetRequest();
        create.setCode(code);
        create.setName("运营资产-" + code);
        create.setAssetType("VIRTUAL");
        create.setPrecision(0);
        ApiResponse<?> created = platformController.postAssets(create);
        assertThat(created.isSuccess()).isTrue();

        // APP token 语境(运营面): 直接调 service 校验落库
        assertThat(registry.getRequired(code).getName()).contains("运营资产");

        ApiResponse<?> list = platformController.getAssets("VIRTUAL", null);
        assertThat(list.isSuccess()).isTrue();
    }

    /** 平台视角查询: 跨 app 合并(同 owner 两 app 各一账户全返回),tenant_id 可收窄 */
    @Test
    public void testPlatformAccountsPostings_crossApp() {
        Long uid = uniqueLongId();
        Long tenantId2 = createTenant().getId();
        postingService.commitTx(cmd("IT-CTRL-7-A-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "9")));
        fun.commons.benefit4j.assets.dto.PostingCommand c2 = cmd(
                "IT-CTRL-7-B-" + uniqueTenantid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "5"));
        c2.setTenantId(tenantId2);
        postingService.commitTx(c2);

        ApiResponse<?> accounts = platformController.getAccounts("USER", uid, null, null);
        List<fun.commons.benefit4j.assets.entity.UbmxAccount> accRows =
                (List<fun.commons.benefit4j.assets.entity.UbmxAccount>) accounts.getData();
        assertThat(accRows).extracting(fun.commons.benefit4j.assets.entity.UbmxAccount::getTenantId)
                .contains(tenantId, tenantId2);   // 跨 app 合并

        ApiResponse<?> narrowed = platformController.getAccounts("USER", uid, "POINTS", tenantId2);
        assertThat(((List<?>) narrowed.getData())).hasSize(1);   // tenant_id 收窄

        ApiResponse<?> postings = platformController.getPostings("user:" + uid, "POINTS", null, 1, 20);
        List<fun.commons.benefit4j.assets.entity.UbmxPosting> postRows =
                (List<fun.commons.benefit4j.assets.entity.UbmxPosting>) postings.getData();
        assertThat(postRows).extracting(p -> p.getAmount().stripTrailingZeros().toPlainString())
                .contains("9", "5");   // 两 app 流水都在
    }

    @Test
    public void testExceptionHandlerMapsAssetsCodes() {
        fun.commons.benefit4j.controller.Benefit4jExceptionHandler handler =
                new fun.commons.benefit4j.controller.Benefit4jExceptionHandler();

        ApiResponse<Void> insufficient = handler.handleAssets(
                new AssetsException(AssetsException.INSUFFICIENT_BALANCE, "x"));
        assertThat(insufficient.getCode()).isEqualTo(402);

        ApiResponse<Void> conflict = handler.handleAssets(
                new AssetsException(AssetsException.IDEMPOTENCY_CONFLICT, "x"));
        assertThat(conflict.getCode()).isEqualTo(409);

        ApiResponse<Void> notFound = handler.handleAssets(
                new AssetsException(AssetsException.ASSET_NOT_FOUND, "x"));
        assertThat(notFound.getCode()).isEqualTo(404);

        ApiResponse<Void> fiat = handler.handleAssets(
                new AssetsException(AssetsException.FIAT_NOT_ALLOWED, "x"));
        assertThat(fiat.getCode()).isEqualTo(403);
    }

    // ---------- locals ----------

    private fun.commons.benefit4j.assets.dto.PostingCommand cmd(String orderId, String txType,
            fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec... legs) {
        fun.commons.benefit4j.assets.dto.PostingCommand c = new fun.commons.benefit4j.assets.dto.PostingCommand();
        c.setTenantId(tenantId);
        c.setExtOrderId(orderId);
        c.setTxType(txType);
        c.setLegs(List.of(legs));
        return c;
    }

    private fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec leg(String src, String dst, String asset, String amount) {
        fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec l = new fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec();
        l.setSrc(src); l.setDst(dst); l.setAssetCode(asset); l.setAmount(new BigDecimal(amount));
        return l;
    }
}
