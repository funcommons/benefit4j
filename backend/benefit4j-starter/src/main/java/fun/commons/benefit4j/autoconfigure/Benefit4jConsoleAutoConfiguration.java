package fun.commons.benefit4j.autoconfigure;

import fun.commons.benefit4j.boot.Benefit4jConsoleWebMvcConfigurer;
import fun.commons.benefit4j.properties.Benefit4jConsoleProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SPA 控制台自动装配: 内嵌前端产物(classpath:/static/)+ history 路由 fallback。
 *
 * <p>默认开启(benefit4j.console.enabled, matchIfMissing=true);消费方可配置关闭,
 * 或声明自有 {@link Benefit4jConsoleWebMvcConfigurer} bean 覆盖默认映射。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "benefit4j.console", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(Benefit4jConsoleProperties.class)
public class Benefit4jConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Benefit4jConsoleWebMvcConfigurer benefit4jConsoleWebMvcConfigurer(Benefit4jConsoleProperties properties) {
        return new Benefit4jConsoleWebMvcConfigurer(properties);
    }
}
