-- V1.3.4 assets 域 · TCC 预扣单 (ubmx_pre_consume)
-- 依据 documents/assets-design.md v0.4 §2.4 (O15: PARTIAL_SETTLED + 终态竞态 guard)
-- RESERVED 时 account.frozen += estimated(frozen 为缓存聚合,主数据源 = ubmx_freeze,O14)

CREATE TABLE IF NOT EXISTS ubmx_pre_consume (
    id              BIGINT       PRIMARY KEY,        -- 雪花 ID
    app_id          BIGINT       NOT NULL,
    request_id      VARCHAR(64)  NOT NULL,           -- 调用方幂等键
    tx_id           BIGINT       NOT NULL,           -- 对应的 posting 主交易号(预扣腿已写入)
    charge_mode     VARCHAR(8)   NOT NULL DEFAULT 'SOLO'
                    CHECK (charge_mode IN ('SOLO','DUAL')),
    user_account_id     BIGINT,
    tenant_account_id   BIGINT,                      -- DUAL 模式下双账户
    asset_code      VARCHAR(32)  NOT NULL,
    estimated       NUMERIC(20,4) NOT NULL,          -- 预扣估算额
    settled_amount  NUMERIC(20,4),                   -- 结算时回填实际
    status          VARCHAR(16)  NOT NULL DEFAULT 'RESERVED'
                    CHECK (status IN ('RESERVED','SETTLED','PARTIAL_SETTLED','REFUNDED','EXPIRED')),
    expire_time     TIMESTAMPTZ  NOT NULL,           -- 默认 NOW()+30min,scheduler 扫描过期
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, request_id)
);

-- 过期回收 scheduler 部分索引: 只扫 RESERVED
CREATE INDEX IF NOT EXISTS idx_ubmx_pre_consume_expire
    ON ubmx_pre_consume(expire_time) WHERE status = 'RESERVED';
