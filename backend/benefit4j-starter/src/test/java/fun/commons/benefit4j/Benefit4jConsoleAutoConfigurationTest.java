package fun.commons.benefit4j;

import fun.commons.benefit4j.autoconfigure.Benefit4jConsoleAutoConfiguration;
import fun.commons.benefit4j.boot.Benefit4jConsoleWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPA 控制台 fallback 集成测试。测试 classpath 自带迷你 static/
 * (index.html + assets/test.js),不依赖真实前端构建产物。
 */
public class Benefit4jConsoleAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WebMvcAutoConfiguration.class,
                    DispatcherServletAutoConfiguration.class,
                    Benefit4jConsoleAutoConfiguration.class));

    @Test
    void spaRouteFallsBackToIndexHtml() {
        contextRunner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("console-index-placeholder")));
        });
    }

    @Test
    void deepSpaRouteFallsBackToIndexHtml() {
        contextRunner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/benefit/app/platform/login").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("console-index-placeholder")));
        });
    }

    @Test
    void benefitApiPathNotSwallowed() {
        contextRunner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            // 无 controller 命中的记账域 API 路径 → 404,不得返回 index.html
            mvc.perform(get("/benefit/api/v1/nonexistent").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound());
        });
    }

    @Test
    void framework4jTenantAuthPathNotSwallowed() {
        contextRunner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            // framework4j-tenant 认证端点前缀(/api/、/open/)不在 /benefit/api/ 之下,必须排除
            mvc.perform(get("/api/v1/auth/token").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/open/api/v1/tenants/register").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound());
        });
    }

    @Test
    void missingStaticAssetNotSwallowed() {
        contextRunner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            // 末段带扩展名的静态文件形态 → 404,避免 JS/CSS 404 被返回 HTML
            mvc.perform(get("/assets/missing.js").accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound());
        });
    }

    @Test
    void realStaticAssetServed() {
        contextRunner.run(context -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/assets/test.js"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("test-asset-placeholder")));
        });
    }

    @Test
    void disabledViaProperty() {
        contextRunner.withPropertyValues("benefit4j.console.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(Benefit4jConsoleWebMvcConfigurer.class));
    }

    @Test
    void enabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(Benefit4jConsoleWebMvcConfigurer.class));
    }
}
