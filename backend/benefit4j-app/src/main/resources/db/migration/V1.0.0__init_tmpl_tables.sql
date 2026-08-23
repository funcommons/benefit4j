-- =============================================================================
-- V1.0.0__init_tmpl_tables.sql
-- 模板层数据库结构初始化
-- 1) 删除旧的 ubmp_* 模板层 4 张表（产品从旧 ubmp 模型迁移到新 ubmp_benefit_tmpl_* 三表）
-- 2) 新建模板层 3 张表：item / set / ref，结构与产品层 (ubma_benefit_item/set/ref) 对齐
--    模板层为平台级共享数据，无租户隔离（不含 app_id），带 ext (JSONB EAV) 列
-- 配合 V1.0.1__seed_tmpl_mock.sql 加载 Mock 模板数据
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) DROP 旧 ubmp_* 模板层 4 张表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS ubmp_benefit_tmpl_item_ref;
DROP TABLE IF EXISTS ubmp_benefit_tmpl_ref;
DROP TABLE IF EXISTS ubmp_benefit_tmpl;
DROP TABLE IF EXISTS ubmp_item_template;

-- -----------------------------------------------------------------------------
-- 2) 模板层权益项 — ubmp_benefit_tmpl_item
-- -----------------------------------------------------------------------------
CREATE TABLE ubmp_benefit_tmpl_item (
    id                BIGINT PRIMARY KEY,
    name              VARCHAR(64) NOT NULL DEFAULT '',
    icon              VARCHAR(255) NOT NULL DEFAULT '',
    description       VARCHAR(512) NOT NULL DEFAULT '',
    default_deduction INTEGER NOT NULL DEFAULT 1,
    status            VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ext               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         VARCHAR(64) NOT NULL DEFAULT '',
    update_by         VARCHAR(64) NOT NULL DEFAULT '',
    is_deleted        SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ubmp_benefit_tmpl_item_ext    ON ubmp_benefit_tmpl_item USING GIN (ext);
CREATE INDEX idx_ubmp_benefit_tmpl_item_status ON ubmp_benefit_tmpl_item (status);
CREATE UNIQUE INDEX uk_ubmp_benefit_tmpl_item_name
    ON ubmp_benefit_tmpl_item (name) WHERE (is_deleted = 0);

-- -----------------------------------------------------------------------------
-- 3) 模板层权益集 — ubmp_benefit_tmpl_set
-- -----------------------------------------------------------------------------
CREATE TABLE ubmp_benefit_tmpl_set (
    id                BIGINT PRIMARY KEY,
    name              VARCHAR(64) NOT NULL DEFAULT '',
    duration          INTEGER NOT NULL DEFAULT 0,
    duration_unit     VARCHAR(16) NOT NULL DEFAULT '',
    priority          INTEGER NOT NULL DEFAULT 0,
    quota             INTEGER NOT NULL DEFAULT 0,
    refresh_cycle     INTEGER NOT NULL DEFAULT 0,
    refresh_cycle_unit VARCHAR(16) NOT NULL DEFAULT '',
    timing_mode       VARCHAR(16) NOT NULL DEFAULT 'RENEWAL',
    quota_unit        VARCHAR(16) NOT NULL DEFAULT '次',
    status            VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ext               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         VARCHAR(64) NOT NULL DEFAULT '',
    update_by         VARCHAR(64) NOT NULL DEFAULT '',
    is_deleted        SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ubmp_benefit_tmpl_set_ext    ON ubmp_benefit_tmpl_set USING GIN (ext);
CREATE INDEX idx_ubmp_benefit_tmpl_set_status ON ubmp_benefit_tmpl_set (status);

-- -----------------------------------------------------------------------------
-- 4) 模板层关联明细 — ubmp_benefit_tmpl_ref
-- -----------------------------------------------------------------------------
CREATE TABLE ubmp_benefit_tmpl_ref (
    id                BIGINT PRIMARY KEY,
    set_id            BIGINT NOT NULL,
    item_id           BIGINT NOT NULL,
    quota             INTEGER NOT NULL DEFAULT 0,
    refresh_cycle     INTEGER NOT NULL DEFAULT 0,
    refresh_cycle_unit VARCHAR(16) NOT NULL DEFAULT '',
    ext               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         VARCHAR(64) NOT NULL DEFAULT '',
    update_by         VARCHAR(64) NOT NULL DEFAULT '',
    is_deleted        SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ubmp_benefit_tmpl_ref_ext    ON ubmp_benefit_tmpl_ref USING GIN (ext);
CREATE INDEX idx_ubmp_benefit_tmpl_ref_set_id ON ubmp_benefit_tmpl_ref (set_id);
CREATE INDEX idx_ubmp_benefit_tmpl_ref_item_id ON ubmp_benefit_tmpl_ref (item_id);

-- =============================================================================
-- 完成 — 模板层 3 张表已就绪
-- =============================================================================