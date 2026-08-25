-- V1.3.0 assets 域 · 资产注册中心 (ubmx_asset)
-- 依据 documents/assets-design.md v0.4 §2.1
-- 说明: code 字符串主键为设计定案权衡(新增资产 = INSERT 一行,账本零 schema 迁移),
--       偏离 mc-database-spec「主键 id bigint」通则,已由 ADR-0008 记录。
-- 全部 IF NOT EXISTS / ON CONFLICT,保证幂等可重放(本仓未接 Flyway,IT @BeforeAll 直连执行)。

CREATE TABLE IF NOT EXISTS ubmx_asset (
    code            VARCHAR(32)  PRIMARY KEY,        -- 'POINTS' | 'GOLD' | 'COMPUTE' | 'CNY' ...
    name            VARCHAR(64)  NOT NULL,           -- 中文名 "积分"
    asset_type      VARCHAR(8)   NOT NULL
                    CHECK (asset_type IN ('FIAT','VIRTUAL')),
    precision       SMALLINT     NOT NULL DEFAULT 0  -- CNY=2, 积分=0, 算力=4
                    CHECK (precision BETWEEN 0 AND 6),
    can_recharge    BOOLEAN      NOT NULL DEFAULT FALSE,  -- 渠道入账(钱包中台调用)
    can_withdraw    BOOLEAN      NOT NULL DEFAULT FALSE,  -- 仅 CNY = TRUE
    can_pay         BOOLEAN      NOT NULL DEFAULT TRUE,
    can_transfer    BOOLEAN      NOT NULL DEFAULT FALSE,  -- 转账风控,首期关
    can_exchange    BOOLEAN      NOT NULL DEFAULT TRUE,   -- 虚拟资产可相互兑换
    can_credit      BOOLEAN      NOT NULL DEFAULT FALSE,  -- F1 授信负余额(列随 P1 落,能力 P2 实现)
    issue_mode      VARCHAR(32),                      -- RECHARGE | GIFT | ACTIVITY | EXCHANGE
    expire_policy   JSONB,                             -- {"strategy":"NEVER"|"FIXED"|"ROLLING"}
    -- O3: 反洗钱/限额(FIAT 强制配);日/月累计按 Asia/Shanghai 营业日口径
    limit_policy    JSONB,
    description     VARCHAR(500),
    status          VARCHAR(8)   NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','SUSPEND')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ubmx_asset_type ON ubmx_asset(asset_type) WHERE status = 'ACTIVE';

-- expire_policy / limit_policy 本期无查询路径,不建 GIN(纯写放大);scheduler 单字段过滤覆盖,
-- 未来需要时再补 —— 设计 §2.1 已注明。

-- 种子: 3 个 VIRTUAL 资产;【无 FIAT 种子】—— assets.fiat-allowed=false 启动 fail-fast 依赖此前提(A4)
INSERT INTO ubmx_asset (code, name, asset_type, precision, can_pay, can_exchange, expire_policy, description)
VALUES
    ('POINTS',  '积分',   'VIRTUAL', 0, TRUE, TRUE, '{"strategy":"NEVER"}', '活动/消费积分'),
    ('GOLD',    '金币',   'VIRTUAL', 2, TRUE, TRUE, '{"strategy":"NEVER"}', '虚拟金币(2位小数)'),
    ('COMPUTE', '算力点', 'VIRTUAL', 4, TRUE, TRUE, '{"strategy":"NEVER"}', '算力计量点(4位小数)')
ON CONFLICT (code) DO NOTHING;
