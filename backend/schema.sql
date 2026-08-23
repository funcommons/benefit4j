-- =============================================================================
-- UBM (User Benefit Middle-platform) Database Schema
-- 完整 schema 快照 — 截至 V1.2.1 (含 idx_user_item_active_priority 新索引)
--
-- 内容:
--   产品层 (ubma_*) : application / benefit_item / benefit_ref / benefit_set /
--                    compensation / consume / migration / refund / subscribe /
--                    subscribe_item / unsubscribe  (11 张)
--   模板层 (ubmp_benefit_tmpl_*) : item / set / ref  (3 张, V1.0.0)
--   审计层 (ubmp_audit_log) : 1 张, 由 framework4j-audit 自动建表
--
-- 重建方式:
--   1) psql -f schema.sql  (整库重建)
--   2) 或迁移方式: db/migration/ 目录下的 V1.0.0 + V1.0.1 + V1.2.0 + V1.2.1
--      (注: 当前 benefit4j-app 未启用 Flyway, 迁移文件需手动执行)
--
-- 不在范围内:
--   - seed 数据 (V1.0.1__seed_tmpl_mock.sql)
--   - 视图 / 触发器 / 存储过程 (项目禁)
-- =============================================================================

-- ubma_application
CREATE TABLE ubma_application (
    id bigint NOT NULL,
    app_secret varchar(132) NOT NULL,
    name varchar(68) NOT NULL,
    status varchar(36) NOT NULL DEFAULT 'ACTIVE',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    description varchar(516) DEFAULT '',
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_application_ext ON public.ubma_application USING gin (ext);


-- ubma_benefit_item
CREATE TABLE ubma_benefit_item (
    id bigint NOT NULL,
    name varchar(68) NOT NULL,
    icon varchar(259) NOT NULL DEFAULT '',
    description varchar(516) NOT NULL DEFAULT '',
    default_deduction integer NOT NULL DEFAULT 1,
    status varchar(36) NOT NULL DEFAULT 'ACTIVE',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_benefit_item_app_id ON public.ubma_benefit_item USING btree (app_id);
CREATE INDEX idx_ubma_benefit_item_ext ON public.ubma_benefit_item USING gin (ext);
CREATE UNIQUE INDEX uk_ubma_benefit_item_app_id_name ON public.ubma_benefit_item USING btree (app_id, name) WHERE (is_deleted = 0);


-- ubma_benefit_ref
CREATE TABLE ubma_benefit_ref (
    id bigint NOT NULL,
    set_id bigint NOT NULL,
    item_id bigint NOT NULL,
    quota integer NOT NULL DEFAULT 0,
    refresh_cycle integer NOT NULL DEFAULT 0,
    refresh_cycle_unit varchar(20) NOT NULL DEFAULT '',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_benefit_ref_app_id ON public.ubma_benefit_ref USING btree (app_id);
CREATE INDEX idx_ubma_benefit_ref_ext ON public.ubma_benefit_ref USING gin (ext);


-- ubma_benefit_set
CREATE TABLE ubma_benefit_set (
    id bigint NOT NULL,
    name varchar(68) NOT NULL,
    duration integer NOT NULL DEFAULT 0,
    duration_unit varchar(20) NOT NULL DEFAULT '',
    priority integer NOT NULL DEFAULT 0,
    quota integer NOT NULL DEFAULT 0,
    refresh_cycle integer NOT NULL DEFAULT 0,
    refresh_cycle_unit varchar(20) NOT NULL DEFAULT '',
    status varchar(36) NOT NULL DEFAULT 'ACTIVE',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    timing_mode varchar(20) NOT NULL DEFAULT 'RENEWAL',
    quota_unit varchar(20) NOT NULL DEFAULT '次',
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_benefit_set_app_id ON public.ubma_benefit_set USING btree (app_id);
CREATE INDEX idx_ubma_benefit_set_ext ON public.ubma_benefit_set USING gin (ext);


-- ubma_compensation
CREATE TABLE ubma_compensation (
    id bigint NOT NULL,
    subscribe_id bigint NOT NULL,
    subs_item_id bigint NOT NULL,
    item_id bigint NOT NULL,
    adjust_num integer NOT NULL,
    adjust_type varchar(36) NOT NULL DEFAULT 'ADD',
    reason varchar(259) NOT NULL DEFAULT '',
    operator varchar(68) NOT NULL DEFAULT '',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_compensation_app_id ON public.ubma_compensation USING btree (app_id);
CREATE INDEX idx_ubma_compensation_ext ON public.ubma_compensation USING gin (ext);
CREATE INDEX idx_ubma_compensation_subscribe_id ON public.ubma_compensation USING btree (subscribe_id);


-- ubma_consume
CREATE TABLE ubma_consume (
    id bigint NOT NULL,
    subs_item_id bigint NOT NULL,
    item_id bigint NOT NULL,
    external_order_id varchar(68) NOT NULL,
    consume_num integer NOT NULL DEFAULT 1,
    status varchar(36) NOT NULL DEFAULT 'COMMITTED',
    consume_time timestamptz NOT NULL,
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    expire_time timestamptz,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_consume_app_id ON public.ubma_consume USING btree (app_id);
CREATE INDEX idx_ubma_consume_ext ON public.ubma_consume USING gin (ext);


-- ubma_migration
CREATE TABLE ubma_migration (
    id bigint NOT NULL,
    userid varchar(68) NOT NULL,
    from_subscribe_id bigint NOT NULL,
    to_subscribe_id bigint NOT NULL,
    from_set_id bigint NOT NULL,
    to_set_id bigint NOT NULL,
    external_migrate_id varchar(68) NOT NULL,
    migrate_type varchar(36) NOT NULL DEFAULT 'UPGRADE',
    reason varchar(259) NOT NULL DEFAULT '',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_migration_app_id ON public.ubma_migration USING btree (app_id);
CREATE INDEX idx_ubma_migration_ext ON public.ubma_migration USING gin (ext);
CREATE INDEX idx_ubma_migration_userid ON public.ubma_migration USING btree (userid);
CREATE UNIQUE INDEX uk_ubma_migration_app_id_external_migrate_id ON public.ubma_migration USING btree (app_id, external_migrate_id) WHERE (is_deleted = 0);


-- ubma_refund
CREATE TABLE ubma_refund (
    id bigint NOT NULL,
    consume_id bigint NOT NULL,
    external_refund_id varchar(68) NOT NULL,
    refund_num integer NOT NULL DEFAULT 1,
    refund_time timestamptz NOT NULL,
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_refund_app_id ON public.ubma_refund USING btree (app_id);
CREATE INDEX idx_ubma_refund_ext ON public.ubma_refund USING gin (ext);


-- ubma_subscribe
CREATE TABLE ubma_subscribe (
    id bigint NOT NULL,
    userid varchar(68) NOT NULL,
    set_id bigint NOT NULL,
    total_consumed integer NOT NULL DEFAULT 0,
    period_consumed integer NOT NULL DEFAULT 0,
    quota_limit integer NOT NULL DEFAULT 0,
    next_refresh_time timestamptz,
    version integer NOT NULL DEFAULT 0,
    date_begin timestamptz NOT NULL,
    date_end timestamptz NOT NULL,
    status varchar(36) NOT NULL DEFAULT 'ACTIVE',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    external_order_id varchar(68) NOT NULL DEFAULT '',
    frozen_consumed integer NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_subscribe_app_id ON public.ubma_subscribe USING btree (app_id);
CREATE INDEX idx_ubma_subscribe_ext ON public.ubma_subscribe USING gin (ext);
CREATE INDEX idx_ubma_subscribe_userid ON public.ubma_subscribe USING btree (userid);
CREATE UNIQUE INDEX uk_ubma_subscribe_app_id_external_order_id ON public.ubma_subscribe USING btree (app_id, external_order_id) WHERE (is_deleted = 0);


-- ubma_subscribe_item
CREATE TABLE ubma_subscribe_item (
    id bigint NOT NULL,
    subscribe_id bigint NOT NULL,
    item_id bigint NOT NULL,
    total_consumed integer NOT NULL DEFAULT 0,
    period_consumed integer NOT NULL DEFAULT 0,
    frozen_consumed integer NOT NULL DEFAULT 0,
    quota_limit integer NOT NULL DEFAULT 0,
    next_refresh_time timestamptz,
    version integer NOT NULL DEFAULT 0,
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    source_type varchar(36) NOT NULL DEFAULT 'SUBSCRIPTION',
    expires_at timestamptz,
    bucket_priority integer NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_subscribe_item_app_id ON public.ubma_subscribe_item USING btree (app_id);
CREATE INDEX idx_ubma_subscribe_item_ext ON public.ubma_subscribe_item USING gin (ext);
CREATE INDEX idx_ubma_subscribe_item_subscribe_id_item_id ON public.ubma_subscribe_item USING btree (subscribe_id, item_id);
CREATE INDEX idx_user_item_active_priority ON public.ubma_subscribe_item USING btree (app_id, item_id, bucket_priority DESC, expires_at) WHERE (is_deleted = 0);
CREATE UNIQUE INDEX uk_subscribe_item_source ON public.ubma_subscribe_item USING btree (subscribe_id, item_id, source_type) WHERE (is_deleted = 0);


-- ubma_unsubscribe
CREATE TABLE ubma_unsubscribe (
    id bigint NOT NULL,
    subscribe_id bigint NOT NULL,
    external_order_id varchar(68) NOT NULL,
    reason varchar(259) NOT NULL DEFAULT '',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    app_id bigint NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_unsubscribe_app_id ON public.ubma_unsubscribe USING btree (app_id);
CREATE INDEX idx_ubma_unsubscribe_ext ON public.ubma_unsubscribe USING gin (ext);


-- ubmp_audit_log
CREATE TABLE ubmp_audit_log (
    id bigint NOT NULL DEFAULT nextval('ubmp_audit_log_id_seq'::regclass),
    action varchar(68) NOT NULL,
    target_type varchar(68) NOT NULL,
    target_id varchar(68),
    actor varchar(68),
    result varchar(20) NOT NULL,
    error_message text,
    args_json text,
    result_json text,
    ip varchar(49),
    user_agent varchar(259),
    trace_id varchar(68),
    timestamp timestamptz NOT NULL,
    prev_hash varchar(132) NOT NULL,
    hash varchar(132) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubmp_audit_actor_time ON public.ubmp_audit_log USING btree (actor, "timestamp" DESC);
CREATE INDEX idx_ubmp_audit_target ON public.ubmp_audit_log USING btree (target_type, target_id);
CREATE INDEX idx_ubmp_audit_time ON public.ubmp_audit_log USING btree ("timestamp" DESC);
CREATE UNIQUE INDEX ubmp_audit_log_hash_key ON public.ubmp_audit_log USING btree (hash);


-- ubmp_benefit_tmpl_item
CREATE TABLE ubmp_benefit_tmpl_item (
    id bigint NOT NULL,
    name varchar(68) NOT NULL DEFAULT '',
    icon varchar(259) NOT NULL DEFAULT '',
    description varchar(516) NOT NULL DEFAULT '',
    default_deduction integer NOT NULL DEFAULT 1,
    status varchar(36) NOT NULL DEFAULT 'ACTIVE',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_benefit_tmpl_item_ext ON public.ubmp_benefit_tmpl_item USING gin (ext);
CREATE INDEX idx_ubma_benefit_tmpl_item_status ON public.ubmp_benefit_tmpl_item USING btree (status);
CREATE UNIQUE INDEX uk_ubmp_benefit_tmpl_item_name ON public.ubmp_benefit_tmpl_item USING btree (name) WHERE (is_deleted = 0);


-- ubmp_benefit_tmpl_ref
CREATE TABLE ubmp_benefit_tmpl_ref (
    id bigint NOT NULL,
    set_id bigint NOT NULL,
    item_id bigint NOT NULL,
    quota integer NOT NULL DEFAULT 0,
    refresh_cycle integer NOT NULL DEFAULT 0,
    refresh_cycle_unit varchar(20) NOT NULL DEFAULT '',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_benefit_tmpl_ref_ext ON public.ubmp_benefit_tmpl_ref USING gin (ext);
CREATE INDEX idx_ubma_benefit_tmpl_ref_item_id ON public.ubmp_benefit_tmpl_ref USING btree (item_id);
CREATE INDEX idx_ubma_benefit_tmpl_ref_set_id ON public.ubmp_benefit_tmpl_ref USING btree (set_id);


-- ubmp_benefit_tmpl_set
CREATE TABLE ubmp_benefit_tmpl_set (
    id bigint NOT NULL,
    name varchar(68) NOT NULL DEFAULT '',
    duration integer NOT NULL DEFAULT 0,
    duration_unit varchar(20) NOT NULL DEFAULT '',
    priority integer NOT NULL DEFAULT 0,
    quota integer NOT NULL DEFAULT 0,
    refresh_cycle integer NOT NULL DEFAULT 0,
    refresh_cycle_unit varchar(20) NOT NULL DEFAULT '',
    timing_mode varchar(20) NOT NULL DEFAULT 'RENEWAL',
    quota_unit varchar(20) NOT NULL DEFAULT '次',
    status varchar(36) NOT NULL DEFAULT 'ACTIVE',
    ext jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by varchar(68) NOT NULL DEFAULT '',
    update_by varchar(68) NOT NULL DEFAULT '',
    is_deleted smallint NOT NULL DEFAULT 0,
    default_source_type varchar(36) NOT NULL DEFAULT 'SUBSCRIPTION',
    default_expires_in_days integer NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ubma_benefit_tmpl_set_ext ON public.ubmp_benefit_tmpl_set USING gin (ext);
CREATE INDEX idx_ubma_benefit_tmpl_set_status ON public.ubmp_benefit_tmpl_set USING btree (status);


