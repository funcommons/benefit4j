# 资产域(assets)· 详细设计

> **版本** v0.4 · **更新** 2026-08-25 · **状态** 设计待评审 · **维护** benefit4j / assets 团队
>
> **范围** 本文覆盖 benefit4j 的 **assets 域**: 资产注册、多资产账户、复式分录、TCC 预扣/结算/退款、过期回收、三层对账、合规开关、反洗钱限额、幂等键撤销。
>
> **明确不做** 真实资金渠道接入(充值/提现/原路退)。这部分留给未来独立的「钱包中台」项目承担——钱包中台对接 JeePay 等渠道,完成入账后调资产域的内部 issue API 写账本。详见 §1.3 关系图。
>
> **关联文档**
> - 多资产钱包中台——账户与账务详细设计(最终版)(上一级方案)
> - [benefit4j 产品说明书](./产品说明书.md) — 既有权益中台模型(被本设计引用)
> - [ADR-0007: PG 原生分区替代 ShardingSphere](./adr/0007-pg-native-partition-not-shardingsphere.md)
> - [ADR-0008: 新建 assets 域独立于权益桶 + 独立于钱包中台](./adr/0008-assets-domain-independent.md)
> - [ADR-0009: assets 域演进路径 —— 与 Stripe Ledger 对标](./adr/0009-assets-stripe-ledger-parity.md)
> - [assets 开发计划](./assets-dev-plan.md) — P1/P2 WBS、验收 DoD、MMagiX 灰度 M1

---

## 1. 背景与动机

### 1.1 现有权益中台的边界

benefit4j 现有权益模型基于**配额桶(`ubma_subscribe_item`)**: 单桶 = (订阅 × 权益项 × 来源类型),整数 `quotaLimit/periodConsumed/frozenConsumed`,乐观锁 CAS,TCC 预扣/结算/退款,过期回收。该模型在算力点/积分/调用次数等**计量型**场景验证成熟。

### 1.2 资产型场景的需求差异

现接入方出现**余额型**需求(钱包/储值卡/多资产钱包/支付与账务):

| 维度 | 配额型(权益中台现有) | 资产型(本期) |
|---|---|---|
| 精度 | 整数(`Integer`) | 小数(`NUMERIC(20,4)`) |
| 资源 | 桶 = 订阅明细,依附订阅生命周期 | 账户 = (主体 × 资产),独立,长期存在 |
| 资产种类 | 单次只能销同一权益项 | **多资产组合**支付(积分抵5 + CNY 23 + 费2) |
| 会计视图 | 桶内 `periodConsumed` 累计 | **复式分录**流水,资产恒等式 Σbalance = Σissue − Σburn |
| 监管 | 不碰资金,合规风险极低 | **资金自持**(若启用 FIAT)触及二清,需前置合规评估 |
| 对账 | 不需要(权益无外部账实) | **三层对账**(渠道/账实/资产),**防超发** |

强行复用 `subscribe_item` 会让语义靠口头约定(永不过期/不刷新/不与订阅关联),资金安全无 schema 兜底——本文档论证后**新建独立 assets 域**,不复用桶表。

> **定位锚点**: assets = **非周期性资产**。凡带「周期刷新/刷新窗口」语义的权益归权益域(`refresh_cycle`);凡「一次性、可耗尽、可退回」的余额型价值归资产域。**当资产定义为「元」(`asset_code='CNY'`, precision=2)时,资产域即为一个简单钱包系统**——issue(充值)/pre-consume→settle(扣费)/refund(退款)三阶段天然对应计费 Saga,可直接支撑 MMagiX 类按量计费,无需钱包中台介入(钱包中台仅在需要真实资金渠道时介入,见 §1.3)。

### 1.3 与未来钱包中台的关系(关键架构图)

```
┌──────────────────────────────────────────┐     真实资金渠道      ┌─────────────────┐
│              钱包中台(独立项目)            │ ◄──── JeePay/银联 ───► │ 微信/支付宝/银行 │
│  - 充值/提现/原路退                         │                       └─────────────────┘
│  - 备付金存管                               │                                │
│  - 渠道对账                                 │                                ▼
│  - 钱包中台本身是资产域的 CNY "外部户"       │              ┌────────────────────────────┐
└──────────────────┬────────────────────────┘              │                            │
           调内部 issue API                                   ▼                            ▼
           (写 world:{channel} → user leg)   ┌─────────────────────────┐   ┌──────────────────────┐
                                              │   assets 域(本期)         │   │ 第三方计费系统(如     │
                                              │  - 资产注册中心             │   │ MMagiX)              │
                                              │  - 多资产账户/分录          │   │                      │
                                              │  - 三阶段预扣/结算/退款     │   │                      │
                                              │  - 多资产组合/兑换          │   │                      │
                                              │  - 三层对账                 │   │                      │
                                              │  - 合规开关                 │   │                      │
                                              └─────────────────────────┘   └──────────────────────┘
                                                       ▲                                    ▲
                                                       │ runtime 域 API(HMAC 签名)            │ runtime 域 API
                                                       └────────────────────────────────────┘
```

**核心约定**:
- **资产域只管账本**,不管钱怎么进来;CNY 资金物理进出由钱包中台经支付渠道完成
- **钱包中台对资产域而言是一个 subject**——它通过内部 `POST /assets/internal/issue`(资产域内部 API)完成入账,等同于其他业务系统的入口调用
- 资产域对钱包中台不感知、不耦合——钱包中台可以替换或迁移,资产域 schema 不变

### 1.4 设计目标

- **多资产**: 资产码作为主键,新增资产 = INSERT 元数据一行,**零 schema 迁移**
- **复式记账**: 资产恒等式由事务原子保障,任意时刻 Σ余额 = Σ来源 − Σ去向
- **原子扣减**: 行锁(`FOR UPDATE ORDER BY id`)+ DB CHECK 兜底(O12 定案,弃纯 CAS 双轨)
- **TCC 预扣**: 沿用现有预扣单 + 过期回收 scheduler
- **多资产组合**: 多腿事务单 SQL,腿间金额一致
- **三层对账**: 渠道账实 + 资产恒等 + 长短款差错池
- **与未来钱包中台解耦**: 钱包中台独立项目,资产域不依赖其存在

---

## 2. 核心数据模型(6 张表 + P2 快照表,PG)

> 表前缀 `ubmx_`(`ubm` + `x` = eXtension/账本),与现有 `ubma_*`(account)、`ubmp_*`(platform template) 区分。索引遵循 `idx_*/uk_*` 规范,JSONB 必带 GIN 索引。

### 2.1 `ubmx_asset` 资产定义(资产注册中心)

```sql
CREATE TABLE ubmx_asset (
    code            VARCHAR(32)  PRIMARY KEY,        -- 'CNY' | 'POINTS' | 'GOLD' | 'COMPUTE' | 'SCORE'
    name            VARCHAR(64)  NOT NULL,           -- 中文名 "积分"
    asset_type      VARCHAR(8)   NOT NULL            -- FIAT | VIRTUAL
                    CHECK (asset_type IN ('FIAT','VIRTUAL')),
    precision       SMALLINT     NOT NULL DEFAULT 0  -- CNY=2, 积分=0, 算力=4
                    CHECK (precision BETWEEN 0 AND 6),
    can_recharge    BOOLEAN      NOT NULL DEFAULT FALSE,  -- 渠道入账(钱包中台调用)
    can_withdraw    BOOLEAN      NOT NULL DEFAULT FALSE,  -- 仅 CNY = TRUE
    can_pay         BOOLEAN      NOT NULL DEFAULT TRUE,
    can_transfer    BOOLEAN      NOT NULL DEFAULT FALSE,  -- 转账风控,首期关
    can_exchange    BOOLEAN      NOT NULL DEFAULT TRUE,   -- 虚拟资产可相互兑换
    can_credit      BOOLEAN      NOT NULL DEFAULT FALSE,  -- F1: 允许授信负余额(列随 P1 落,能力 P2 实现,§11.2)
    issue_mode      VARCHAR(32),                      -- RECHARGE | GIFT | ACTIVITY | EXCHANGE
    expire_policy   JSONB,                             -- {"strategy":"NEVER"|"FIXED"|"ROLLING"}
    -- O3: 反洗钱/限额策略(FIAT 资产强制配,VIRTUAL 推荐配)
    -- 例:{"singleMax":"10000.00","dailyMax":"50000.00","monthlyMax":"200000.00"}
    -- 校验时机: PostIssue/PostPreConsume 前置校验当前累计值
    -- 口径: 日/月累计按 Asia/Shanghai 营业日(法币合规要求,不取服务器时区)
    -- 方向: 扁平键 = 入账侧(AML 充值限额);出账侧用 {"out":{...}} 显式分组,未配 out 不拦(P2 实现)
    limit_policy    JSONB,
    description     VARCHAR(500),
    status          VARCHAR(8)   NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','SUSPEND')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ubmx_asset_type ON ubmx_asset(asset_type) WHERE status = 'ACTIVE';
```

**关键约束**:
- `code` 是字符串主键,**全局唯一**;新增资产 = `INSERT ubmx_asset` 一行,账本零 schema 改动
- `asset_type='FIAT'` 的行需在启动时校验 `assets.fiat-allowed=true` 环境变量(资金自持合规开关),否则**启动 fail-fast**
- `can_*` 布尔位组合 → 资产能力矩阵(转账/兑换/支付在 API 层按位强校验,拒绝时返回明确 `INSUFFICIENT_PRIVILEGE`)
- `expire_policy` JSONB:
  - `{"strategy":"NEVER"}` —— 余额类
  - `{"strategy":"FIXED","durationDays":365}` —— 固定天数过期
  - `{"strategy":"ROLLING","windowDays":365,"refreshOnUse":true}` —— 滚动续期(消费即重置窗口)
- 预留 GIN 索引位(本期不建索引,`expire_policy` 查询由 scheduler 单字段过滤覆盖)

### 2.2 `ubmx_account` 账户(主体 × 资产)

```sql
CREATE TABLE ubmx_account (
    id              BIGINT       PRIMARY KEY,        -- 雪花 ID
    app_id          BIGINT       NOT NULL,            -- 多租户隔离
    owner_type      VARCHAR(8)   NOT NULL
                    CHECK (owner_type IN ('USER','TENANT','MERCHANT','PLATFORM','EXTERNAL')),
    owner_id        BIGINT       NOT NULL,            -- userId / tenantId / merchantId
                                                    -- EXTERNAL 类型 owner_id = 钱包中台 ID(内部约定)
    asset_code      VARCHAR(32)  NOT NULL,            -- 一致性应用层保证(禁外键,DB 铁律)
    account_type    VARCHAR(8)   NOT NULL DEFAULT 'NORMAL'
                    CHECK (account_type IN ('NORMAL','BOUNDARY')),   -- O11: 边界户,见关键约束
    balance         NUMERIC(20,4) NOT NULL DEFAULT 0,
    credit_limit    NUMERIC(20,4) NOT NULL DEFAULT 0, -- F1: 授信额度(列随 P1 落,P2 启用授信业务)
    frozen          NUMERIC(20,4) NOT NULL DEFAULT 0
                    CHECK (frozen >= 0),
    version         INTEGER      NOT NULL DEFAULT 0,  -- 乐观锁 CAS
    status          VARCHAR(8)   NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
    ext             JSONB,                             -- 业务侧透传(资产级 ext)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, owner_type, owner_id, asset_code),
    -- 资金安全兜底(O11/F1): 边界户不约束;普通户非负;授信户允许负至 -credit_limit
    CHECK (account_type = 'BOUNDARY' OR balance >= -credit_limit)
);
CREATE INDEX idx_ubmx_account_owner ON ubmx_account(app_id, owner_type, owner_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_ubmx_account_ext_gin ON ubmx_account USING GIN (ext);
```

**关键约束**:
- **唯一键保证账户唯一**:`(app_id, owner_type, owner_id, asset_code)` 不可重复开户
- **资金安全 CHECK**: 普通户 `balance >= -credit_limit`(默认 `credit_limit=0` 即非负)——DB 兜底防超发,**这是资金安全的最后一道闸**
- **`BOUNDARY` 边界户**(O11): `world:*` / `issue:*` / `fee:*` / `exchange:*` / `credit:*` 等虚拟边界户——**不参与 FOR UPDATE 锁、不更新 balance**(恒 0),只写 posting。边界户是全平台单行热点(所有充值都打 `world:wechat` 同一行),豁免后充值/发放 TPS 不再被一行锁限死;资产恒等式对账口径改为「用户域 Σbalance = Σ边界户流出 − Σ边界户流入」
- `version` 沿用 `@Version` 乐观锁;CAS UPDATE 自带 `AND version = ?`,重试模式同 `UbmaSubscribeItem`
- **`EXTERNAL` 类型**专给钱包中台等外部系统开户使用(资产域对其记账但不知其内部状态);钱包中台对资产域而言就是一个外部账户 owner
- `ext` JSONB GIN 索引预建(本期不查,留给运营维度未来扩展)
- **不开户** = 无 `ubmx_account` 行;首次充值/发放时 lazy 创建(单语句 `INSERT ... ON CONFLICT DO NOTHING` + 重新读)

### 2.3 `ubmx_posting` 复式分录(append-only)

```sql
CREATE TABLE ubmx_posting (
    id              BIGINT       NOT NULL,
    app_id          BIGINT       NOT NULL,
    tx_id           BIGINT       NOT NULL,            -- 业务交易号(同一笔业务多腿共享)
    tx_type         VARCHAR(32)  NOT NULL,            -- ISSUE | CONSUME | REFUND | TRANSFER | EXCHANGE | ADJUST | FREEZE | UNFREEZE
    ext_order_id    VARCHAR(64)  NOT NULL,            -- 幂等键(同订单同动作幂等)
    leg_seq         SMALLINT     NOT NULL,            -- 同 tx 的腿序号(0,1,2...)
    src_account_id  BIGINT       NOT NULL,            -- 一致性应用层保证(禁外键,DB 铁律)
    dst_account_id  BIGINT       NOT NULL,
    asset_code      VARCHAR(32)  NOT NULL,
    amount          NUMERIC(20,4) NOT NULL
                    CHECK (amount > 0),               -- 金额恒正,方向由 leg 含义区分
    direction       VARCHAR(3)   NOT NULL
                    CHECK (direction IN ('IN','OUT')),
    balance_after   NUMERIC(20,4),                      -- 该腿记账后 src/dst 余额(便于追溯)
    status          VARCHAR(8)   NOT NULL DEFAULT 'INIT'
                    CHECK (status IN ('INIT','SUCCESS','FAILED')),
    ext             JSONB,                             -- 业务侧透传
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
-- O10: 不在分区表上建 (app_id, ext_order_id, leg_seq) 唯一键——PG 要求分区表唯一约束必须含
-- 分区键,带上 created_at 会稀释幂等(同订单跨日重放仍会成功,资金级缺陷)。
-- 幂等闸移至 §2.7 ubmx_tx_order(不分区小表),posting 侧只留普通查询索引。
-- 当前月 + ���认分区(沿用 V1.2.2 模式,后续 cron 月初自动建下月分区)
CREATE TABLE ubmx_posting_202608 PARTITION OF ubmx_posting
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE ubmx_posting_default PARTITION OF ubmx_posting DEFAULT;

CREATE INDEX idx_ubmx_posting_tx          ON ubmx_posting(tx_id);
CREATE INDEX idx_ubmx_posting_src         ON ubmx_posting(app_id, src_account_id, created_at DESC);
CREATE INDEX idx_ubmx_posting_dst         ON ubmx_posting(app_id, dst_account_id, created_at DESC);
CREATE INDEX idx_ubmx_posting_order       ON ubmx_posting(app_id, ext_order_id);
CREATE INDEX idx_ubmx_posting_asset_time  ON ubmx_posting(asset_code, created_at DESC);  -- O8: 对账按资产切片更快
CREATE INDEX idx_ubmx_posting_ext_gin     ON ubmx_posting USING GIN (ext);
```

**关键约束**:
- **append-only**: 不允许 UPDATE/DELETE;唯一合法变更 = `INIT → FAILED` 幂等撤销标记(O6),`SUCCESS` 后永不变。分录在事务内直接以 `SUCCESS` 写入(INIT 仅供撤销流程使用)
- `leg_seq` 是分录序号,一笔业务 N 条腿共享 `tx_id`;幂等由 `ubmx_tx_order` 表 `UNIQUE (app_id, ext_order_id)` 兜底(O10),posting 上 `idx_ubmx_posting_order` 仅作查询索引
- 资产恒等式:每条腿的 `src_account` 与 `dst_account` 都属于 `asset_code`,**单事务强校验**
- `tx_type` 大类: 入账 / 消费 / 退款 / 转账 / 兑换 / 调账 / 冻结 / 解冻,对应资产流向
  - **`tx_type='ISSUE'`** 专给钱包中台等渠道入账使用(任何调 issue 内部 API 的腿)
- `balance_after` 字段给流水展示/对账,不必查 `ubmx_account` 反推(性能)

### 2.4 `ubmx_pre_consume` 预扣单(TCC 模式)

```sql
CREATE TABLE ubmx_pre_consume (
    id              BIGINT       PRIMARY KEY,        -- 雪花 ID(事务表规范,非 serial)
    app_id          BIGINT       NOT NULL,
    request_id      VARCHAR(64)  NOT NULL,            -- 调用方幂等键
    tx_id           BIGINT       NOT NULL,            -- 对应的 posting 主交易号(预扣腿已写入)
    charge_mode     VARCHAR(8)   NOT NULL
                    CHECK (charge_mode IN ('SOLO','DUAL')),
    user_account_id     BIGINT,
    tenant_account_id   BIGINT,                      -- 一致性应用层保证(禁外键)
    asset_code      VARCHAR(32)  NOT NULL,
    estimated       NUMERIC(20,4) NOT NULL,           -- 预扣估算额
    settled_amount  NUMERIC(20,4),                    -- 结算时回填实际
    status          VARCHAR(16)  NOT NULL DEFAULT 'RESERVED'
                    CHECK (status IN ('RESERVED','SETTLED','PARTIAL_SETTLED','REFUNDED','EXPIRED')),
                    -- PARTIAL_SETTLED(O15): actual > estimated 且补扣余额不足 → 差额入差错池,终态放行
    expire_time     TIMESTAMPTZ  NOT NULL,            -- 默认 NOW() + 30min,scheduler 扫描过期
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, request_id)
);
CREATE INDEX idx_ubmx_pre_consume_expire
    ON ubmx_pre_consume(expire_time)
    WHERE status = 'RESERVED';                       -- scheduler 部分索引
```

**关键约束**:
- `RESERVED` 状态时 `account.frozen += estimated`(frozen 是**缓存聚合**,主数据源为 `ubmx_freeze`,见 §2.5/O14)
- `SETTLED` / `REFUNDED` / `EXPIRED` 都是终态,触发相应 posting(确认/退款)
- `charge_mode=DUAL` 表示 user + tenant 双账户同时预扣同额,**单事务原子**(接 MMagiX 场景 2/3)
- `expire_time` 默认 30 min,scheduler 兜底回收防悬挂(沿用现有 `ReserveTimeoutScheduler` 模式)
- **终态竞态 guard(O15)**: 过期回收与 settle 的 UPDATE 均带 `WHERE status='RESERVED'`,先抢到者赢;后到方读终态按幂等语义原样返回,不二次变更

### 2.5 `ubmx_freeze` 冻结明细表(冻结粒度细分,O5)

> **问题**: `account.frozen NUMERIC` 是单一聚合字段,无法区分"提现冻结"vs"售后冻结"vs"风控冻结"——大厂(Stripe/PayPal)冻结按原因分桶,审计与释放更精细。

```sql
CREATE TABLE ubmx_freeze (
    id              BIGINT       PRIMARY KEY,        -- 雪花 ID
    app_id          BIGINT       NOT NULL,
    account_id      BIGINT       NOT NULL,           -- 一致性应用层保证(禁外键)
    freeze_no       VARCHAR(64)  NOT NULL,            -- 业务冻结单号(幂等键)
    reason          VARCHAR(32)  NOT NULL             -- WITHDRAW | AFTER_SALE | RISK | PRE_CONSUME | OTHER
                    CHECK (reason IN ('WITHDRAW','AFTER_SALE','RISK','PRE_CONSUME','OTHER')),
    amount          NUMERIC(20,4) NOT NULL
                    CHECK (amount > 0),
    used_amount     NUMERIC(20,4) NOT NULL DEFAULT 0, -- 已使用部分(部分解冻/扣减后减少)
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','RELEASED','CONSUMED','EXPIRED')),
    expire_time     TIMESTAMPTZ,                      -- 可选,自动过期
    ext             JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, freeze_no)
);
CREATE INDEX idx_ubmx_freeze_account ON ubmx_freeze(account_id) WHERE status = 'ACTIVE';
```

**关键约束**:
- **冻结单一数据源(O14)**: 主数据 = `ubmx_freeze`(`SUM(amount - used_amount) WHERE status='ACTIVE'`);`account.frozen` 降级为同事务同步更新的**缓存聚合**,仅供展示与快速校验。对账 job 每日校验 `account.frozen == SUM(...)`,不一致告警并以 `ubmx_freeze` 为准修复
- 冻结生命周期: `ACTIVE → RELEASED` (全额释放)/ `CONSUMED` (被消费掉,如提现成功)/ `EXPIRED` (scheduler 过期)
- 单笔冻结可**部分释放**:`used_amount` 跟踪已用,amount - used_amount = 剩余可释放额
- 冻结原因为审计与反洗钱依据(如大额冻结全部有 reason,可追溯)

### 2.6 `ubmx_posting` 改: 取消 `balance_after` 改为游离列(可选)

> 原 v0.2 把 `balance_after` 当流水账的快照。v0.3 改用更明确语义:**`balance_after` 仅做对账一致性校验**,不影响核心逻辑。保留即可,不优化。

### 2.7 `ubmx_tx_order` 幂等闸(O10)

> **问题(v0.3 评审 D1)**: posting 是分区表,PG 要求分区表唯一约束必须包含分区键——v0.3 的 `UNIQUE (app_id, ext_order_id, leg_seq, created_at)` 中 `created_at` 稀释了幂等:**同一订单跨日重放(网络重试/分区轮换后)仍会再次成功**。资金级缺陷。
> **修复**: 幂等闸移到独立不分区小表,posting 只留普通查询索引。

```sql
CREATE TABLE ubmx_tx_order (
    id              BIGINT       PRIMARY KEY,        -- 雪花 ID
    app_id          BIGINT       NOT NULL,
    ext_order_id    VARCHAR(64)  NOT NULL,           -- 调用方幂等键(issueOrderId / requestId)
    tx_type         VARCHAR(32)  NOT NULL,           -- ISSUE | CONSUME | REFUND | ...
    tx_id           BIGINT       NOT NULL,           -- 抢占成功后分配的业务交易号
    status          VARCHAR(8)   NOT NULL DEFAULT 'SUCCESS'
                    CHECK (status IN ('SUCCESS','FAILED')),
    result_snapshot JSONB,                           -- 首次成功响应快照(重放时原样返回)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, ext_order_id)                    -- 真正的幂等闸(不分区,永不过期)
);
```

**抢占协议**(PostingService 事务第一步,单事务内):
1. `INSERT ubmx_tx_order` → 成功 = 首次请求,继续记账
2. 唯一冲突 → 读旧行:**同参数**返回 `result_snapshot`(幂等重放);**异参数**抛 `IDEMPOTENCY_CONFLICT`(Stripe `idempotency_error` 同思路,防幂等键被挪用)
3. `FAILED` 行可经 `idempotency:replace`(§4.3.2)释放后重用

---

## 3. 复式记账引擎(单笔事务原子)

> **核心思想**: 一笔业务 = N 条腿(原子),腿间 amount 一致(双扣场景)。任一腿失败 → 整笔回滚(由 DB 事务保证,非应用层补偿)。

### 3.1 分录模型

```
PostingService.commit(LegSpec...):
  legs = [
    {src: "world:wechat", dst: "user:{uid}", asset: "CNY", amount: 100},
    ...
  ]

  BEGIN
    // 0. 幂等抢占(O10): INSERT ubmx_tx_order(app_id, ext_order_id)
    //    唯一冲突 → 同参返回 result_snapshot; 异参抛 IDEMPOTENCY_CONFLICT

    // 1. 解析腿为账户 ID (lazy 开户; 识别 account_type='BOUNDARY' 边界户)
    for leg in legs:
      srcAcc = resolveAccount(leg.src)
      dstAcc = resolveAccount(leg.dst)

    // 2. 只锁普通账户, 按 id 升序防死锁; 边界户(O11)不锁、不更新 balance
    normalIds = [src1, dst1, src2, ...].filter(NORMAL).distinct().sort()
    SELECT ... FROM ubmx_account WHERE id IN (normalIds) ORDER BY id FOR UPDATE

    // 3. 校验余额 (授信户覆盖到 -credit_limit)
    for each leg where src is NORMAL:
      if srcAcc.balance + srcAcc.credit_limit < amount:
        throw INSUFFICIENT_BALANCE

    // 4. 更新账户 + 写 posting (version 自增仅作审计, 不参与 WHERE 条件)
    for each leg:
      if src is NORMAL: UPDATE ubmx_account SET balance = balance - amount, version = version + 1 WHERE id = ?
      if dst is NORMAL: UPDATE ubmx_account SET balance = balance + amount, version = version + 1 WHERE id = ?
      INSERT INTO ubmx_posting (...) VALUES (..., 'SUCCESS')   // 含边界户腿

  COMMIT
  // 任一异常 → ROLLBACK, 整笔回滚,无副作用
```

> **锁策略定案(O12)**: 主路径 = `FOR UPDATE ORDER BY id` 行锁(悲观)。事务持有行锁期间 version 不会变化,故 `version` 自增**仅作审计**,不参与更新条件;RetryTemplate 只兜死锁 victim 与锁超时(§3.4)。v0.3 的「行锁 + CAS 双轨」已删除——两者混用语义矛盾,且行锁路径下 CAS 分支永不触发。

### 3.2 典型场景分录

| 业务场景 | 分录 |
| | |
| **钱包中台充 100 CNY(微信回调后写入)** | `world:wechat` → `user:{uid}:CNY`, CNY 100 (单腿,wallet 后台调 issue 内部 API) |
| **消费 30 元: 5 POINTS 抵 + 23 CNY + 2 CNY 手续费** | 3 腿: <br>① `user:{uid}:POINTS` → `issue:POINTS`, POINTS 5 <br>② `user:{uid}:CNY` → `merchant:{mid}:CNY`, CNY 23 <br>③ `user:{uid}:CNY` → `fee:CNY`, CNY 2 |
| **退余额(默认)** | `merchant:{mid}:CNY` → `user:{uid}:CNY`, CNY 30 |
| **资产发放(活动送 100 积分)** | `issue:POINTS` → `user:{uid}:POINTS`, POINTS 100 |
| **资产核销(过期/抵扣)** | `user:{uid}:POINTS` → `issue:POINTS`, POINTS 50 |
| **兑换(10 CNY →` → 1000 GOLD)** | 2 腿: <br>① `user:{uid}:CNY` → `exchange:CNY`, CNY 10 <br>② `exchange:GOLD` → `user:{uid}:GOLD`, GOLD 1000 |
| **冻结(预留,本期不做提现)** | `user:{uid}:CNY` → `user:frozen:{uid}:CNY`, CNY 50(预留接口给钱包中台) |
| **解冻(预留)** | `user:frozen:{uid}:CNY` → `user:{uid}:CNY`, CNY 50 |

> 提现冻结 / 代付成功的分录由**钱包中台**通过 issue 内部 API 写入(资产域只暴露 `frozen` 字段 + 冻结/解冻 API,提现两段式由钱包中台编排)。

**资产恒等式校验**: 每条腿 `src.asset_code == dst.asset_code == leg.asset_code`,校验失败 → 启动期 fail-fast。

### 3.3 双账户原子扣减(charge_mode=DUAL)

`POST /assets/pre-consume:dual` —— **接 MMagiX 场景 2/3**,把"user+tenant 同额双扣原子"在 assets 域内收口。

```
dualDeduct(userAccountId, tenantAccountId, assetCode, amount):
  charge_mode = DUAL
  tx_id = TxIdGenerator.next()
  BEGIN
    锁两账户(SELECT ... WHERE id IN (?, ?) ORDER BY id FOR UPDATE)
    校验: 两账户 balance + credit_limit >= amount
    user.balance -= amount;   user.frozen += amount
    tenant.balance -= amount; tenant.frozen += amount
    INSERT ubmx_pre_consume(RESERVED) + posting(tx_type=CONSUME, charge_mode=DUAL)
  COMMIT
```

**双扣语义**: user + tenant 同时预扣同额,本质是两个账户各自的 (balance → frozen) 挪移——**没有中间户、没有转账腿**,避免「user→tenant 转账」的错误建模:

- **DUAL 预扣**: 单事务内同锁两账户,各扣 amount 至 `frozen`,**任一失败整体回滚**(DB 事务保证)
- **DUAL 结算**: frozen -= est,balance += (est − actual) 差额回补(diff 逻辑同单扣)
- **DUAL 退款**: frozen -= amount,balance += amount(预扣全额释放)

> **关键**: 双扣全程不涉及「中间账户」,只在两账户的 balance/frozen 上做同事务更新,单腿失败时数据不一致不可能发生。

### 3.4 死锁防御 + 重试(O2/O12 定案)

- **死锁防御**: 多腿事务锁账户时 **按 account_id 升序加锁**(SELECT ... FOR UPDATE ORDER BY id),杜绝循环等待
- **重试策略**(O12 定案:行锁为主、version 仅审计,RetryTemplate 只兜可安全重试的异常):

```
RetryTemplate 配置:
  maxAttempts: 3
  backoffPolicy: ExponentialBackOffPolicy(initial=50ms, multiplier=2.0, max=800ms)
    -> 第1次重试 50ms,第2次重试 200ms,第3次重试 800ms
  retryOn:
    - DeadlockLoserDataAccessException     # 死锁 victim,必须重试
    - CannotAcquireLockException           # 锁超时(热点行竞争)
  # OptimisticLockingFailureException 不再重试 —— 行锁路径下 version 不参与条件,不会触发
```

- **热点账户治理(O11)**: 根因在边界户(`world:*`/`issue:*`/`fee:*`)单行锁——已通过 `account_type='BOUNDARY'` 豁免锁与余额更新根治;user 级真实热点(如头部商家户)再叠加 Redis 分布式锁(framework4j-distributed-lock)限速,防雪崩
- **监控指标**: `assets_posting_retry_count` / `assets_posting_deadlock_count` / `assets_cas_failure_rate`
  - 重试率 > 5% 告警(并发过高)
  - 死锁率 > 0.1% 告警

---

## 4. 核心 API 设计

> URL 沿用 `/assets/*` 命名空间,**runtime 域强制 `@RequiresSignature`**(三方对接),`/ops/*` 用 OPS token。
> 所有写操作走 framework4j-idempotency 路径(`Idempotency-Key` 请求头 + 表内 `uk(ext_order_id)` 双层幂等)。

### 4.1 资产定义管理(运营端,OPS)

| API | 语义 |
| | |
| `POST /assets/assets` | 新增资产定义(运营后台) |
| `GET /assets/assets` | 列表(支持 `asset_type / status` 过滤) |
| `GET /assets/assets/{code}` | 详情 |
| `PATCH /assets/assets/{code}` | 改 name/precision/can_*/expire_policy |
| `POST /assets/assets/{code}/suspend` | 停用(已有账户不冻结,新充值/发放禁止) |

### 4.2 账户查询(tenant/平台,APP token)

| API | 语义 |
| | |
| `GET /assets/accounts?owner_type=USER&owner_id={uid}` | 主体名下所有资产账户(含余额) |
| `GET /assets/accounts/{id}` | 单账户详情 |
| `GET /assets/accounts/{id}/balance` | 单账户余额(轻量,带 cache) |

### 4.3 内部 API(钱包中台 + 第三方系统调用)

> **这些接口既是对外的「资产操作」入口,也是钱包中台入账的内部口子**。钱包中台收到微信支付回调后,调 `POST /assets/runtime/issue` 把资金入账。

| API | 语义 | 幂等键 | 调用方 |
| | | | |
| `POST /assets/runtime/issue` | 入账(钱包中台写入渠道腿 / 运营发奖) | `{issueOrderId}` | 钱包中台 / 运营端 |
| `POST /assets/runtime/freeze` | 冻结(预留) | `{freezeOrderId}` | 钱包中台提现申请 |
| `POST /assets/runtime/unfreeze` | 解冻(预留) | `{unfreezeOrderId}` | 钱包中台代付失败 |

> **渠道对接不属于资产域**——钱包中台调这里完成账本写入,**钱的物理进出在钱包中台 + JeePay 层完成**。

#### 4.3.1 issue API leg 结构模板(O4 优化:钱包中台对接规范)

> 钱包中台调 issue 时,**必须按以下 leg 结构组装**;资产域会校验 leg 完整性与限额。

**模板 1: 法币充值入账(微信回调后)**
```json
{
  "issueOrderId": "WXCH-20260825-001",
  "txType": "ISSUE",
  "appId": 100,
  "extOrderId": "WXCH-20260825-001",
  "legs": [
    {"src": "world:wechat", "dst": "user:1001:CNY", "amount": "100.00", "assetCode": "CNY"}
  ],
  "ext": {"channelOrderId": "wx_20260825_xxx", "channelFee": "0.60"}
}
```
- 1 腿:渠道户 → 用户
- 平台手续费由钱包中台在渠道提现时单独走账(本期不在 issue 内扣手续费,避免 leg 数量膨胀)

**模板 2: 活动赠送积分**
```json
{
  "issueOrderId": "ACT-20260825-001",
  "txType": "ISSUE",
  "appId": 100,
  "legs": [
    {"src": "issue:POINTS", "dst": "user:1001:POINTS", "amount": "100", "assetCode": "POINTS"}
  ],
  "ext": {"activityId": "BACK-TO-SCHOOL-2026"}
}
```

**模板 3: 退款入账(商家 → 用户)**
```json
{
  "issueOrderId": "RFD-20260825-001",
  "txType": "REFUND",
  "appId": 100,
  "legs": [
    {"src": "merchant:2001:CNY", "dst": "user:1001:CNY", "amount": "30.00", "assetCode": "CNY"}
  ]
}
```

**校验规则(资产域 enforce)**:
1. **资产恒等**: 同 `assetCode` 的 leg,`src/dst/leg.assetCode` 必须一致
2. **限额**: FIAT 资产按 `limit_policy` 校验单笔/日累计/月累计(超过 → `LIMIT_EXCEEDED`)
3. **资产能力**: 检查 `asset.can_recharge` / `asset.can_transfer` 等
4. **幂等**: `issueOrderId` 经 `ubmx_tx_order` 抢占(O10)——重复请求同参返回 `result_snapshot` 上次结果、异参抛 `IDEMPOTENCY_CONFLICT`,不再重复扣减
5. **金额校验**: `amount > 0`,`SUM(legs.amount) >= 0`(允许 0 触发对账补录)

### 4.3.2 幂等键撤销/换单(O6)

> 业务方同一笔订单失败后,换订单号重试是常见诉求;但 `uk(ext_order_id)` 会阻断新订单号生效(原订单被锁)。本接口显式释放原幂等键。

| API | 语义 |
| | |
| `POST /assets/runtime/idempotency:replace` | 释放旧 `ext_order_id` 占用,允许新订单号生效 |

请求:
```json
{
  "appId": 100,
  "oldExtOrderId": "FAILED-OLD-001",
  "oldTxType": "ISSUE",
  "reason": "BUSINESS_ROLLBACK"
}
```

**强制条件**(防滥用):
- 原订单状态必须是 `FAILED`(不能撤销 SUCCESS 订单——那是真钱)
- 调用方需提供 OPS token + 业务方签名
- 释放动作入 `ubmx_posting.status='FAILED'` 标记(原订单 N 条腿变 FAILED,不改账本余额)
- 释放后写入审计日志(谁、何时、为何)

### 4.4 消费三阶段(runtime 域,核心)

| API | 语义 | 幂等键 | 关键行为 |
| | | | |
| `POST /assets/runtime/pre-consume` | 预扣估算额(单腿) | `{requestId}` | CAS `balance-=`, `frozen+=`, 写 `ubmx_pre_consume(RESERVED)` + posting 2 腿(扣除 + 冻结) |
| `POST /assets/runtime/pre-consume:dual` | 双账户原子预扣 | `{requestId}` | 同上 + DUAL 模式,user/tenant 各扣 amount |
| `POST /assets/runtime/settle` | 结算(confirm 实际额) | `{requestId}` | diff = est - actual; diff>0 退 / diff<0 补扣;**补扣余额不足 → `PARTIAL_SETTLED`,差额入差错池**(O15);UPDATE 带 `WHERE status='RESERVED'` guard |
| `POST /assets/runtime/refund` | 上游失败退款 | `{requestId}` | posting: frozen-=est, balance+=est |

### 4.5 流水查询

| API | 语义 |
| | |
| `GET /assets/postings?tx_id={txId}` | 按业务交易号查(全腿) |
| `GET /assets/postings?account_id={id}&page=N&size=M` | 账户流水分页 |
| `GET /assets/postings/export` | 对账导出(CSV,Ops) |

### 4.6 对账(ops 域,OPS)

| API | 语义 |
| | |
| `POST /assets/ops/reconcile/asset` | 资产恒等式校验(Σbalance = Σissue − Σburn,按资产) |
| `GET /assets/ops/reconcile/diff` | 差错池查询(长短款明细) |
| `POST /assets/ops/reconcile/channel` | **(预留)** 资产域账实核对 Σbalance ≤ 外部资产总额;**实际渠道对账单核对在钱包中台** |

> **渠道对账由钱包中台承担**(它有渠道账单),资产域只做账本侧的资产恒等校验 + 账实上限校验,**不重复做渠道对账**。

---

## 5. 与计费域(Saga)集成

> MMagiX 的 LLM 计费场景(三阶段扣费)迁入资产域后,对应 API 替换:

| MMagiX 现有(credit 模块) | 资产域对应 | 差异 |
| | | |
| `BillingSagaService.preConsume` | `POST /assets/runtime/pre-consume` 或 `:dual` | 单事务原子,无需跨服务 Saga |
| `BillingSagaService.settle` | `POST /assets/runtime/settle` | 单调用,内部处理 diff |
| `BillingSagaService.refund` | `POST /assets/runtime/refund` | 同 |
| `CreditAccountRepository.deductUserBalance/settleUserBalance` | 由 PostingService 统一收口 | 删 credit 域内 CAS 逻辑,迁移到 assets |

**MMagiX 侧改造**:
1. 删除 `BillingSagaService` 三阶段编排代码(下沉到资产域)
2. `CreditApiAdapter` 改调 assets runtime 域 API(走 HMAC 签名)
3. 余额账户表(`mmxt_credit_account`) 由 assets 的 `ubmx_account` 替代**(注意金额精度一致 NUMERIC(18,4) → 资产 NUMERIC(20,4) 兼容)**
4. 迁移脚本:V124__migrate_mmxt_credit_account_to_ubmx_account.sql(本期不写,作 MMagiX 改造时定)
5. 灰度期间**双跑对比**:新旧两路各跑 24h,对账校验差异 → 0 才切换

---

## 6. 复用现有基建(改造量主要省在这)

| 能力 | 复用来源 | 改动 |
| | | |
| CAS 乐观锁 | `@Version` + retry pattern (`ubma_subscribe_item`) | 直接搬 |
| TCC 预扣/退款 | `consume/reserve/commit` + `RefundService` | 字段适配金额 |
| 过期回收 | `ReserveTimeoutScheduler`(扫过期 RESERVED → refund) | 复用代码结构,改 SQL |
| 幂等键 | framework4j-idempotency + `ubmx_tx_order` 表 `uk(app_id, ext_order_id)` | 双保险(O10) |
| HMAC 签名 | `@RequiresSignature` runtime 域 | 直接覆盖 |
| 限流 | `@RateLimit` runtime 域 | 直接覆盖 |
| 审计 | `@Auditable` AOP | 直接覆盖 |
| outbox | 现 `ubmp_outbox` 模式 | 充值/扣减事件可订阅 |
| 多级缓存 | framework4j-cache `@CacheableGet` | 余额查询可加 Caffeine L1 |
| 双模式 SDK | local `service` 直接调用 + remote `@RequiresSignature` | runtime 默认开启 |
| 前端 | fc-web-sdk `FcTable`/`Pagination`/`FilterBar` | 运营后台直接拼 |

---

## 7. 合规与安全(资金自持强约束)

### 7.1 启动期合规开关

```java
// Benefit4jAutoConfiguration.java 新增装配
@Bean
@PostConstruct
void checkFiatAllowed() {
    long fiatCount = ubmxAssetMapper.countByType("FIAT");
    if (fiatCount > 0 && !fiatAllowed) {
        throw new IllegalStateException(
            "[Assets] FIAT assets detected but assets.fiat-allowed=false. " +
            "资金自持需提前完成合规评估(支付牌照 / 备付金存管). " +
            "评估通过后,设置 assets.fiat-allowed=true 启用.");
    }
}
```

### 7.2 资金安全 DB 兜底

- `ubmx_account.balance CHECK (>= 0)`: 任何 CAS 即使逻辑漏洞,DB 也会拒绝负余额
- `ubmx_posting.amount CHECK (> 0)`: 杜绝 0 / 负金额分录
- `ubmx_posting` append-only(应用代码无 UPDATE/DELETE 入口)

### 7.3 审计与对账红线

- 所有写操作 `@Auditable`,act=具体动作(ASSETS_ISSUE/ASSETS_PRECONSUME/ASSETS_SETTLE/ASSETS_REFUND/ASSETS_ADJUST)
- 调账(ADJUST)强制双签 + 通知(本期不实现,需时再加)
- 对账 T+1 自动跑,差异入差错池,**长款/短款自动告警**

---

## 8. 性能与容量规划

### 8.1 容量预估(业务口径: 每天 1~100w 条余额变更)

> 设计输入(v0.4 校准): 余额变更 **1~100w 条/日**(弹性区间)。按峰值 100w 条/日估算:均值 ≈ 12 TPS,秒级突发按 10 倍 ≈ **120 TPS**——单实例 PG 行锁 + O11 边界户豁免绰绰有余,**无需异步入账合并/拆户**(支付宝式缓冲记账在此量级属过度设计,记入 §11.1)。

| 表 | 峰值日增量 | 峰值月增量 | 年增量(按 100w/日) | 分区策略 |
| | | | | |
| `ubmx_posting` | ~300w 行(每笔 1-3 腿) | ~9000w | ~10 亿 | **首期就按 `created_at` 月分区**(O1),DDL 已沿用 V1.2.2 模式无增量成本;月初 cron 自动建下月分区 |
| `ubmx_tx_order` | ~100w | ~3000w | ~3.6 亿 | 单表不分区;O10 幂等闸,行窄,3 年后再评估归档 |
| `ubmx_account` | <1w(开户) | 30w | 360w | 单表足够 |
| `ubmx_pre_consume` | ~50w RESERVED | ~50w | 6 亿 | 终态 SETTLED/REFUNDED 归档冷表(本期不实现) |

### 8.2 索引覆盖

- 主查询路径:**账户余额**(`uk(app_id, owner_type, owner_id, asset_code)`)、**流水翻页**(`idx_posting_src/dst`)、**预扣单过期**(`idx_pre_consume_expire` 部分索引)
- 对账路径:按 `created_at` 分区裁剪扫描 + `idx_posting_asset_time` 按资产切片(对账每资产一次扫描,避免全表)
- **`ubmx_posting` 首期即分区**(O1):DDL 沿用 V1.2.2 pg_pathman 模式无新增成本;后续 SLO 月初 cron 自动建下月分区

### 8.3 缓存策略

| 数据 | 缓存 | TTL |
| | | |
| 资产定义 | Caffeine L1 + Redis L2 | 10 min(变更频低) |
| 账户余额 | **不缓存**(强一致),DB 走主键 | 0 |
| 流水翻页 | 不缓存(分页扫) | 0 |
| 预扣单过期时间 | 不缓存(scheduler 直接查 DB) | 0 |

### 8.4 对账调度与读模型(P2,R4 补)

- **调度**: framework4j-scheduler + distributed-lock 单实例,T+1 02:00 Asia/Shanghai(与 `limit_policy` 口径一致),依次跑:资产恒等式(边界户口径,§2.2 O11)→ 冻结一致性(O14)→ 快照生成 → 哈希链校验(O17,若启用)
- **日终余额快照(O16)**: `ubmx_balance_snapshot(account_id, snap_date, balance, frozen, PRIMARY KEY(account_id, snap_date))`——对账从快照起算当日增量,等价会计「日终试算平衡」,避免 T+1 全量扫月分区
- **哈希链防篡改(O17,可选)**: posting 增 `prev_hash/hash CHAR(64)`(SHA-256 = 前腿 hash + 本腿全部字段),复用 framework4j-audit Hash Chain 模式;对账顺带验链,防内部误改/作案

---

## 9. 测试策略

### 9.1 单元测试

| 类 | 用例 |
| | |
| `PostingServiceTest` | 单腿 / 多腿 / 双扣原子 / 死锁重试 / 余额不足抛 `INSUFFICIENT_BALANCE` |
| `AssetRegistryServiceTest` | CRUD / `can_*` 校验 / FIAT 合规开关 |
| `AccountServiceTest` | lazy 开户 / 乐观锁重试 / 余额 CHECK 兜底 |
| `PreConsumeServiceTest` | RESERVED 状态 / settle diff 计算 / refund / expire 回收 |
| `ReconcileServiceTest` | 资产恒等式校验 / 长短款检测 |

### 9.2 集成测试(framework4j-compatible-test 跑)

- **并发**:100 线程同账户 CAS,验证无超发(终态 ΣCAS 等于预期总和)
- **死锁**:10 线程互转(双向转账),验证无死锁 / 无脏数据
- **过期回收**:Mock 时间推进,验证 scheduler 自动 refund
- **幂等**:同 `request_id` 重放 N 次,只成功 1 次
- **对账**:注入伪差异,验证差错池告警

### 9.3 端到端(灰度)

1. dev DB 跑 schema + seed(预置 CNY/POINTS/GOLD 三种资产)
2. **mock 钱包中台**调 `POST /assets/runtime/issue` 写 100 CNY → 验账户余额 = 100,流水 1 条
3. 多资产消费(5 POINTS + 23 CNY + 2 fee)→ 验两端账户 + fee 户 + 流水 3 条
4. 退款 → 验原路账户 + 流水 1 条
5. 双扣 DUAL 预扣 user=50, tenant=50 → settle actual=40 → diff=10 各退 10
6. 故意构造余额不足 → 验证 DB CHECK 触发 / 抛 `INSUFFICIENT_BALANCE`

---

## 10. 落地顺序(P1/P2)

> 钱包中台独立项目负责渠道接入,资产域不涉及 P3。

| 期 | 内容 | 估时 | 验收 |
| | | | |
| **P1 资产基础** | ubmx_asset/account/posting/tx_order/pre_consume 5 表 Flyway(含 `credit_limit`/`can_credit` 预留列)+ AssetRegistry CRUD + Account lazy 开户 + PostingService 单笔原子事务 + 三阶段 API(issue/pre-consume/settle/refund) + 过期回收 scheduler | **~5-7 天** | 单资产闭环跑通,单元 + 集成测试全绿 |
| **P2 多资产 + 对账 + 双扣 + 授信** | 多腿事务 + charge_mode=DUAL + 兑换(双户过渡) + 三层对账 + outbox 事件 + 差错池 + **F1 授信完整能力**(OPS 调额/还款分录/负余额风控) | **~6-8 天** | MMagiX 场景 2/3 可切换,资产恒等式 T+1 校验通过,授信户负余额闭环 |
| **(独立) 钱包中台** | JeePay 适配层 + 提现两段式 + 原路退 + 渠道对账 + 备付金存管 | 由独立团队推进 | 资产域与钱包中台协同端到端,合规评估通过为前提 |

P1 即可承接 MMagiX 单扣场景,P2 承接双扣全量。钱包中台独立推进,**资产域完成后无任何阻塞**。

> 任务级拆解(WBS)、逐里程碑验收清单(DoD)与 MMagiX 灰度步骤见 [assets-dev-plan.md](./assets-dev-plan.md)。

---

## 11. 风险与前置

| ID | 风险 | 等级 | 处置 |
|---|---|---|---|
| **R1** | **没考虑分账/代收代付** | 中 | **本期显式不做**——商家提现(`merchant → merchant:frozen`)、平台分润(`merchant → fee → platform`)作为下一迭代项;§11.1 留接口位(merchant 户冻结已支持) |
| **R2** | **钱包中台 issue leg 结构缺失** | 中 | **已修复**(§4.3.1)——3 类 leg 模板 + 5 项校验规则,钱包中台按模板调 |
| **R3** | **CAS 重试策略未定义** | 中 | **已修复**(§3.4)——RetryTemplate + 指数退避 50ms→200ms→800ms + 死锁单独处理 |
| **R4** | **对账调度 + 分布式锁未细写** | 低 | **已补**(§8.4)——scheduler + distributed-lock 单实例,T+1 02:00 Asia/Shanghai,含快照/冻结一致性/验链 |
| **R5** | **大表 posting 没首期分区** | 中 | **已修复**(§2.3 + §8.1)——首期即按月分区,DDL 沿用 V1.2.2 模式无增量成本 |
| **R6** | **缺幂等键撤销/换单** | 中 | **已修复**(§4.3.2)——显式 `POST /assets/runtime/idempotency:replace` API |
| **R7** | **汇率/法币换算** | 低 | **不阻塞**,但 ADR-0008 显式声明"multi-资产 ≠ multi-币种",本期不涉及 |
| **R8** | **反洗钱/大额监控** | 中 | **已修复**(§2.1)——`limit_policy JSONB` 单笔/日累计/月累计,FIAT 强制配 |
| **R9** | **冻结字段粒度粗** | 中 | **已修复**(§2.5)——新增 `ubmx_freeze` 原因表,WITHDRAW/AFTER_SALE/RISK/PRE_CONSUME/OTHER 分桶 |
| **R10** | **owner_type 枚举扩展性差** | 低 | **P2 优化**(O7)——后续字典表化,本期不动 |
| **R11** | **幂等键被分区键稀释**(uk 含 created_at,同订单跨日重放仍成功) | 高 | **已修复**(O10/§2.7)——独立不分区 `ubmx_tx_order` 表承担幂等闸 + 响应快照 |
| **R12** | **边界户单行热点**(全平台充值串行于 `world:wechat` 一行锁) | 高 | **已修复**(O11/§2.2)——`account_type='BOUNDARY'` 豁免锁与余额更新 |
| **R13** | **锁策略双轨矛盾**(FOR UPDATE + CAS 语义打架,CAS 分支永不触发) | 中 | **已修复**(O12/§3.4)——行锁定案,version 仅审计 |
| **基础 R** | **资金自持合规**(国内二清) | 高 | 启动 fail-fast `assets.fiat-allowed=false` 时禁止 CNY 资产;**生产部署前完成支付合规评估** |
| **基础 R** | **CAS 死锁** | 高 | 锁账户按 id 升序;监控 `assets_posting_deadlock_count`,>0.1% 告警 |
| **基础 R** | **精度溢出** | 高 | 应用层 BigDecimal + 单元测试覆盖边界值;DB `NUMERIC(20,4)` = 16 位整数 + 4 位小数,余量充足 |
| **基础 R** | **跨服务 Saga** | 中 | 本期不做,业务方单事务收口;后续需要再升级(可借 outbox/eventual) |
| **基础 R** | **FIAT 资产误启用** | 高 | CI 加 lint:不允许在 staging/prod 默认开启 `assets.fiat-allowed=true`,需人工审批 |
| **基础 R** | **调度器故障** | 中 | 过期回收 scheduler 单实例 + 分布式锁(framework4j-distributed-lock),防多实例重复 refund |
| **基础 R** | **钱包中台 schema 演进** | 低 | 资产域 `ubmx_account` 已含 `EXTERNAL` owner_type 兜底;钱包中台替换/迁移不影响资产域 |

### 11.1 明确不在本期范围

| 项 | 原因 | 后续规划 |
|---|---|---|
| **R1 分账/代收代付** | 业务复杂度高,本期优先账本稳定性 | 下迭代 P3,需 §2.5 冻结原因表已支持 merchant 户冻结 |
| **R10 owner_type 字典表** | 改动广(全表 FK),本期不动 | 下迭代重构 owner_type 关联 `ubmx_owner_type` 字典表 |
| **跨资产可转账**(POINTS → GOLD 等) | 风控压力大,首期关 `can_transfer` | 风控成熟后开 `can_transfer` |
| **大额出金串行化** | 当前 `FOR UPDATE` + CAS 足够;大额单笔走单事务调度 | 后续如发现大额出金瓶颈,改 `SERIALIZABLE` 隔离 |
| **user 级异步入账合并**(支付宝式缓冲记账) | 量级 1~100w 条/日(均值 ~12 TPS),同步记账绰绰有余 | 单用户 TPS 成为实测瓶颈时再评估(需牺牲余额强一致) |
| **`effective_at` 未来日期分录** | 无账期/应收场景,Stripe 该能力用不上 | 已随 F4 否决(§11.2);真出现月结合同再重评 |
| **计息/理财** | 牌照边界外,虚拟资产无计息语义 | 永不(合规红线) |
| **多币种真法币**(USD/EUR/JPY) | 国内合规不做国际渠道 | 钱包中台需国际资质时再启动 |

### 11.2 域定位与扩展边界(唯一扩展 = F1 授信)

> **域定位**(定案): assets = **非周期性资产**——周期性权益归权益域(`refresh_cycle`),计费编排归计费方(MMagiX),资产域只提供**记账原语**(issue/三阶段扣费/退款/冻结)。**资产定义为「元」时即为简单钱包,直接支撑按量计费**(§1.2 定位锚点)。
>
> 据此收敛扩展边界:F2-F5 **已评估并否决**,唯一保留的扩展 = F1 授信负余额(正式能力,P2 实现)。

| # | 能力 | 预留位(本期落) | 实现期 | 对标 |
|---|---|---|---|---|
| **F1** | **授信负余额** | `ubmx_account.credit_limit`(默认 0,行为不变)+ `ubmx_asset.can_credit` + CHECK 兼容(`balance >= -credit_limit`)随 P1 DDL 落地 | **P1 落列,P2 实现完整能力**(正式范围) | Stripe 账户原生允许负余额(credit 模式);花呗/白条 = 授信账户 |
| ~~F2~~ | ~~周期发放~~(每月送 N 算力点) | **不做**——与权益中台 `refresh_cycle` 周期刷新语义重复:周期性权益归权益域(配额桶天然支持),资产域只接收 issue 单次入账,不重复造周期调度 | - | - |
| ~~F3~~ | ~~超额转按量~~ | **不做**——属计费编排层(MMagiX)职责:资产域只提供扣费三阶段原语,「额度耗尽转按量」是编排开关,届时纯编排零 schema 改动 | - | - |
| ~~F4~~ | ~~账期/月结~~ | **不做**——无月结合同;`effective_at`(记账日≠发生日)会引入「记账额 vs 生效额」双余额口径,复杂度不值 | - | - |
| ~~F5~~ | ~~账户组/成本中心~~ | **不做**——USER/TENANT 两级 owner 够用;真出现多预算单元时借 O7 字典表化一起落 | - | - |

**F1 授信负余额 · 定案口径**(P1 落列,P2 实现完整能力):
- 授信户:`credit_limit > 0` 且 `asset.can_credit = true`,余额可负至 `-credit_limit`——**与防超发共用同一道 DB CHECK 闸**,无新增一致性风险
- 授信调整:走 OPS `ADJUST` + 双签(§7.3 已预留);多笔授信叠加/独立过期 → P3 `ubmx_credit_grant` 明细表(结构同 `ubmx_freeze`)
- 还款分录:`credit:{asset}` → `user:{uid}:CNY`(授信释放语义;`credit:*` 为 BOUNDARY 边界户,复用 O11 豁免,不引入新锁点。v0.4 原文方向写反,实现时更正)
- 风控联动:`balance < 0` 时强制禁提现/转账(校验前置余额符号判断)
- **不做**: 计息、动态授信风控评分(风险域职责,不属于资产域)

**扩展收敛结论**: 扩展能力只剩 **F1 一项(P2 实现)**;F2-F5 已评估并否决——防止资产域长成「小钱包中台 + 计费系统」的怪物,边界保持:周期权益归权益域,计费编排归计费方,资金渠道归钱包中台。

---

## 12. ADR(架构决策记录)

### ADR-0008: 新建 assets 域独立于权益桶 + 独立于钱包中台

> **状态**: 已决定 · **日期**: 2026-08 · **版本**: 草稿

**背景**: 余额型场景与现有权益桶模型语义偏差(精度/资源/会计视图/合规四维差异);钱包/储值渠道接入不应与账本耦合。

**决策**:
1. 新建 `assets` 域(6 张表 `ubmx_asset/account/posting/tx_order/pre_consume` + 冻结原因表 `ubmx_freeze`),不复用 `ubma_subscribe_item`
2. 资产域只承担账本能力;真实法币渠道接入(充值/提现/原路退)由未来独立的「钱包中台」项目承担

**理由**:
- 复用 → 永不过期/不刷新/不与订阅关联全靠业务方自律,资金风险无兜底
- 复用 → 复式分录语义靠拼接(权益桶三字段拼余额+冻结+累计,语义模糊)
- 新建 → 5 张表结构清晰,`CHECK (>= 0)` DB 兜底防超发,**资金安全有 schema 保障**
- 钱包中台独立 → 资产域不被渠道层耦合,钱包中台可独立演进/替换/迁移
- 冻结分桶(`ubmx_freeze`)→ 借鉴 Stripe/PayPal 的多冻结原因子账,审计与释放更精细

**后果**:
- ✅ 资金安全 schema 兜底
- ✅ 多资产组合支付多腿事务自然
- ✅ 资产恒等式可对账校验
- ✅ 钱包中台独立演进,资产域 schema 不变
- ✅ 冻结粒度细到原因,可追溯
- ⚠️ 复用 `ReserveTimeoutScheduler` 等模式但不复用表
- ⚠️ MMagiX 迁入需双跑灰度
- ⚠️ 钱包中台独立项目需另立项(本设计不阻塞)

### ADR-0009: assets 域演进路径 —— 与 Stripe Ledger 对标

> **状态**: 已决定 · **日期**: 2026-08 · **版本**: 草稿

**背景**: 资产域核心设计借鉴 Stripe Ledger 复式记账模型;演进路径需对齐大厂成熟方案,避免从零造轮子。

**决策**: assets 域核心模型(append-only posting + 资产码主键 + DB 兜底 + 多腿事务)对齐 Stripe Ledger;演进分 4 期,与大厂一致性对齐度**:

| 期 | 范围 | 对标 | 对齐度 |
|---|---|---|---|
| **P1 基础账本** | 5 表 Flyway + 资产注册 + 三阶段 API + 过期回收 | Stripe Ledger v1 | **85%** |
| **P2 多资产 + 对账 + 双扣** | 多腿事务 + DUAL + 兑换 + 三层对账 + outbox + 冻结分桶 + 限额 + RetryTemplate + 幂等撤销 | Stripe Ledger v2 + PayPal hold ledger | **90%** |
| **P3 分账/代收代付** | 多腿 splits + merchant 提现 + 平台分润 | Stripe Connect | **70%**(首期) |
| **P4 国际化** | 多币种 + 实时汇率 + Stripe-style `available_balance` 拆分 | Stripe Connect + PayPal multi-currency | **按需启动** |

**与 Stripe 的差异**:

| 维度 | Stripe | 本 assets 域 | 差异原因 |
|---|---|---|---|
| 隔离级别 | `SERIALIZABLE` | `READ COMMITTED` + 乐观锁 | PG serializable 性能开销大,本方案并发量级(中等 SaaS 100w/日)乐观锁足够 |
| 资产发行 | Stripe Connect + Issuing | 资产注册中心 + issue leg | 同思想,实现路径不同 |
| 冻结模型 | `pending_balance` 派生列 | `ubmx_freeze` 明细表 + 聚合 | Stripe 用派生列性能好但审计弱,本方案明细表审计更强 |
| 多币种 | 原生 USD/EUR/GBP 等 | **不做**(国内合规优先) | 国际化非本期目标 |

**与 Formance 的差异**:

| 维度 | Formance | 本 assets 域 | 差异原因 |
|---|---|---|---|
| 技术栈 | Go + Cloud | Java + PG | benefit4j 全栈 Java,跨语言成本高 |
| Numscript DSL | 有 | 无 | 中等规模自研足够,DSL 学习成本高 |
| Wallets 模块 | 有 | 自研 `ubmx_account` | 同功能,实现路径不同 |
| 对账 | 内置 | scheduler 调 `assets_posting_reconcile_job` | 接入 PG cron 即可 |

**理由**:
- **对齐 Stripe/Formance 而非自造** → 借大厂多年验证的设计,降低试错成本
- **对齐度不是目标,可演进才是** → 每期都比上一期更接近大厂成熟方案
- **技术栈差异接受** → Java vs Go/Cloud 不强求一致,功能对齐即可

**后果**:
- ✅ P1/P2 可与 Stripe Ledger 等同类比,招人 / Code Review 都有大厂参照
- ✅ 未来切到 Stripe Ledger / Formance 时数据模型可迁移(stub 替换,业务无感)
- ⚠️ 与大厂差异点(冻结模型、隔离级别)需 ADR 记录,新人 onboarding 看 ADR-0009

---

## 13. 附录

### A. 术语表

| 术语 | 含义 |
| | |
| 资产(asset) | 一种可被持有和流通的价值载体(CNY/POINTS/GOLD/COMPUTE/SCORE) |
| 资产码(asset_code) | 资产的字符串唯一标识,作为账本资产主键 |
| 主体(subject) | 资产持有方(USER/TENANT/MERCHANT/PLATFORM/EXTERNAL) |
| 账户(account) | (主体 × 资产) 的可计量账本 |
| 分录(posting) | 一笔资产流动的原子单元(src→dst+amount+asset) |
| 业务交易(tx) | 一组相关分录的集合(共享 tx_id) |
| 预扣(pre-consume) | TCC 模式: 估算额冻结(写 frozen 字段),confirm 后转 balance |
| 复式记账 | 每条腿都满足 src.asset = dst.asset = leg.asset;资产恒等式由事务保证 |
| 发行(issue) | 资产从 `issue:{asset}` 边界户流出,进入用户账户(运营发放/钱包中台入账) |
| 核销(burn) | 资产从用户账户流入 `issue:{asset}` 边界户(核销/过期/兑换出) |
| 钱包中台 | **未来独立项目**,对接支付渠道(JeePay)做真实充值/提现/原路退,通过资产域 `issue` 内部 API 写账本 |

### B. 文件清单(本期 P1 落地)

```
backend/benefit4j-starter/src/main/java/fun/commons/benefit4j/assets/
├── entity/
│   ├── UbmxAsset.java                 # @TableName("ubmx_asset")
│   ├── UbmxAccount.java               # @TableName("ubmx_account")
│   ├── UbmxPosting.java               # @TableName("ubmx_posting")
│   ├── UbmxTxOrder.java                 # @TableName("ubmx_tx_order") 幂等闸(O10)
│   ├── UbmxPreConsume.java            # @TableName("ubmx_pre_consume")
│   └── UbmxFreeze.java                  # @TableName("ubmx_freeze")
├── mapper/
│   ├── UbmxAssetMapper.java
│   ├── UbmxAccountMapper.java
│   ├── UbmxPostingMapper.java
│   ├── UbmxTxOrderMapper.java         # INSERT ... ON CONFLICT 幂等抢占
│   ├── UbmxPreConsumeMapper.java
│   └── UbmxFreezeMapper.java
├── service/
│   ├── AssetRegistryService.java      # 资产定义 CRUD
│   ├── AccountService.java            # lazy 开户 + 余额查询 + findRef 只读解析
│   ├── PostingService.java            # 复式记账引擎(核心,含 outbox 事件)
│   ├── PreConsumeService.java         # 三阶段(SOLO/DUAL)+ 过期回收
│   ├── AssetsExchangeService.java     # 兑换(P2)
│   ├── AssetsFreezeService.java       # 冻结/解冻 + O14 一致性(P2)
│   ├── AssetsLimitGuard.java          # limit_policy 限额(P2,方向键)
│   ├── AssetsReconcileService.java    # T+1 对账 + 快照 + 差错池(P2)
│   ├── AssetsIdempotencyService.java  # 幂等释放(P2,O6)
│   └── AssetsQueryService.java        # 账户/流水查询
├── scheduler/
│   ├── AssetsExpireScheduler.java     # 仿 ReserveTimeoutScheduler
│   └── AssetsReconcileScheduler.java  # T+1 对账 02:00 Asia/Shanghai(P2)
├── exception/
│   ├── InsufficientBalanceException.java
│   └── AssetsConfigException.java     # FIAT 禁用抛
└── dto/
    ├── PostIssueRequest.java
    ├── PostPreConsumeRequest.java
    ├── PostPreConsumeDualRequest.java
    ├── PostSettleRequest.java
    └── PostRefundRequest.java

backend/benefit4j-starter/src/main/java/fun/commons/benefit4j/controller/
└── BenefitAssetsController.java       # /assets/* 路由(运行时签名 + 调账走 OPS)

backend/benefit4j-starter/src/main/java/fun/commons/benefit4j/autoconfigure/
└── BenefitAssetsAutoConfiguration.java        # @PostConstruct FIAT 合规检查

backend/benefit4j-app/src/main/resources/db/migration/
├── V1.3.0__init_assets_asset.sql              # ubmx_asset
├── V1.3.1__init_assets_account.sql            # ubmx_account
├── V1.3.2__init_assets_posting.sql            # ubmx_posting + 索引(月分区)
├── V1.3.3__init_assets_tx_order.sql           # ubmx_tx_order 幂等闸(O10)
├── V1.3.4__init_assets_pre_consume.sql        # ubmx_pre_consume + 部分索引
├── V1.3.5__init_assets_freeze.sql             # ubmx_freeze + 部分索引
└── V1.3.6__init_reconcile.sql                 # 快照/差错池 + outbox 兜底(P2)
```

---

**评审要点**:
1. **assets 域独立于权益桶 + 独立于钱包中台** 的边界划分是否清晰?(§1.3 / §12 ADR-0008)
2. **钱包中台对资产域是 EXTERNAL subject** 的抽象是否合理?(§2.2)
3. **表前缀 `ubmx_`** 是否可接受?(§2 引言)
4. **资产域不暴露「充值」API,只暴露 issue 内部 API** 给钱包中台,是否达成职责清晰?(§4.3)
5. **issue API leg 结构模板**(§4.3.1)是否覆盖钱包中台对接场景?三类模板够不够?
6. **O1 首期分区**(§2.3 + §8.1)是否能与 V1.2.2 模式无缝衔接?DDL 是否需要可加?
7. **O3 限额 `limit_policy`** 字段定义是否合理?单笔/日累计/月累计够不够?
8. **O5 冻结原因表 `ubmx_freeze`** 是否会与现有 `frozen` 字段双重维护?
9. **R1-R13 风险**(§11)处置是否都达成?哪些需要额外讨论?
10. **MMagiX 灰度迁移** 路径是否可行?(§5 / §10 P1 验收)
11. **O10 幂等闸外移**(`ubmx_tx_order` 独立表 + 响应快照)是否彻底?是否还需 posting 侧二级兜底?(§2.7)
12. **O11 边界户豁免**(不锁/不更新余额)后,资产恒等式对账口径改为「用户域 Σbalance vs 边界户流出−流入」是否可接受?(§2.2/§8.4)
13. **F1 授信预留列**(`credit_limit` + `can_credit`)随 P1 DDL 落地(默认 0 行为不变)、P3 再实现授信业务,节奏是否合适?(§11.2)