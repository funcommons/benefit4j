# ADR-0002: 多源额度桶 — 扩展 ubma_subscribe_item 而非新表

> **状态**: 已实施 · **日期**: 2026-08 · **版本**: V1.2.0

## 背景

同一权益项下需支持多种来源余额(月度赠送 + 独立充值 + 人工补偿),各自不同过期策略/优先级。需选择数据模型。

## 决策

扩展 `ubma_subscribe_item`(加 source_type / expires_at / bucket_priority 3 列),而非新建 `ubma_balance_bucket` 表。

## 理由
- `ubma_subscribe_item` **已经是桶**(quotaLimit/periodConsumed/frozenConsumed/version 三件套 + 乐观锁)
- 新建表 → 双写 + 双迁移 + 扣减逻辑分裂(过度抽象)
- 扩展列 → 扣减主循环复用,仅新增过滤 + 排序键(最小破坏面)

## 后果
- ✅ 扣减/退款/TCC 全复用,零改动
- ✅ 部分唯一索引 `uk_subscribe_item_source` 防重复补偿
- ⚠️ 桶字段与订阅明细耦合(但语义一致,可接受)
