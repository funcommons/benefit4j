package fun.commons.benefit4j.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * 资产定义(资产注册中心)。code 字符串主键为设计定案(ADR-0008):
 * 新增资产 = INSERT 一行,账本零 schema 迁移。
 */
@Getter
@Setter
@TableName(value = "ubmx_asset", autoResultMap = true)
public class UbmxAsset {

    /** 资产码,全局唯一主键 */
    @TableId(value = "code", type = IdType.INPUT)
    private String code;

    /** 中文名 */
    private String name;

    /** FIAT | VIRTUAL */
    private String assetType;

    /** 小数位数: CNY=2, 积分=0, 算力=4 */
    private Integer precision;

    /** 渠道入账(钱包中台调用) */
    private Boolean canRecharge;

    /** 仅 CNY = TRUE */
    private Boolean canWithdraw;

    /** 可支付 */
    private Boolean canPay;

    /** 可转账(风控,首期关) */
    private Boolean canTransfer;

    /** 虚拟资产可相互兑换 */
    private Boolean canExchange;

    /** F1 授信负余额(列随 P1 落,能力 P2 实现) */
    private Boolean canCredit;

    /** RECHARGE | GIFT | ACTIVITY | EXCHANGE */
    private String issueMode;

    /** {"strategy":"NEVER"|"FIXED"|"ROLLING"} */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object expirePolicy;

    /** {"singleMax":"10000.00","dailyMax":...} 亚洲/上海营业日口径 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object limitPolicy;

    private String description;

    /** ACTIVE | SUSPEND */
    private String status;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;
}
