package fun.commons.benefit4j.assets.exception;

/**
 * assets 域业务异常。code 为稳定错误码(前端/调用方按 code 分流),
 * Step5 在 Benefit4jExceptionHandler 统一映射为 4xx 语义响应。
 */
public class AssetsException extends RuntimeException {

    public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
    public static final String ASSET_SUSPENDED = "ASSET_SUSPENDED";
    public static final String ASSET_INVALID = "ASSET_INVALID";
    public static final String FIAT_NOT_ALLOWED = "FIAT_NOT_ALLOWED";
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String PRE_CONSUME_NOT_FOUND = "PRE_CONSUME_NOT_FOUND";
    public static final String FREEZE_NOT_FOUND = "FREEZE_NOT_FOUND";
    public static final String ASSET_PRIVILEGE_DENIED = "INSUFFICIENT_PRIVILEGE";

    private final String code;

    public AssetsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
