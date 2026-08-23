# ADR-0004: outbox 事件表 — 分布式事务预留而非 Seata

> **状态**: 已实施 · **日期**: 2026-08 · **版本**: V1.2.2

## 背景

扣减/退款/补偿是资金类写操作,需保证一致性。benefit4j 是单体(本地事务+乐观锁已足够),但未来可能拆服务。

## 决策

用 outbox 事件表(`ubma_outbox`),而非引入 Seata AT/SAGA。

## 理由
- 单体内 Seata 空转(无跨服务调用,需 TC server,徒增复杂度)
- outbox 模式:本地事务写业务+outbox(原子),异步发布(poll PENDING→SENT)
- 拆服务时 outbox → Kafka,无需改业务代码

## 后果
- ✅ 单体内一致性由本地事务保证,outbox 是预留(无消费者)
- ✅ OutboxPublisher @Scheduled poll,不阻塞主流程
- ✅ 拆服务时改 OutboxPublisher 发 MQ,业务零改动
- ⚠️ 单体内 outbox 无实际消费者(仅标记 SENT)
