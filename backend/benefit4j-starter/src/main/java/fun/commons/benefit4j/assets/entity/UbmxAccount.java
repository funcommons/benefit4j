package fun.commons.benefit4j.assets.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 资产账户 = (app × 主体 × 资产)。资金安全兜底:
 * CHECK (account_type='BOUNDARY' OR balance >= -credit_limit)。
 * 注意 version 为 O12 定案的纯审计字段,【不加 @Version】,
 * 由 PostingService 的 SQL 显式 version = version + 1。
 */
@Getter
@Setter
@TableName(value = "ubmx_account", autoResultMap = true)
public class UbmxAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 多租户隔离: 幂等键命名空间 / 数据权限 / 对账切片 */
    private Long tenantId;

    /** USER | TENANT | MERCHANT | PLATFORM | EXTERNAL */
    private String ownerType;

    /** 主体 ID(EXTERNAL = 钱包中台 ID,内部约定) */
    private Long ownerId;

    private String assetCode;

    /** NORMAL | BOUNDARY(O11: 边界户豁免锁与余额约束) */
    private String accountType;

    private BigDecimal balance;

    /** F1 授信额度,默认 0 = 不授信 */
    private BigDecimal creditLimit;

    /** 冻结缓存聚合,主数据源 = ubmx_freeze(O14) */
    private BigDecimal frozen;

    /** 审计自增(O12: 不参与更新条件) */
    private Integer version;

    /** ACTIVE | FROZEN | CLOSED */
    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object ext;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;
}
