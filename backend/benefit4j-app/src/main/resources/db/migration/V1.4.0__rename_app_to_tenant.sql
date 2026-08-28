-- V1.4.0 术语彻改: 应用(app/application) → 租户(tenant)
-- 纯重命名,零业务规则变更;幂等可重放(列/表/索引存在才改)。
-- 背景: assets 域 owner_type='TENANT'/tenantAccountRef 已把「接入方自身」称租户,
--       隔离键却叫 app_id —— 同一实体两套名字,本次收敛为零外部接入方窗口的统一改名。

-- ① 应用主表: ubma_application → ubma_tenant(app_secret → tenant_secret)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ubma_application') THEN
        ALTER TABLE ubma_application RENAME TO ubma_tenant;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ubma_tenant' AND column_name = 'app_secret') THEN
        ALTER TABLE ubma_tenant RENAME COLUMN app_secret TO tenant_secret;
    END IF;
END $$;

-- ② app_id 列 → tenant_id(分区父表改名自动传播至子分区)
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'ubma_benefit_item', 'ubma_benefit_set', 'ubma_benefit_ref',
        'ubma_refund', 'ubma_compensation', 'ubma_consume',
        'ubma_unsubscribe', 'ubma_subscribe', 'ubma_subscribe_item', 'ubma_migration',
        'ubmx_account', 'ubmx_posting', 'ubmx_tx_order', 'ubmx_pre_consume', 'ubmx_freeze',
        'ubmx_balance_snapshot', 'ubmx_reconcile_diff'
    ] LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = t AND column_name = 'app_id') THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN app_id TO tenant_id', t);
        END IF;
    END LOOP;
END $$;

-- ③ 索引/约束名机械同步(application→tenant 先于 _app_id→_tenant_id,防部分匹配)
DO $$
DECLARE
    r record;
    newname text;
BEGIN
    FOR r IN SELECT indexname, tablename FROM pg_indexes
             WHERE indexname LIKE '%app%' AND indexname NOT LIKE '%tenant%'
    LOOP
        newname := replace(r.indexname, 'application', 'tenant');
        newname := replace(newname, '_app_id', '_tenant_id');
        newname := replace(newname, 'app_id', 'tenant_id');
        IF newname <> r.indexname THEN
            IF r.indexname LIKE '%\_key' THEN
                EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', r.tablename, r.indexname, newname);
            ELSE
                EXECUTE format('ALTER INDEX %I RENAME TO %I', r.indexname, newname);
            END IF;
        END IF;
    END LOOP;
END $$;
