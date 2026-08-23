package fun.commons.benefit4j.transport;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * WebClient 非阻塞响应式 HttpTransport (remote 高 QPS 场景)。
 * <p>
 * 业务方引入 spring-boot-starter-webflux 后, 可替换 RestTemplateHttpTransport:
 * <code>@Bean public HttpTransport httpTransport() { return new WebClientHttpTransport(webClient); }</code>
 * <p>
 * 适合业务方微服务异步调远端 benefit4j (非阻塞, 高并发)。
 * 注意: 返回 Object (block() 获取, 简化; 完全响应式需改 Client 接口返回 Mono/Flux)。
 */
public class WebClientHttpTransport implements HttpTransport {

    private final WebClient webClient;

    public WebClientHttpTransport(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Object post(String url, Object body, Map<String, String> headers) {
        return webClient.post().uri(url)
                .headers(h -> applyHeaders(h, headers))
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block();
    }

    @Override
    public Object get(String url, Map<String, String> headers) {
        return webClient.get().uri(url)
                .headers(h -> applyHeaders(h, headers))
                .retrieve().bodyToMono(Object.class).block();
    }

    @Override
    public Object put(String url, Object body, Map<String, String> headers) {
        return webClient.put().uri(url)
                .headers(h -> applyHeaders(h, headers))
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block();
    }

    @Override
    public Object delete(String url, Map<String, String> headers) {
        return webClient.delete().uri(url)
                .headers(h -> applyHeaders(h, headers))
                .retrieve().bodyToMono(Object.class).block();
    }

    private void applyHeaders(org.springframework.http.HttpHeaders target, Map<String, String> source) {
        target.setContentType(MediaType.APPLICATION_JSON);
        if (source != null) source.forEach(target::add);
    }
}
