package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.framework4j.transport.HttpTransport;
import org.springframework.util.Assert;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Remote Client 公共基类 (双模式 remote 用)。
 * <p>
 * 各 RemoteXxxClient 继承, 方法调 {@link #invoke} 即可。
 * 自动注入: Idempotency-Key (写操作) + X-App-Id。
 * S2S JWT / 签名由业务方在 HttpTransport 拦截器层注入 (可替换 Bean)。
 */
public abstract class AbstractRemoteBenefitClient {

    protected final Benefit4jProperties properties;
    protected final HttpTransport transport;

    protected AbstractRemoteBenefitClient(Benefit4jProperties properties, HttpTransport transport) {
        this.properties = properties;
        this.transport = transport;
    }

    public void init() {
        Assert.hasText(properties.getRemoteUrl(), "remote 模式下, benefit4j.{域}.remote-url 不能为空");
    }

    /** 无 query 参数调用 */
    protected Object invoke(String path, String method, Long tenantId, Object body) {
        return invoke(path, method, tenantId, body, null);
    }

    /** 带 query 参数调用 (分页/过滤) */
    protected Object invoke(String path, String method, Long tenantId, Object body, Map<String, String> query) {
        String url = properties.getRemoteUrl() + path;
        if (query != null && !query.isEmpty()) {
            String qs = query.entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            if (!qs.isEmpty()) url += "?" + qs;
        }
        Map<String, String> headers = new HashMap<>();
        if (tenantId != null) headers.put("X-App-Id", String.valueOf(tenantId));
        String m = method.toUpperCase();
        if ("POST".equals(m) || "PUT".equals(m) || "DELETE".equals(m)) {
            headers.put("Idempotency-Key", UUID.randomUUID().toString());
        }
        return switch (m) {
            case "POST" -> transport.post(url, body, headers);
            case "GET" -> transport.get(url, headers);
            case "PUT" -> transport.put(url, body, headers);
            case "DELETE" -> transport.delete(url, headers);
            default -> throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
        };
    }
}
