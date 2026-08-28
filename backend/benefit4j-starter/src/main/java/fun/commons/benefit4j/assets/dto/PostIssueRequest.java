package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** issue/记账 API 请求(§4.3.1 leg 模板)。tenantId 以 TokenContext 为准,body 传入将被忽略 */
@Getter
@Setter
public class PostIssueRequest {

    private Long tenantId;

    /** 调用方幂等键(issueOrderId) */
    private String extOrderId;

    /** ISSUE | CONSUME | REFUND | TRANSFER | EXCHANGE | ADJUST,默认 ISSUE */
    private String txType;

    private List<LegIn> legs;

    private Object ext;

    @Getter
    @Setter
    public static class LegIn {
        private String src;
        private String dst;
        private String assetCode;
        private BigDecimal amount;
    }
}
