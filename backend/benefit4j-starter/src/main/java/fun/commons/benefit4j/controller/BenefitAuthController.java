package fun.commons.benefit4j.controller;

import fun.commons.benefit4j.service.BenefitAuthService;
import fun.commons.framework4j.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class BenefitAuthController {

    private final ObjectProvider<BenefitAuthService> authServiceProvider;

    BenefitAuthController(ObjectProvider<BenefitAuthService> authServiceProvider) {
        this.authServiceProvider = authServiceProvider;
    }

    @PostMapping("/benefit/api/v1/auth/token")
    public Object postToken(HttpServletRequest request) {
        Map<String, String> params = parseFormParams(request);

        String grantType = params.get("grant_type");
        String clientId = params.get("client_id");
        String clientSecret = params.get("client_secret");

        // fallback to query string
        if (grantType == null) grantType = request.getParameter("grant_type");
        if (clientId == null) clientId = request.getParameter("client_id");
        if (clientSecret == null) clientSecret = request.getParameter("client_secret");

        if (grantType == null || grantType.isEmpty()) {
            return ApiResponse.fail(400, "缺少必要参数: [grant_type]");
        }
        if (clientId == null || clientId.isEmpty()) {
            return ApiResponse.fail(400, "缺少必要参数: [client_id]");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            return ApiResponse.fail(400, "缺少必要参数: [client_secret]");
        }

        BenefitAuthService authService = authServiceProvider.getIfAvailable();
        if (authService == null) {
            return ApiResponse.fail(503, "认证服务未启用");
        }
        return authService.postToken(grantType, clientId, clientSecret);
    }

    private Map<String, String> parseFormParams(HttpServletRequest request) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8));
            String body = reader.lines().collect(Collectors.joining());
            if (body.isEmpty()) return Map.of();

            Map<String, String> params = new java.util.LinkedHashMap<>();
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    params.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                               java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
            return params;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
