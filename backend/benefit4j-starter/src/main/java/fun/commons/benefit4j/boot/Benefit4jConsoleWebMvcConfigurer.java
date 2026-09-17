package fun.commons.benefit4j.boot;

import fun.commons.benefit4j.properties.Benefit4jConsoleProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * SPA 控制台 WebMvc 配置: classpath:/static/ 静态资源 + history 路由 fallback。
 *
 * <p>controller 映射优先于 resource handler,真 API 端点永不受影响;
 * fallback 只拦「无 controller 命中、无静态资源命中」的 GET 请求。
 */
public class Benefit4jConsoleWebMvcConfigurer implements WebMvcConfigurer {

    private final Benefit4jConsoleProperties properties;

    public Benefit4jConsoleWebMvcConfigurer(Benefit4jConsoleProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver(
                        properties.getFallbackExcludes(),
                        new ClassPathResource("/static/index.html")));
    }

    /**
     * SPA history 路由 fallback 解析器(Spring 官方 SPA 模式)。规则按序:
     * <ol>
     *   <li>真实资源存在且可读 → 返回(js/css/index.html 正常命中)</li>
     *   <li>命中排除前缀 → null(API 404 语义不吞)</li>
     *   <li>末段含 {@code .} → null(静态文件形态 404 不吞,避免 JS 拿到 HTML)</li>
     *   <li>否则 → index.html;index.html 缺失(产物未入库)时自然失效返回 null</li>
     * </ol>
     */
    public static class SpaFallbackResourceResolver extends PathResourceResolver {

        private final List<String> excludes;
        private final Resource indexHtml;

        public SpaFallbackResourceResolver(List<String> fallbackExcludes, Resource indexHtml) {
            // 归一化: resource handler 传入的 requestPath 无前导斜杠,配置项按 URL 习惯带斜杠
            this.excludes = fallbackExcludes.stream()
                    .map(p -> p.startsWith("/") ? p.substring(1) : p)
                    .toList();
            this.indexHtml = indexHtml;
        }

        @Override
        protected Resource getResource(String requestPath, Resource location) throws IOException {
            Resource resource = location.createRelative(requestPath);
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            for (String prefix : excludes) {
                if (requestPath.startsWith(prefix)) {
                    return null;
                }
            }
            String lastSegment = requestPath.substring(requestPath.lastIndexOf('/') + 1);
            if (lastSegment.contains(".")) {
                return null;
            }
            return indexHtml.exists() ? indexHtml : null;
        }
    }
}
