-- V1.3.5 assets 域 · 冻结原因明细 (ubmx_freeze) —— O5 冻结分桶 / O14 冻结单一数据源
-- 依据 documents/assets-design.md v0.4 §2.5
-- account.frozen 是缓存聚合;主数据 = SUM(amount - used_amount) WHERE status='ACTIVE',
-- 对账 job 每日校验两者一致,不一致以本表为准(P2 启用)。

CREATE TABLE IF NOT EXISTS ubmx_freeze (
    id              BIGINT       PRIMARY KEY,        -- 雪花 ID
    app_id          BIGINT       NOT NULL,
    account_id      BIGINT       NOT NULL,
    freeze_no       VARCHAR(64)  NOT NULL,           -- 业务冻结单号(幂等键)
    reason          VARCHAR(32)  NOT NULL
                    CHECK (reason IN ('WITHDRAW','AFTER_SALE','RISK','PRE_CONSUME','OTHER')),
    amount          NUMERIC(20,4) NOT NULL
                    CHECK (amount > 0),
    used_amount     NUMERIC(20,4) NOT NULL DEFAULT 0, -- 已使用部分(部分释放)
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','RELEASED','CONSUMED','EXPIRED')),
    expire_time     TIMESTAMPTZ,                      -- 可选,自动过期
    ext             JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, freeze_no)
);

CREATE INDEX IF NOT EXISTS idx_ubmx_freeze_account ON ubmx_freeze(account_id) WHERE status = 'ACTIVE';
