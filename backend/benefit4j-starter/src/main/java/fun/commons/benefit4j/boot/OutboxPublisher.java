package fun.commons.benefit4j.boot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.benefit4j.entity.UbmaOutbox;
import fun.commons.benefit4j.mapper.UbmaOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Outbox 事件发布器 (分布式事务预留)。
 * <p>
 * 定时 poll PENDING 事件 → 标记 SENT。
 * 单体内无 MQ 消费者, 仅完成 "本地事务写 + 标记" 闭环;
 * 拆服务时把标记逻辑改为发 Kafka, 消费方做对账/通知/审计。
 * <p>
 * 单飞 + 限量: 每次最多 100 条, 避免单次拉取过多。
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final UbmaOutboxMapper outboxMapper;

    @Scheduled(fixedDelayString = "${benefit4j.outbox.publish-interval:5000}")
    public void publishPending() {
        LambdaQueryWrapper<UbmaOutbox> q = new LambdaQueryWrapper<>();
        q.eq(UbmaOutbox::getStatus, "PENDING")
                .orderByAsc(UbmaOutbox::getCreatedAt)
                .last("LIMIT 100");
        List<UbmaOutbox> pending = outboxMapper.selectList(q);
        if (pending.isEmpty()) return;

        int sent = 0;
        for (UbmaOutbox event : pending) {
            try {
                // 单体内: 仅标记 SENT (无 MQ 消费者)
                // 拆服务时: 这里改 producer.send(topic, event.getPayload())
                LambdaUpdateWrapper<UbmaOutbox> u = new LambdaUpdateWrapper<>();
                u.eq(UbmaOutbox::getId, event.getId())
                        .eq(UbmaOutbox::getStatus, "PENDING")   // CAS 防并发重复
                        .set(UbmaOutbox::getStatus, "SENT")
                        .set(UbmaOutbox::getSentAt, OffsetDateTime.now());
                outboxMapper.update(null, u);
                sent++;
            } catch (Exception e) {
                log.warn("[Outbox] 发布失败 id={}: {}", event.getId(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("[Outbox] 发布 {} 条事件 (PENDING→SENT)", sent);
        }
    }
}
