package fun.commons.benefit4j.it;

import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * assets 域 SMOKE(真实进程,非 IT 上下文): 前置 = 本地已起 benefit4j-app(9200)。
 * 未启动时自动 skip(assumeTrue),不进常规回归。
 *
 * 覆盖「平台登录 → 运营页」真实链(P1 修复回归): APP token 打 platform/assets 正例链
 * (列表含种子/新建/停启用/流水查询);OPS token 打运维通道(reconcile/run);
 * APP token 无签名打 runtime 资金 API → 被签名拦截(InMemorySecretProvider 无注册密钥=全拒模式,
 * 等接入方配置前的安全默认);无 token → 401。
 */
@Tag("smoke")
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class,
        properties = "spring.application.name=benefit4j-backend")   // 与被测 app 同名,Redis 会话 key(appName 前缀)互通
public class AssetsSmokeIT extends BaseMapperTest {

    private static final String BASE = "http://localhost:9200";

    @Autowired
    private AccessTokenGenerator tokenGenerator;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    private Long tenantId;
    private String opsToken;
    private String appToken;

    @BeforeAll
    static void requireApp() throws Exception {
        AssetsMigrations.applyAll();
        boolean up = false;
        try {
            HTTP_PROBE();
            up = true;
        } catch (Exception ignore) {
            // app 未启动
        }
        Assumptions.assumeTrue(up, "benefit4j-app 未在 9200 运行,smoke 跳过(启动: cd backend/benefit4j-app && mvn spring-boot:run)");
    }

    private static void HTTP_PROBE() throws Exception {
        HttpClient.newBuilder().build().send(
                HttpRequest.newBuilder(URI.create(BASE + "/benefit/api/v1/platform/assets")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void ensureTokens() {
        if (appToken != null) return;
        if (tenantId == null) tenantId = createTenant().getId();
        Map<String, Object> claims = Map.of("tenant_id", String.valueOf(tenantId));
        opsToken = tokenGenerator.generateToken("OPS", claims);
        appToken = tokenGenerator.generateToken("APP", claims);
    }

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));
        switch (method) {
            case "GET" -> b.GET();
            case "POST" -> b.POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
            default -> throw new IllegalArgumentException(method);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void smoke_platformPositiveChain() throws Exception {
        ensureTokens();

        // ① 资产列表(平台登录链,APP token): 种子可见
        HttpResponse<String> list = call("GET", "/benefit/api/v1/platform/assets", appToken, null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("POINTS").contains("GOLD").contains("COMPUTE");

        // ② 新建资产 → 200
        String code = "SMOKE" + uniqueTenantid().substring(0, 6).toUpperCase();
        HttpResponse<String> create = call("POST", "/benefit/api/v1/platform/assets", appToken,
                "{\"code\":\"" + code + "\",\"name\":\"冒烟-" + code + "\",\"asset_type\":\"VIRTUAL\",\"precision\":2}");
        assertThat(create.statusCode()).isEqualTo(200);
        assertThat(create.body()).contains(code);

        // ③ 停用 → 启用 → 200
        assertThat(call("POST", "/benefit/api/v1/platform/assets/" + code + "/suspend", appToken, null).statusCode())
                .isEqualTo(200);
        assertThat(call("POST", "/benefit/api/v1/platform/assets/" + code + "/resume", appToken, null).statusCode())
                .isEqualTo(200);

        // ④ 流水查询(平台视角,账户不存在返回空数组)
        HttpResponse<String> postings = call("GET",
                "/benefit/api/v1/platform/assets/postings?account_ref=user:999999&asset_code=POINTS", appToken, null);
        assertThat(postings.statusCode()).isEqualTo(200);
    }

    @Test
    public void smoke_opsChannel_reconcile() throws Exception {
        ensureTokens();
        // OPS token → 运维通道对账(本 tenantId 无流量,恒等式应成立)
        HttpResponse<String> resp = call("POST", "/benefit/api/v1/assets/ops/reconcile/run", opsToken,
                "{\"tenantId\":" + tenantId + ",\"assetCode\":\"POINTS\"}");
        assertThat(resp.statusCode()).isEqualTo(200);

        // 反向验证 token 型别隔离: OPS 型 token 打 runtime(要求 APP 型)→ 拒绝
        HttpResponse<String> wrongType = call("POST", "/benefit/api/v1/assets/runtime/issue", opsToken,
                "{\"extOrderId\":\"SMOKE-OPS-1\",\"legs\":[{\"src\":\"issue:POINTS\",\"dst\":\"user:1\",\"assetCode\":\"POINTS\",\"amount\":\"1\"}]}");
        assertThat(wrongType.statusCode()).isNotEqualTo(200);
    }

    @Test
    public void smoke_runtimeSignatureGate_active() throws Exception {
        ensureTokens();
        // APP token 合法但无签名头: 签名拦截器必须拒绝(全拒模式,secret 未注册)
        HttpResponse<String> resp = call("POST", "/benefit/api/v1/assets/runtime/issue", appToken,
                "{\"extOrderId\":\"SMOKE-SIG-1\",\"legs\":[{\"src\":\"issue:POINTS\",\"dst\":\"user:1\",\"assetCode\":\"POINTS\",\"amount\":\"1\"}]}");
        assertThat(resp.statusCode()).isNotEqualTo(200);
        assertThat(resp.body()).contains("\"success\":false");
    }

    @Test
    public void smoke_unauthorizedWithoutToken() throws Exception {
        HttpResponse<String> resp = HTTP.send(
                HttpRequest.newBuilder(URI.create(BASE + "/benefit/api/v1/platform/assets")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(401);
    }
}
