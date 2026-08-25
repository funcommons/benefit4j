# 资产域(assets)· 开发计划

> **版本** v1.1 · **更新** 2026-08-25 · **状态** P1/P2 已完成(13 commit,141 IT 全绿,压测 1267 TPS) · **维护** benefit4j / assets 团队
>
> **修订记录** v1.1(2026-08-25): P1(A1-A10)/P2(B1-B8 + §5 压测)全部落地;B9 哈希链按 YAGLI 跳过;实现期勘误 3 处已回写设计文档(direction VARCHAR(3)/repay 方向/limit 方向键);遗留:M1 灰度(独立仓)、签名 403 端到端(IT 环境 signature.enabled=false)。
>
> **依据** [assets-design.md](./assets-design.md) v0.4(设计定稿:O1-O17 优化 + F1 授信唯一扩展 + 域定位「非周期资产,定义为元即简单钱包」)
>
> **关联** [ADR-0008](./adr/0008-assets-domain-independent.md) · [ADR-0009](./adr/0009-assets-stripe-ledger-parity.md) · [ADR-0007 PG 原生分区](./adr/0007-pg-native-partition-not-shardingsphere.md)

---

## 1. 总览

### 1.1 里程碑

| 里程碑 | 内容 | 估时 | 出口条件 |
|---|---|---|---|
| **P1 资产基础账本**(✅ 2026-08-25 完成) | 6 表 DDL + 资产注册 + 账户 + 复式记账引擎 + 三阶段扣费 + 过期回收 | **~5-7 天** | 单资产闭环端到端跑通,单测/IT 全绿 |
| **P2 多资产 + 对账 + 双扣 + 授信**(✅ 2026-08-25 完成,含 §5 压测 1267 TPS/零失败) | 多腿组合 + DUAL + 兑换 + 冻结 + 限额 + T+1 对账 + outbox + F1 授信 | **~6-8 天** | MMagiX 场景 2/3 可切换;恒等式 T+1 通过;授信负余额闭环 |
| **M1 MMagiX 灰度** | 新旧双跑 24h 对账 → 切流(独立于 P2 验收后启动) | ~3 天(含观察) | 双跑差异 = 0,切换完成 |
| **钱包中台** | 独立项目立项(JeePay 渠道/提现两段式/原路退/备付金) | 独立团队 | 不阻塞资产域任何里程碑 |

### 1.2 环境与依赖(开工前置)

| 项 | 要求 | 状态 |
|---|---|---|
| framework4j | ≥ v1.3.2(idempotency/distributed-lock/audit/scheduler) | ✅ 已接入(tracelog 同版) |
| PG | 17(月分区沿用 V1.2.2 模式;月初建分区 cron 复用现有) | ✅ pg-17 容器 |
| Flyway 基线 | 现网最新 V1.2.x,assets 占用 V1.3.0~V1.3.5 | ⚠️ 合并前确认无版本号冲突 |
| Redis | 限流/分布式锁/缓存 L2 | ✅ |
| 种子资产 | `POINTS`/`GOLD`/`COMPUTE`(VIRTUAL)随 Flyway 种子;`CNY` 仅预置定义,`assets.fiat-allowed=false` 启动拦截 | 设计时已定 |
| 业务口径 | 每天 1~100w 条余额变更(均值 ~12 TPS,突发 ~120 TPS)——单实例行锁足够,不做异步入账 | 设计 §8.1 |

---

## 2. P1 资产基础账本(~5-7 天)

### 2.1 任务分解(WBS)

| # | 任务 | 产出 | 依赖 | 估时 |
|---|---|---|---|---|
| A1 | 环境与基线确认 | Flyway 版本号占位确认;`benefit4j-it` 测试库 schema 基线 | - | 0.5d |
| A2 | 6 表 DDL + 种子 | `V1.3.0__asset` / `V1.3.1__account`(含 `account_type`/`credit_limit` 列)/ `V1.3.2__posting`(月分区+default)/ `V1.3.3__tx_order` / `V1.3.4__pre_consume`(含 `PARTIAL_SETTLED`)/ `V1.3.5__freeze`;种子 3 个 VIRTUAL 资产 | A1 | 1d |
| A3 | 实体 + Mapper | 6 实体(雪花 ID,`@TableName`)+ `UbmxTxOrderMapper`(`INSERT ... ON CONFLICT` 抢占)+ `UbmxPostingMapper` 批插 | A2 | 0.5d |
| A4 | AssetRegistryService | 资产 CRUD + `can_*` 能力校验 + `expire_policy` 解析 + FIAT 启动 fail-fast(`BenefitAssetsAutoConfiguration`) | A3 | 1d |
| A5 | AccountService | lazy 开户(`ON CONFLICT DO NOTHING` + 重读)+ 余额/流水查询(§4.2) | A3 | 0.5d |
| A6 | **PostingService 引擎(核心)** | O10 幂等抢占协议 → 腿解析(识别 `BOUNDARY`)→ O12 `FOR UPDATE ORDER BY id` 只锁 NORMAL 户 → 校验 `balance + credit_limit >= amount` → 更新+写 posting;RetryTemplate(仅 `DeadlockLoser`/`CannotAcquireLock`,50/200/800ms) | A3-A5 | 2d |
| A7 | PreConsumeService 三阶段 | `pre-consume`/`settle`(diff 回补 + `PARTIAL_SETTLED` 差错池)/`refund`;`WHERE status='RESERVED'` guard;`AssetsExpireScheduler` 过期回收(分布式锁单实例) | A6 | 1d |
| A8 | Controller + 异常体系 | runtime 域 `@RequiresSignature`(issue/pre-consume/settle/refund)+ OPS 域(资产 CRUD)+ 错误码映射(`INSUFFICIENT_BALANCE`/`IDEMPOTENCY_CONFLICT`/`LIMIT_EXCEEDED`/`INSUFFICIENT_PRIVILEGE` → 4xx 语义码) | A4-A7 | 1d |
| A9 | 单测 + IT | §9.1/§9.2 用例:单腿/多腿原子/100 线程并发无超发/10 线程互转无死锁/幂等重放 N 次响应一致/过期回收 | A6-A8(与开发同步写) | (并行) |
| A10 | 端到端验收 | §9.3 六步:issue 100 → 组合消费 → 退款 → 双扣(P2 前可跳过)→ 余额不足触发 CHECK → FIAT fail-fast | 全部 | 0.5d |

> A2 说明:**6 张表一次性随 P1 建齐**(freeze/tx_order 建表无害),P2 零 DDL 迁移即可启用功能;`credit_limit`/`can_credit`/`account_type` 均为默认关闭的预留列,P1 行为与设计基线完全一致。

### 2.2 P1 验收清单(DoD)

- [x] `mvn test` 全绿(基线 60 → 141)
- [x] §9.3 端到端六步全过(AssetsEndToEndIT,④双扣已随 P2 补齐)
- [x] 100 线程无超发守恒 + DB CHECK 直写被拒(AssetsConcurrencyIT)
- [x] 同键 5 连发同 txId 只记一次 + 异参冲突(E2E + PostingServiceIT)
- [x] 640 笔并发 issue 共享同一边界户全成功且 balance 恒 0
- [x] FIAT fail-fast 三分支(AssetRegistryServiceIT + E2E)
- [x] @Auditable 12 写端点;⚠️ 签名 403 受 IT signature.enabled=false 限制未端到端验证(注解链与既有 runtime 同款)
- [x] 分区 202608-202610 + default(schema IT);月初 cron 属运维上线检查项

---

## 3. P2 多资产 + 对账 + 双扣 + 授信(~6-8 天)

### 3.1 任务分解(WBS)

| # | 任务 | 产出 | 依赖 | 估时 |
|---|---|---|---|---|
| B1 | 多腿组合支付 | 多腿事务(积分抵 + CNY + fee 三腿原子,§3.2)+ `:dual` 双账户预扣(§3.3 定案版) | P1 | 1.5d |
| B2 | 兑换 EXCHANGE | `exchange:*` 边界户双腿;汇率由调用方定价,资产域只记账 | B1 | 0.5d |
| B3 | 冻结功能启用 | `freeze`/`unfreeze` API + `ubmx_freeze` 生命周期(ACTIVE/RELEASED/CONSUMED/EXPIRED,部分释放 `used_amount`)+ O14 一致性(`frozen` 缓存 vs Σfreeze) | P1 | 1d |
| B4 | limit_policy 限额 | 单笔/日累计/月累计校验(Asia/Shanghai 营业日);FIAT 强制配置校验 | P1 | 1d |
| B5 | T+1 对账 | `AssetsReconcileScheduler`(02:00,分布式锁):恒等式(**边界户口径**:用户域 Σbalance = 边界户流出−流入)+ 冻结一致性 + `ubmx_balance_snapshot` 快照(O16)+ 差错池 API | B1-B3 | 1.5d |
| B6 | outbox 事件 | posting 事务内写 outbox(issue/consume/refund 事件,含 tx_id+leg_seq,消费端幂等) | B1 | 0.5d |
| B7 | **F1 授信负余额** | OPS `ADJUST` 调额(双签审计)+ 还款分录(`user → credit:*` 边界户)+ 负余额禁提现/转账联动 | B1 | 1.5d |
| B8 | 幂等键撤销 | `POST /assets/runtime/idempotency:replace`(仅 FAILED 可撤,OPS + 签名,审计) | P1 | 0.5d |
| B9 | 哈希链防篡改(可选) | posting `prev_hash/hash` 列(默认关,配置开启)+ 对账验链 | B5 | 0.5d |

### 3.2 P2 验收清单(DoD)

- [x] DUAL diff 双侧回补 + guard 竞态(DualAndExchangeIT / PreConsumeServiceIT)
- [x] 三腿原子 + 不足全回滚(PostingServiceIT)
- [x] 兑换双腿原子 + 能力拒绝(DualAndExchangeIT)
- [x] 限额单笔/日/月 + out 方向键(AssetsLimitIT;日界翻转靠 Asia/Shanghai 计算口径)
- [x] 授信下限闭环 + can_credit 校验 + 负余额禁冻结(CreditIT;双签为后续项)
- [x] 恒等式篡改暴露→差错池→恢复 + O14 全量 + 快照(ReconcileAndOpsIT)
- [x] frozen 篡改暴露(FreezeServiceIT + ReconcileAndOpsIT)
- [x] 事务内 outbox 落库验证;消费端属订阅方职责(事件含 txId)

---

## 3.5 P3 运营前端(v1.2 追加,2026-08-26)

| # | 任务 | 说明 |
|---|---|---|
| C1 | OPS 查询端点补齐 | 流水/账户查询原仅在签名 runtime 域,平台端浏览器不可达;补 `GET /assets/ops/postings`、`GET /assets/ops/accounts`(OPS token) |
| C2 | api/benefitAssets.ts | 仿 benefitItem.ts 范式(snake_case) |
| C3 | Assets.vue(平台端) | 资产注册列表/新建/停启用(can_* 能力位展示) |
| C4 | AssetPostings.vue | 流水查询(账户引用+资产,分页) |
| 验证 | typecheck + 既有 e2e 无回归 | 禁区 dev/sdk 不触 |

## 4. MMagiX 灰度迁移(M1,P2 验收后)

| 步骤 | 内容 | 通过条件 |
|---|---|---|
| M1.1 | `CreditApiAdapter` 双写:旧 credit 模块 + assets runtime 并行(影子模式,旧为准) | 影子期不影响线上计费 |
| M1.2 | 双跑对账 24h:逐笔比对 `mmxt_credit_*` vs `ubmx_posting` | 差异 = 0 |
| M1.3 | 切流:assets 为准,旧模块保留回滚窗口 72h | 观察无告警 |
| M1.4 | 存量迁移:`mmxt_credit_account` → `ubmx_account`(NUMERIC(18,4)→(20,4) 兼容),停旧模块 | 迁移脚本双向可核对 |

> 注意:精度迁移脚本与切流分开执行(先切流量后迁存量),避免单窗口大事务。

---

## 5. 测试与 CI 门禁

- 单测/IT 按 §9(设计文档)执行,P1/P2 的 DoD 即 CI 出口
- 新增门禁:assets 相关 IT 纳入现有 `benefit4j-it` 套件,不另起模块
- 压测(P2 末):按业务口径峰值 120 TPS 单实例压 30min,观察重试率 < 5%、死锁 = 0、P99 < 50ms(参考值,超限再评估 O11 之外的优化)

## 6. 风险与依赖(开发期特有)

| 风险 | 处置 |
|---|---|
| Flyway V1.3.x 与并行分支版本号冲突 | A1 占位确认;冲突时跳号(如 V1.3.10 起) |
| 月分区 cron 在 P1 上线前跨月 | 上线检查清单含「确认下月分区存在」;兜底 default 分区已建 |
| MMagiX NUMERIC(18,4)→(20,4) 迁移 | 双向核对脚本 + 影子期验证,见 M1.4 |
| PostingService 复杂度集中(A6) | 拆 `IdempotencyGate`/`AccountLocker`/`LegExecutor` 三个内部组件,单测各自覆盖 |
| 授信与提现联动(B7)依赖钱包中台接口未定 | 联动只做「禁提现标记 + 错误码」,不依赖钱包中台存在 |

## 7. 明确不做(边界对齐设计定稿)

周期发放(F2)/超额转按量(F3)/账期月结(F4)/账户组(F5)/批次三元组(lot)/user 级异步入账合并/多币种/计息 —— 理由与替代方案见设计 §11.1/§11.2;开发期内不接受范围外需求,新增先改设计文档再排期。

---

**评审要点**:
1. P1/P2 边界是否合理?(6 表随 P1 建齐、freeze/授信功能留 P2)
2. MMagiX 灰度「先切流后迁存量」顺序是否接受?
3. 估时(5-7 + 6-8 天)按单人全职口径,多人并行时 A6/A7 与 A4/A5 可压 1-2 天,是否需要并行排期?
