package fun.commons.benefit4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 事件 outbox 表 (分布式事务预留)。
 * <p>
 * 本地事务写 consume/refund/compensation 时同事务写 outbox (原子)，
 * OutboxPublisher @Scheduled poll PENDING → SENT。
 * 单体内无 MQ 消费者，仅标记 SENT；拆服务时改发 Kafka。
 */
@Getter
@Setter
@TableName(value = "ubma_outbox", autoResultMap = true)
public class UbmaOutbox {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** consume / refund / compensation */
    private String aggregateType;

    /** 关联实体 id */
    private Long aggregateId;

    /** CREATED / COMMITTED / REFUNDED / ADDED */
    private String eventType;

    /** 事件负载 (JSON) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object payload;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime sentAt;
}
