# benefit4j ADR (Architecture Decision Records)

> 架构决策记录:每个重要决策的背景/决策/后果,供后人理解设计意图。

## 索引

| ADR | 标题 | 状态 |
|---|---|---|
| [0001](0001-framework4j-tokencontext-appid-missing.md) | framework4j TokenContext 不填 app_id claim | 已修复 (v1.2.8) |
| [0002](0002-multi-source-bucket-extend-subscribe-item.md) | 多源额度桶 — 扩展 ubma_subscribe_item 而非新表 | 已实施 |
| [0003](0003-dual-mode-starter.md) | 双模式架构 (user-starter 模式) | 已实施 |
| [0004](0004-outbox-not-seata.md) | outbox 事件表 — 分布式事务预留而非 Seata | 已实施 |
| [0005](0005-flyway-druid-wall-bypass.md) | Flyway + Druid Wall 绕过 | 已实施 |
| [0006](0006-app-secret-encryption-lazy-key.md) | app_secret AES 加密 + TypeHandler lazy key | 已实施 |
| [0007](0007-pg-native-partition-not-shardingsphere.md) | PG 原生分区 — 中等方案而非 ShardingSphere | 已实施 |

## 约定

- 新决策写新 ADR(编号递增),不修改已发布 ADR(写新 ADR 覆盖)
- 每篇:背景(为什么需要)→ 决策(选什么)→ 后果(✅收益 / ⚠️代价)
- 状态:已实施 / 已修复 / 已废弃 / 已替代
