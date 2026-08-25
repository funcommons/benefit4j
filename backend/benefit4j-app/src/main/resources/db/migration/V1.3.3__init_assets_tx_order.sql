-- V1.3.3 assets 域 · 幂等闸 (ubmx_tx_order) —— O10
-- 依据 documents/assets-design.md v0.4 §2.7
-- 不分区小表,UNIQUE(app_id, ext_order_id) 是真正的幂等闸(永不过期);
-- result_snapshot 存首次成功响应,重放同参原样返回、异参抛 IDEMPOTENCY_CONFLICT。
-- PostingService 事务第一步 INSERT 抢占。

CREATE TABLE IF NOT EXISTS ubmx_tx_order (
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
    UNIQUE (app_id, ext_order_id)
);
