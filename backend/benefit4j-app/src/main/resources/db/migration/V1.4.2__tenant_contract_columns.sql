-- V1.4.2 framework4j-tenant 接入:ubma_tenant 补齐契约列(中间件中台租户设计 §3.1)
-- 基类 TenantEntity 冻结字段 vs benefit4j 存量表的差集:email/channel/privileges/config/oem
-- 纯增量加列(带默认值),零数据迁移;幂等(IF NOT EXISTS)

ALTER TABLE ubma_tenant ADD COLUMN IF NOT EXISTS email VARCHAR(128);
ALTER TABLE ubma_tenant ADD COLUMN IF NOT EXISTS channel VARCHAR(16) NOT NULL DEFAULT 'OPS';
ALTER TABLE ubma_tenant ADD COLUMN IF NOT EXISTS privileges JSONB NOT NULL DEFAULT '{}';
ALTER TABLE ubma_tenant ADD COLUMN IF NOT EXISTS config JSONB NOT NULL DEFAULT '{}';
ALTER TABLE ubma_tenant ADD COLUMN IF NOT EXISTS oem JSONB NOT NULL DEFAULT '{}';

-- 契约索引(§3.1):ext GIN + email 部分唯一
CREATE INDEX IF NOT EXISTS idx_ubma_tenant_ext ON ubma_tenant USING GIN (ext);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ubma_tenant_email ON ubma_tenant (email)
    WHERE is_deleted = 0 AND email IS NOT NULL;
