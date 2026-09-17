package fun.commons.benefit4j.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * SPA 控制台配置(benefit4j.console)。
 *
 * <p>家族裁决: admin 控制台随 starter 单部署物分发(classpath:/static/),默认开启;
 * 消费方可 {@code benefit4j.console.enabled: false} 关闭。
 */
@Data
@ConfigurationProperties(prefix = "benefit4j.console")
public class Benefit4jConsoleProperties {

    /**
     * 是否启用内嵌 SPA 控制台(静态资源映射 + history 路由 fallback)
     */
    private boolean enabled = true;

    /**
     * fallback 排除前缀: 请求路径以此开头时不 fallback 到 index.html,保持 404 语义。
     * 默认覆盖记账域 API(/benefit/api/)、framework4j-tenant 认证端点(/api/、/open/)、
     * actuator、/error。消费方可追加自有 API 前缀。
     */
    private List<String> fallbackExcludes = List.of("/benefit/api/", "/api/", "/open/", "/actuator", "/error");
}
