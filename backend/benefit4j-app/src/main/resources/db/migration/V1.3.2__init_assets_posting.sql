-- V1.3.2 assets 域 · 复式分录 (ubmx_posting) —— append-only,原生月分区
-- 依据 documents/assets-design.md v0.4 §2.3 (O1 分区 / O10 不建分区表唯一键 / O8 复合索引)
-- DDL 模式沿用 V1.2.2 (ADR-0007 PG 原生分区):
--   * PRIMARY KEY (id, created_at) + PARTITION BY RANGE (created_at)
--   * 除当月外预建未来两月 + default(2026-08 落地,9/10 月分区就绪;
--     default 兜底历史/未来数据,但【一旦 default 吃了数据,补建同范围分区会冲突】,
--     因此 cron 需在每月 25 日前建出下月分区 —— assets-dev-plan.md 风险表)

CREATE TABLE IF NOT EXISTS ubmx_posting (
    id              BIGINT       NOT NULL,
    app_id          BIGINT       NOT NULL,
    tx_id           BIGINT       NOT NULL,            -- 业务交易号(同一笔业务多腿共享)
    tx_type         VARCHAR(32)  NOT NULL,            -- ISSUE | CONSUME | REFUND | TRANSFER | EXCHANGE | ADJUST | FREEZE | UNFREEZE
    ext_order_id    VARCHAR(64)  NOT NULL,            -- 调用方幂等键(闸在 ubmx_tx_order,此处仅查询维度)
    leg_seq         SMALLINT     NOT NULL,            -- 同 tx 的腿序号(0,1,2...)
    src_account_id  BIGINT       NOT NULL,
    dst_account_id  BIGINT       NOT NULL,
    asset_code      VARCHAR(32)  NOT NULL,
    amount          NUMERIC(20,4) NOT NULL
                    CHECK (amount > 0),               -- 金额恒正,方向由腿含义区分
    direction       VARCHAR(3)   NOT NULL
                    CHECK (direction IN ('IN','OUT')),  -- 'OUT' 为 3 字符,v0.4 文档误写 VARCHAR(2) 已修
    balance_after   NUMERIC(20,4),                    -- 该腿记账后 src/dst 余额(追溯用)
    status          VARCHAR(8)   NOT NULL DEFAULT 'INIT'
                    CHECK (status IN ('INIT','SUCCESS','FAILED')),
    ext             JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
-- O10: 不在分区表上建 (app_id, ext_order_id, leg_seq) 唯一键 ——
--     PG 分区表唯一约束必含分区键,created_at 会稀释幂等(同订单跨日重放仍成功,资金级缺陷)。
--     幂等闸 = V1.3.3 ubmx_tx_order.UNIQUE(app_id, ext_order_id)。

CREATE TABLE IF NOT EXISTS ubmx_posting_202608 PARTITION OF ubmx_posting
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS ubmx_posting_202609 PARTITION OF ubmx_posting
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS ubmx_posting_202610 PARTITION OF ubmx_posting
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS ubmx_posting_default PARTITION OF ubmx_posting DEFAULT;

CREATE INDEX IF NOT EXISTS idx_ubmx_posting_tx          ON ubmx_posting(tx_id);
CREATE INDEX IF NOT EXISTS idx_ubmx_posting_src         ON ubmx_posting(app_id, src_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ubmx_posting_dst         ON ubmx_posting(app_id, dst_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ubmx_posting_order       ON ubmx_posting(app_id, ext_order_id);
CREATE INDEX IF NOT EXISTS idx_ubmx_posting_asset_time  ON ubmx_posting(asset_code, created_at DESC);  -- O8
CREATE INDEX IF NOT EXISTS idx_ubmx_posting_ext_gin     ON ubmx_posting USING GIN (ext);

-- 勘误修复: v1.0 该列误建为 VARCHAR(2),存不下 'OUT'(同类型 ALTER 幂等)
ALTER TABLE ubmx_posting ALTER COLUMN direction TYPE VARCHAR(3);
