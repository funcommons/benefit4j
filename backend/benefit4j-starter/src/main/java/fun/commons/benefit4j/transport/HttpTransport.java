package fun.commons.benefit4j.transport;

import java.util.Map;

/**
 * HTTP 传输抽象 (双模式 remote 用)。
 * <p>
 * 默认实现 {@link RestTemplateHttpTransport} (RestTemplate 同步)。
 * 业务方可替换为 WebClient (响应式) / gRPC / 带 S2S JWT 拦截器的实现。
 * <p>
 * 开闭原则: 新增传输协议无需改 RemoteClient, 只替换此 Bean。
 */
public interface HttpTransport {

    Object post(String url, Object body, Map<String, String> headers);

    Object get(String url, Map<String, String> headers);

    Object put(String url, Object body, Map<String, String> headers);

    Object delete(String url, Map<String, String> headers);
}
