package fun.commons.benefit4j.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Pattern;

@Data
@Validated
@ConfigurationProperties(prefix = "benefit4j.runtime")
public class Benefit4jProperties {

    /**
     * 运行模式：local（本地进程内调用）或 remote（远程 HTTP 调用）
     */
    @Pattern(regexp = "^(local|remote)$", message = "mode 只能是 local 或 remote")
    private String mode = "local";

    /**
     * 是否启动内嵌的 OpenAPI Web 层路由
     */
    private boolean enableApi = false;

    /**
     * remote 模式下的远程服务端点，例如 http://benefit4j-svc:8080
     */
    private String remoteUrl;

    /**
     * 预扣冻结确认超时时间（秒），超时后自动释放。默认10秒。
     */
    private int reserveTimeoutSeconds = 10;

    /**
     * remote 模式下业务方 app_id (OpenID, 用于 S2S JWT claims + X-Access-Key)
     */
    private String remoteAppId;

    /**
     * remote 模式下业务方 app_secret (HMAC-SHA256 签名密钥, 从独立部署 benefit4j 平台获取)
     */
    private String remoteAppSecret;
}
