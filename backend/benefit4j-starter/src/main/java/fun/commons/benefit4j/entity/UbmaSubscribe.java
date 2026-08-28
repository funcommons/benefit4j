package fun.commons.benefit4j.entity;

import fun.commons.framework4j.openid.annotation.OpenId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户的核心资产账户主账本。记录用户获取的产品记录，承载整体的有效期。
 */
@Getter
@Setter
@TableName(value = "ubma_subscribe", autoResultMap = true)
public class UbmaSubscribe {
    /**
     * 主键ID (订阅记录的唯一流水号)
     */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /**
     * 归属的租户主键ID (关联 ubma_tenant.id)
     */
    @OpenId
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 外部业务系统的真实用户ID
     */
    private String userid;

    /**
     * 获取的产品/权益集ID
     */
    @OpenId
    private Long setId;

    /**
     * 集合级总消耗量
     */
    private Integer totalConsumed;

    /**
     * 集合级当期消耗量
     */
    private Integer periodConsumed;

    /**
     * 集合级当期额度上限快照
     */
    private Integer quotaLimit;

    /**
     * 集合级下一次额度刷新时间
     */
    private java.time.OffsetDateTime nextRefreshTime;

    /**
     * 外部业务订阅单号(幂等防重)
     */
    private String externalOrderId;

    /**
     * 集合级预扣冻结量 (TCC/两阶段提交的预占额度)
     */
    private Integer frozenConsumed;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 资产实际生效起始时间
     */
    private java.time.OffsetDateTime dateBegin;

    /**
     * 资产实际失效结束时间
     */
    private java.time.OffsetDateTime dateEnd;

    /**
     * 订阅状态 (状态机):
     * <pre>
     *   ACTIVE --(额度耗尽)--> EXHAUSTED --(刷新周期)--> ACTIVE
     *     |                      |
     *     +--(退订 cancel)------> CANCELED
     *     +--(dateEnd 到期)-----> EXPIRED
     *   转换: 创建->ACTIVE / 耗尽->EXHAUSTED / 刷新->ACTIVE / 退订->CANCELED / 到期->EXPIRED
     *   终态: CANCELED/EXPIRED 不可恢复
     * </pre>
     */
    private String status;

    /**
     * 租户自定义透传动态属性 (底层必须建 GIN 索引)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object ext;

    /**
     * 创建时间
     */
    private java.time.OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private java.time.OffsetDateTime updatedAt;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 逻辑删除标志(0:未删, 1:已删)
     */
    @TableLogic
    private Short isDeleted;

}
