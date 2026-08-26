package fun.commons.benefit4j.controller;

import fun.commons.framework4j.web.ApiResponse;
import fun.commons.framework4j.web.ApiError;
import fun.commons.framework4j.accesstoken.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class Benefit4jExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(Benefit4jExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuth(AuthException ex) {
        return ApiResponse.fail(401, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ApiError.of(fe.getField(), "VALIDATION_ERROR", fe.getDefaultMessage()))
                .toList();
        return ApiResponse.fail(400, "请求参数校验失败", errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingHeader(MissingRequestHeaderException ex) {
        return ApiResponse.fail(400, "缺少必需的请求头: " + ex.getHeaderName());
    }

    /** 重复键 (uk_subscribe_item_source 重复补偿等) → 409 */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("[Exception] 重复键冲突: {}", ex.getMessage());
        return ApiResponse.fail(409, "数据已存在, 请勿重复提交");
    }

    /** assets 域业务异常 → 稳定错误码映射(INSUFFICIENT_BALANCE=402 / 幂等与终态冲突=409 / 不存在=404 / 权限与合规=403 / 参数=400) */
    @ExceptionHandler(fun.commons.benefit4j.assets.exception.AssetsException.class)
    public ApiResponse<Void> handleAssets(fun.commons.benefit4j.assets.exception.AssetsException ex) {
        int http;
        switch (ex.getCode()) {
            case fun.commons.benefit4j.assets.exception.AssetsException.INSUFFICIENT_BALANCE -> http = 402;
            case fun.commons.benefit4j.assets.exception.AssetsException.IDEMPOTENCY_CONFLICT -> http = 409;
            case fun.commons.benefit4j.assets.exception.AssetsException.ASSET_NOT_FOUND,
                 fun.commons.benefit4j.assets.exception.AssetsException.PRE_CONSUME_NOT_FOUND -> http = 404;
            case fun.commons.benefit4j.assets.exception.AssetsException.FIAT_NOT_ALLOWED,
                 fun.commons.benefit4j.assets.exception.AssetsException.ASSET_PRIVILEGE_DENIED -> http = 403;
            default -> http = 400;
        }
        return ApiResponse.fail(http, "[" + ex.getCode() + "] " + ex.getMessage());
    }

    /** 非法参数 → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArg(IllegalArgumentException ex) {
        return ApiResponse.fail(400, ex.getMessage());
    }

    /** 权限不足 (tracelog 控制台 API 等非 token 体系鉴权失败) → 403 */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleSecurity(SecurityException ex) {
        log.warn("[Exception] 权限不足: {}", ex.getMessage());
        return ApiResponse.fail(403, "权限不足");
    }

    /** 未知路径(无匹配路由,Spring 落静态资源解析抛出) → 404,勿走 500 兜底误导排障 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ApiResponse.fail(404, "资源不存在: " + ex.getResourcePath());
    }

    /** 兜底: 未捕获异常 → 500 + trace_id */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleAll(Exception ex) {
        log.error("[Exception] 未捕获异常, trace_id={}", ex.getMessage(), ex);
        return ApiResponse.fail(500, "服务器内部错误, 请联系管理员并提供 trace_id");
    }
}
