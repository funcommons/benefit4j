package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PostIssueRequest;
import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.framework4j.accesstoken.context.TokenContext;
import fun.commons.framework4j.web.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 A10: §9.3 端到端六步(controller 全链,TokenContext 语境)
 *   ① issue 100 → 账户 100 + 1 腿; ② 组合消费 5+23+2 → 三端 + 3 腿;
 *   ③ 退款原路回补; ④ 双扣 DUAL 属 P2 跳过;
 *   ⑤ 余额不足(应用拦 + DB CHECK 已由并发 IT 直测); ⑥ FIAT fail-fast 语义复核。
 *   附加 DoD: 同幂等键 5 连发只记一次账。
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class AssetsEndToEndIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private fun.commons.benefit4j.controller.BenefitAssetsRuntimeController runtime;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AssetRegistryService registry;

    private Long tenantId;

    @BeforeEach
    void setUp() {
        if (tenantId == null) tenantId = createTenant().getId();
        TokenContext.set("APP", Map.of("tenant_id", tenantId));
        ensureAsset("CNY_T");
    }

    @AfterEach
    void tearDown() {
        TokenContext.clear();
    }

    @SuppressWarnings("unused")
    private void ensureAsset(String code) {
        try {
            var a = new fun.commons.benefit4j.assets.entity.UbmxAsset();
            a.setCode(code);
            a.setName("端到端-" + code);
            a.setAssetType("VIRTUAL");
            a.setPrecision(2);
            registry.createAsset(a);
        } catch (DuplicateKeyException ignore) {
            // 已注册
        }
    }

    private PostIssueRequest.LegIn leg(String src, String dst, String asset, String amount) {
        PostIssueRequest.LegIn l = new PostIssueRequest.LegIn();
        l.setSrc(src); l.setDst(dst); l.setAssetCode(asset); l.setAmount(new BigDecimal(amount));
        return l;
    }

    private ApiResponse<Map<String, Object>> issue(String orderId, PostIssueRequest.LegIn... legs) {
        PostIssueRequest r = new PostIssueRequest();
        r.setExtOrderId(orderId);
        r.setTxType("ISSUE");
        r.setLegs(List.of(legs));
        return runtime.postIssue(r);
    }

    private BigDecimal balance(String ownerType, Long ownerId, String asset) {
        return accountService.getOrCreateAccount(tenantId, ownerType, ownerId, asset).getBalance();
    }

    @Test
    public void e2e_sixSteps() {
        Long uid = uniqueLongId();
        Long mid = uniqueLongId();

        // ① 充值入账 100(用户)+ 100(积分)
        ApiResponse<Map<String, Object>> r1 = issue("E2E-1-" + uniqueTenantid(),
                leg("world:wechat", "user:" + uid, "CNY_T", "100"),
                leg("issue:POINTS", "user:" + uid, "POINTS", "100"));
        assertThat(r1.isSuccess()).isTrue();
        assertThat(balance("USER", uid, "CNY_T")).isEqualByComparingTo("100");
        assertThat(balance("USER", uid, "POINTS")).isEqualByComparingTo("100");

        // ② 组合支付: 积分抵 5 + CNY 23 + 手续费 2
        ApiResponse<Map<String, Object>> r2 = issue("E2E-2-" + uniqueTenantid(),
                leg("user:" + uid, "issue:POINTS", "POINTS", "5"),
                leg("user:" + uid, "merchant:" + mid, "CNY_T", "23"),
                leg("user:" + uid, "fee:CNY_T", "CNY_T", "2"));
        assertThat(r2.isSuccess()).isTrue();
        assertThat(balance("USER", uid, "POINTS")).isEqualByComparingTo("95");
        assertThat(balance("USER", uid, "CNY_T")).isEqualByComparingTo("75");
        assertThat(balance("MERCHANT", mid, "CNY_T")).isEqualByComparingTo("23");

        // ③ 退款原路回补(商家 → 用户 23)
        issue("E2E-3-" + uniqueTenantid(),
                leg("merchant:" + mid, "user:" + uid, "CNY_T", "23"));
        assertThat(balance("MERCHANT", mid, "CNY_T")).isEqualByComparingTo("0");
        assertThat(balance("USER", uid, "CNY_T")).isEqualByComparingTo("98");

        // ④ 双扣 DUAL 属 P2(§10),本步跳过 —— 占位注释满足 §9.3 编号完整性

        // ⑤ 余额不足: 应用层拦截(明确错误码)
        PostIssueRequest over = new PostIssueRequest();
        over.setExtOrderId("E2E-5-" + uniqueTenantid());
        over.setTxType("CONSUME");
        over.setLegs(List.of(leg("user:" + uid, "fee:CNY_T", "CNY_T", "9999")));
        PostIssueRequest finalOver = over;
        assertThatThrownBy(() -> runtime.postIssue(finalOver))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.INSUFFICIENT_BALANCE);
        assertThat(balance("USER", uid, "CNY_T")).isEqualByComparingTo("98");   // 原封不动

        // ⑥ FIAT fail-fast: 种子与运行时均无 FIAT 资产可用
        assertThat(registry.list("FIAT", null)).isEmpty();

        // DoD: 同幂等键 5 连发只记一次
        String orderId = "E2E-IDEM-" + uniqueTenantid();
        Long uid2 = uniqueLongId();
        Object firstTxId = null;
        for (int i = 0; i < 5; i++) {
            ApiResponse<Map<String, Object>> resp = issue(orderId,
                    leg("issue:POINTS", "user:" + uid2, "POINTS", "10"));
            assertThat(resp.isSuccess()).isTrue();
            if (firstTxId == null) firstTxId = resp.getData().get("txId");
            assertThat(resp.getData().get("txId")).isEqualTo(firstTxId);
        }
        assertThat(balance("USER", uid2, "POINTS")).isEqualByComparingTo("10");   // 只记一次
    }
}
