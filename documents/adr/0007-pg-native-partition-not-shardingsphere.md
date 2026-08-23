# ADR-0007: PG 原生分区 — 中等方案而非 ShardingSphere

> **状态**: 已实施 · **日期**: 2026-08 · **版本**: V1.2.2

## 背景

`ubma_consume` 流水表会无限增长,需冷热分离 + 归档。当前数据量小(358 行),但需为规模化准备。分库分表方案选型。

## 决策

PG 原生表分区(`PARTITION BY RANGE (created_at)` 月分区),不引 ShardingSphere。

## 理由
- 数据量远低于分片阈值(500万+),ShardingSphere 过度工程 + 迁移风险
- PG 原生分区:DB 层管理,无中间件,冷热分离(旧分区 detach/归档)
- 中等方案:比"延后"进一步(分区框架就绪),比"ShardingSphere"轻

## 后果
- ✅ `ubma_consume` 重建为分区表(主键 `(id, created_at)` + 月分区 + DEFAULT)
- ✅ 归档接口 `POST /ops/jobs/archive-consumes`(detach/DELETE 旧分区)
- ✅ 358 行迁移成功,MyBatis 透明(分区表 selectById 走全局索引)
- ⚠️ 50万 DAU+ 需分库分表(按 app_id 分片),当前分区是过渡
- ⚠️ 分区表主键含 created_at(MyBatis @TableId 单列,selectById 扫所有分区,数据小可接受)
