-- V1.3.1 assets 域 · 账户 (ubmx_account) —— 主体 × 资产
-- 依据 documents/assets-design.md v0.4 §2.2 (含 O11 边界户 / F1 授信预留列)
-- 禁外键: 一致性由 PostingService 应用层单事务保证(mc-database-spec 铁律 4)

CREATE TABLE IF NOT EXISTS ubmx_account (
    id              BIGINT       PRIMARY KEY,        -- 雪花 ID
    app_id          BIGINT       NOT NULL,            -- 多租户隔离(幂等键命名空间/数据权限/对账切片)
    owner_type      VARCHAR(8)   NOT NULL
                    CHECK (owner_type IN ('USER','TENANT','MERCHANT','PLATFORM','EXTERNAL')),
    owner_id        BIGINT       NOT NULL,            -- userId / tenantId / merchantId;EXTERNAL = 钱包中台 ID(内部约定)
    asset_code      VARCHAR(32)  NOT NULL,
    account_type    VARCHAR(8)   NOT NULL DEFAULT 'NORMAL'
                    CHECK (account_type IN ('NORMAL','BOUNDARY')),   -- O11: 边界户豁免锁与余额约束
    balance         NUMERIC(20,4) NOT NULL DEFAULT 0,
    credit_limit    NUMERIC(20,4) NOT NULL DEFAULT 0, -- F1: 授信额度(默认 0 = 不授信,P2 启用授信业务)
    frozen          NUMERIC(20,4) NOT NULL DEFAULT 0
                    CHECK (frozen >= 0),
    version         INTEGER      NOT NULL DEFAULT 0,  -- 审计自增(O12: 不参与更新条件)
    status          VARCHAR(8)   NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
    ext             JSONB,                             -- 业务侧透传(资产级 ext)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, owner_type, owner_id, asset_code),
    -- 资金安全兜底(O11/F1): 边界户不约束;普通户非负;授信户允许负至 -credit_limit。
    -- 这是防超发的最后一道闸,任何应用层逻辑漏洞都被此拒绝。
    CHECK (account_type = 'BOUNDARY' OR balance >= -credit_limit)
);

CREATE INDEX IF NOT EXISTS idx_ubmx_account_owner
    ON ubmx_account(app_id, owner_type, owner_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_ubmx_account_ext_gin ON ubmx_account USING GIN (ext);
