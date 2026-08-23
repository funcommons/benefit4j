# ADR-0005: Flyway + Druid Wall 绕过

> **状态**: 已实施 · **日期**: 2026-08 · **版本**: V1.2.2

## 背景

启用 Flyway 管理 schema 演进,但 Druid Wall 拦截 Flyway 脚本的 `--` 注释 + 部分索引 `WHERE` 语法(PG 语法 Druid parser 不认)。

## 决策

配 `spring.flyway.url` 独立 datasource 绕过 Druid Wall,而非改 Druid Wall 配置。

## 理由
- Druid Wall 是 SQL 注入防护,不应为 Flyway 放宽(commentAllow 有安全风险)
- Flyway 用独立 PG 连接(DriverManager),不经过 Druid filter chain
- 同 IT 用 DriverManager 绕 Wall 跑 EXPLAIN/ANALYZE 的思路

## 后果
- ✅ Flyway 迁移正常(注释 + 部分索引 WHERE 不拦)
- ✅ Druid Wall 保持严格(业务 SQL 仍防护)
- ⚠️ 两个 PG 连接(Flyway 独立 + Druid 业务),但 Flyway 仅启动时跑,开销可忽略
- ⚠️ V1.2.2 脚本去 `--` 注释(纯 SQL,Druid Wall 即使绕过也安全)
