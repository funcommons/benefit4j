# ADR-0009: assets 域演进路径 —— 与 Stripe Ledger 对标

> **状态**: 已决定 · **日期**: 2026-08 · **版本**: v0.2 · **关联**: [assets-design.md §12](../assets-design.md)

> v0.2 增补:幂等模型对齐 Stripe `Idempotency-Key` + 响应快照 + 异参冲突报错(O10 `ubmx_tx_order`);热点治理对齐支付宝「边界户不占锁」思想(O11,异步入账合并在 1~100w 条/日量级下显式不做);授信负余额对齐 Stripe credit 模式(F1 预留列)。

## 背景

assets 域核心设计借鉴 Stripe Ledger 复式记账模型。演进路径需要对齐大厂成熟方案,避免从零造轮子——同时接受技术栈差异(Java vs Go/Cloud)、合规边界差异(国内 multi-资产 ≠ multi-币种)、隔离级别差异(乐观锁 vs `SERIALIZABLE`)等非对称点。

## 决策

assets 域核心模型对齐 Stripe Ledger;演进分 4 期,与大厂对齐度逐期提升:

| 期 | 范围 | 对标 | 对齐度 |
|---|---|---|---|
| **P1 基础账本** | 5 表 Flyway + 资产注册 + 三阶段 API + 过期回收 | Stripe Ledger v1 | **85%** |
| **P2 多资产 + 对账 + 双扣** | 多腿事务 + DUAL + 兑换 + 三层对账 + outbox + 冻结分桶 + 限额 + RetryTemplate + 幂等撤销 | Stripe Ledger v2 + PayPal hold ledger | **90%** |
| **P3 分账/代收代付** | 多腿 splits + merchant 提现 + 平台分润 | Stripe Connect | **70%**(首期) |
| **P4 国际化** | 多币种 + 实时汇率 + Stripe-style `available_balance` 拆分 | Stripe Connect + PayPal multi-currency | **按需启动** |

## 与 Stripe Ledger 的差异

| 维度 | Stripe | 本 assets 域 | 差异原因 |
|---|---|---|---|
| **隔离级别** | `SERIALIZABLE` | `READ COMMITTED` + 乐观锁 | PG serializable 性能开销大,本方案并发量级(中等 SaaS 100w/日)乐观锁足够 |
| **资产发行** | Stripe Connect + Issuing | 资产注册中心 + issue leg | 同思想,实现路径不同 |
| **冻结模型** | `pending_balance` 派生列 | `ubmx_freeze` 明细表 + 聚合 | Stripe 用派生列性能好但审计弱,本方案明细表审计更强 |
| **多币种** | 原生 USD/EUR/GBP 等 | **不做**(国内合规优先) | 国际化非本期目标 |
| **审计** | Stripe Sigma + Dashboard | `@Auditable` AOP + outbox | 接入链路不同 |
| **幂等** | `Idempotency-Key` 全链路 | framework4j-idempotency + 表 `uk(ext_order_id)` | 同思路 |
| **退款** | Stripe Refunds API | PostingService 多腿退 + idempotency:replace | 多一个幂等撤销能力 |

## 与 Formance 的差异

| 维度 | Formance | 本 assets 域 | 差异原因 |
|---|---|---|---|
| **技术栈** | Go + Cloud | Java + PG | benefit4j 全栈 Java,跨语言成本高 |
| **Numscript DSL** | 有 | 无 | 中等规模自研足够,DSL 学习成本高 |
| **Wallets 模块** | 有 | 自研 `ubmx_account` | 同功能,实现路径不同 |
| **对账** | 内置 | scheduler 调 `assets_posting_reconcile_job` | 接入 PG cron 即可 |
| **分账** | `splits` 参数内置 | P3 迭代做(参考 ADR-0008) | 业务复杂度高,延后 |

## 关键非对称(不可消除)

| 非对称点 | 决策 | 理由 |
|---|---|---|
| 国内合规 vs 国际渠道 | 不做 multi-币种真法币 | 国内合规风险 > 业务价值;wallet 中台要做国际需另立项 |
| Java 乐观锁 vs PG serializable | 乐观锁 | PG serializable 性能低 30%-50%;中等规模乐观锁足够;如未来并发爆炸可局部升级 serializable |
| 冻结明细表 vs 派生列 | 明细表 | Stripe pending_balance 性能好但审计弱;明细表多 5% 性能开销换审计可追溯 |
| 资产域不内置 payments/wallets | 拆分 | Stripe Ledger 把账本 + wallet 做一起,但钱包中台与资产域的边界在我们的语境下更清晰 |

## 理由

- **对齐 Stripe/Formance 而非自造** → 借大厂多年验证的设计,降低试错成本
- **对齐度不是目标,可演进才是** → 每期都比上一期更接近大厂成熟方案
- **技术栈差异接受** → Java vs Go/Cloud 不强求一致,功能对齐即可
- **大表分区按 PG 原生** → Stripe 用 Shard,Formance 用 PG,我们也用 PG;DDL 沿用 V1.2.2 pg_pathman 模式无增量成本

## 后果

### ✅ 正面

- P1/P2 可与 Stripe Ledger 等同类比,招人 / Code Review 都有大厂参照
- 未来切到 Stripe Ledger / Formance 时数据模型可迁移(stub 替换,业务无感)
- 文档结构清晰,新人 onboarding 可顺 ADR-0008 + ADR-0009 + assets-design.md 一次性了解全貌

### ⚠️ 负面

- 与大厂差异点(冻结模型、隔离级别)需 ADR 记录,新人 onboarding 看 ADR-0009
- 大表分区按月,大厂按 shard;长期若单库 1 万亿行量级需重评估
- `SERIALIZABLE` 不开,极端并发(如热点账户 100w 并发扣减)有理论可能超发,需热点账户限速机制(framework4j-distributed-lock)兜底

### 📋 后续

- 季度 review:对比 Stripe / Formance 最新 release notes,补差距
- 热点账户压测:P2 后做 100w 并发同账户 CAS,验证乐观锁是否需要升级 serializable
- 长期(2027+):若接入国际渠道,启动 P4 多币种 + 汇率