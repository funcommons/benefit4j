CREATE TABLE ubma_consume_new (
    id                bigint       NOT NULL,
    subs_item_id      bigint       NOT NULL,
    item_id           bigint       NOT NULL,
    external_order_id varchar(68)  NOT NULL,
    consume_num       integer      NOT NULL DEFAULT 1,
    status            varchar(36)  NOT NULL DEFAULT 'COMMITTED',
    consume_time      timestamptz  NOT NULL,
    ext               jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at        timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         varchar(68)  NOT NULL DEFAULT '',
    update_by         varchar(68)  NOT NULL DEFAULT '',
    is_deleted        smallint     NOT NULL DEFAULT 0,
    expire_time       timestamptz,
    app_id            bigint       NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE ubma_consume_202608 PARTITION OF ubma_consume_new
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE ubma_consume_default PARTITION OF ubma_consume_new DEFAULT;

INSERT INTO ubma_consume_new SELECT * FROM ubma_consume;

DROP TABLE ubma_consume;
ALTER TABLE ubma_consume_new RENAME TO ubma_consume;

CREATE INDEX idx_ubma_consume_app_id            ON ubma_consume USING btree (app_id);
CREATE INDEX idx_ubma_consume_ext               ON ubma_consume USING gin (ext);
CREATE INDEX idx_ubma_consume_external_order_id ON ubma_consume USING btree (external_order_id);
CREATE INDEX idx_ubma_consume_subs_item_id      ON ubma_consume USING btree (subs_item_id);
CREATE INDEX idx_ubma_consume_consume_time      ON ubma_consume USING btree (consume_time);

CREATE TABLE ubma_outbox (
    id             bigint       PRIMARY KEY,
    aggregate_type varchar(32)  NOT NULL,
    aggregate_id   bigint       NOT NULL,
    event_type     varchar(32)  NOT NULL,
    payload        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    status         varchar(16)  NOT NULL DEFAULT 'PENDING',
    created_at     timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at        timestamptz
);
CREATE INDEX idx_ubma_outbox_status_created ON ubma_outbox (status, created_at) WHERE status = 'PENDING';
