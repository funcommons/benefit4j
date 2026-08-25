-- V1.3.6 assets 域 · 对账配套(O16 快照表 + 差错池)
-- 依据 documents/assets-design.md v0.4 §8.4

-- 日终余额快照: 对账从快照起算当日增量的基础(当前实现全量校验,数据量增长后切增量)
CREATE TABLE IF NOT EXISTS ubmx_balance_snapshot (
    account_id      BIGINT        NOT NULL,
    snap_date       DATE          NOT NULL,
    balance         NUMERIC(20,4) NOT NULL,
    frozen          NUMERIC(20,4) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, snap_date)
);

-- 差错池: 恒等式破坏 / O14 冻结不一致等,OPEN 待人工处置
CREATE TABLE IF NOT EXISTS ubmx_reconcile_diff (
    id              BIGSERIAL    PRIMARY KEY,
    app_id          BIGINT       NOT NULL,
    reconcile_type  VARCHAR(32)  NOT NULL,   -- ASSET_IDENTITY | FREEZE_MISMATCH
    asset_code      VARCHAR(32),
    account_id      BIGINT,
    expected        NUMERIC(20,4),
    actual          NUMERIC(20,4),
    status          VARCHAR(8)   NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','RESOLVED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ubmx_reconcile_diff_open
    ON ubmx_reconcile_diff(app_id, reconcile_type) WHERE status = 'OPEN';

-- 兜底: ubmp_outbox 生产由 V1.2.2 建;本仓测试库未跑过 V1.2.2(权益域 outbox 路径未触达),
-- assets B6 事件写入依赖该表,IF NOT EXISTS 幂等补建(结构与 V1.2.2 一致)
CREATE TABLE IF NOT EXISTS ubmp_outbox (
    id             BIGINT       PRIMARY KEY,
    aggregate_type VARCHAR(32)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(32)  NOT NULL,
    payload        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_ubmp_outbox_status_created ON ubmp_outbox (status, created_at) WHERE status = 'PENDING';
