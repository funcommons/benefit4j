-- =============================================================================
-- V1.2.1__bucket_candidate_index.sql
-- 扣减候选桶查询加速 (V1.2.0 多源桶上线后)
--
-- 背景:
--   findDeductionCandidates(app_id, userid, item_id) 在
--   ubma_subscribe_item 上执行:
--     WHERE app_id = ? AND item_id = ?
--       AND subscribe_id IN (SELECT id FROM ubma_subscribe WHERE ... ACTIVE)
--       AND (expires_at IS NULL OR expires_at > now())
--     ORDER BY bucket_priority DESC, expires_at ASC NULLS LAST
--
--   V1.2.0 的 idx_user_item_active_expiry 只覆盖 (app_id, item_id, expires_at),
--   bucket_priority 排序走 in-memory Sort, 单用户桶数 > 10 时变慢.
--
-- 改动:
--   1) 重建 idx_user_item_active_expiry, 把 bucket_priority 加进索引末位,
--      让 ORDER BY bucket_priority DESC 直接走 Index Scan (无需 Sort).
--      expires_at 留作过滤前缀, NULLS LAST 由 SQL 层显式表达.
--   2) 部分索引继续 WHERE is_deleted = 0, 缩小索引体积.
--
-- 兼容性:
--   CREATE 走 IF NOT EXISTS, 但 DROP 必须先执行 (PostgreSQL 不支持 IF EXISTS
--   修改索引列). 已在生产执行的实例会被 Flyway 识别为 checksum 不匹配,
--   需用 flyway repair 或手动执行.
-- =============================================================================

DROP INDEX IF EXISTS idx_user_item_active_expiry;

CREATE INDEX IF NOT EXISTS idx_user_item_active_priority
    ON ubma_subscribe_item (app_id, item_id, bucket_priority DESC, expires_at ASC NULLS LAST)
    WHERE is_deleted = 0;

COMMENT ON INDEX idx_user_item_active_priority IS
    '扣减候选桶查询: WHERE app_id=? AND item_id=? AND (expires_at IS NULL OR expires_at > now) ORDER BY bucket_priority DESC';