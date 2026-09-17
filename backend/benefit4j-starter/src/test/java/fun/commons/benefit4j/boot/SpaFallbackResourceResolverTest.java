package fun.commons.benefit4j.boot;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpaFallbackResourceResolver 直测(同包可见 protected getResource)。
 */
class SpaFallbackResourceResolverTest {

    private final ClassPathResource staticRoot = new ClassPathResource("/static/");

    @Test
    void missingIndexHtmlYieldsNull() throws Exception {
        // index.html 缺失(产物未入库)→ fallback 自然失效,返回 null → 404
        Benefit4jConsoleWebMvcConfigurer.SpaFallbackResourceResolver resolver =
                new Benefit4jConsoleWebMvcConfigurer.SpaFallbackResourceResolver(
                        List.of(), new ClassPathResource("/static/nonexistent-index.html"));
        assertThat(resolver.getResource("login", staticRoot)).isNull();
    }

    @Test
    void excludesMatchedAfterLeadingSlashStripped() throws Exception {
        // 配置项带前导斜杠("/api/"),requestPath 不带 → 归一化后仍命中排除
        Benefit4jConsoleWebMvcConfigurer.SpaFallbackResourceResolver resolver =
                new Benefit4jConsoleWebMvcConfigurer.SpaFallbackResourceResolver(
                        List.of("/api/"), new ClassPathResource("/static/index.html"));
        assertThat(resolver.getResource("api/v1/auth/token", staticRoot)).isNull();
    }

    @Test
    void fallbackExcludesConfigurable() throws Exception {
        // 消费方追加自有前缀后生效
        Benefit4jConsoleWebMvcConfigurer.SpaFallbackResourceResolver resolver =
                new Benefit4jConsoleWebMvcConfigurer.SpaFallbackResourceResolver(
                        List.of("/benefit/api/", "/custom-api/"), new ClassPathResource("/static/index.html"));
        assertThat(resolver.getResource("custom-api/thing", staticRoot)).isNull();
        assertThat(resolver.getResource("some-page", staticRoot)).isNotNull();
    }
}
