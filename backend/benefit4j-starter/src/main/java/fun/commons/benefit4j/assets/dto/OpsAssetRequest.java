package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** OPS 资产定义管理请求(§4.1) */
@Getter
@Setter
public class OpsAssetRequest {

    private String code;
    private String name;
    private String assetType;
    private Integer precision;
    private Boolean canRecharge;
    private Boolean canWithdraw;
    private Boolean canPay;
    private Boolean canTransfer;
    private Boolean canExchange;
    private Boolean canCredit;
    private String issueMode;
    private Map<String, Object> expirePolicy;
    private Map<String, Object> limitPolicy;
    private String description;
}
