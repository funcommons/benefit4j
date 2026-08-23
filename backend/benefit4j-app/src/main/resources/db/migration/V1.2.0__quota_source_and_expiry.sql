-- =============================================================================
-- V1.2.0__quota_source_and_expiry.sql
-- 多源额度批次: 同一权益项下多种来源余额 + 优先级扣减
--
-- 1) ubma_subscribe_item 增加 source_type / expires_at / bucket_priority 三列
--    - source_type: SUBSCRIPTION / TOPUP / GRANT / COMPENSATION / PROMOTION
--    - expires_at: 单桶过期时间, NULL = 永不过期
--    - bucket_priority: 单桶优先级, 数字越大越优先扣减
-- 2) 复合索引加速候选桶检索
-- 3) 部分唯一索引防止同一 subscribe 内重复创建同 source_type 桶
-- 4) 模板层 ubmp_benefit_tmpl_set 增加 default_source_type / default_expires_in_days
--    让运营可在模板端声明"月度赠送 30 天过期"等
--
-- DEFAULT 值保证旧数据自动回填, 向后兼容
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) ubma_subscribe_item 增量列
-- -----------------------------------------------------------------------------
ALTER TABLE ubma_subscribe_item
    ADD COLUMN IF NOT EXISTS source_type     VARCHAR(32)  NOT NULL DEFAULT 'SUBSCRIPTION',
    ADD COLUMN IF NOT EXISTS expires_at      TIMESTAMPTZ  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS bucket_priority INTEGER      NOT NULL DEFAULT 0;

COMMENT ON COLUMN ubma_subscribe_item.source_type     IS 'SUBSCRIPTION / TOPUP / GRANT / COMPENSATION / PROMOTION';
COMMENT ON COLUMN ubma_subscribe_item.expires_at      IS '单桶过期时间, NULL = 永不过期';
COMMENT ON COLUMN ubma_subscribe_item.bucket_priority IS '单桶优先级, 数字越大越优先扣减';

-- -----------------------------------------------------------------------------
-- 2) 活动桶检索的复合索引 (候选查询加速)
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_user_item_active_expiry
    ON ubma_subscribe_item (app_id, item_id, expires_at)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 3) 部分唯一索引: 同一 (subscribe, item, source_type) 唯一, 避免重复补偿创建重复桶
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_subscribe_item_source
    ON ubma_subscribe_item (subscribe_id, item_id, source_type)
    WHERE is_deleted = 0;

-- -----------------------------------------------------------------------------
-- 4) 模板层增量列
-- -----------------------------------------------------------------------------
ALTER TABLE ubmp_benefit_tmpl_set
    ADD COLUMN IF NOT EXISTS default_source_type     VARCHAR(32) NOT NULL DEFAULT 'SUBSCRIPTION',
    ADD COLUMN IF NOT EXISTS default_expires_in_days INTEGER     NOT NULL DEFAULT 0;

COMMENT ON COLUMN ubmp_benefit_tmpl_set.default_source_type     IS '默认 source_type, 订阅创建时拷贝到桶';
COMMENT ON COLUMN ubmp_benefit_tmpl_set.default_expires_in_days IS '默认过期天数, 0 = 永不过期, 由 subscribe.dateBegin + N 天 计算桶 expires_at';

-- =============================================================================
-- 完成 — 多源额度批次表结构已就绪
-- =============================================================================