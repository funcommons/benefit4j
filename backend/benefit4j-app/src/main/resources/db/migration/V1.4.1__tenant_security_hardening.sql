-- V1.4.1 租户安全加固(《中间件中台租户设计》§5.5/§8 落地)
-- ① 密钥轮换宽限期: ubma_tenant 双版本列(旧密钥 + 存入时间)
-- ② RLS 就位(不 FORCE): 账本域策略建好,当前连接(admin/superuser)不受影响;
--    未来连接层切换非 owner 角色 + SET app.tenant_id 后 FORCE 即全量生效
--    (scheduler 对账等平台视角连接保持 superuser 通道,天然豁免)。
-- 幂等可重放: 列/策略存在即跳过。

-- ① 宽限期双版本列
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ubma_tenant' AND column_name = 'tenant_secret_prev') THEN
        ALTER TABLE ubma_tenant ADD COLUMN tenant_secret_prev VARCHAR(256);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ubma_tenant' AND column_name = 'tenant_secret_prev_at') THEN
        ALTER TABLE ubma_tenant ADD COLUMN tenant_secret_prev_at TIMESTAMPTZ;
    END IF;
END $$;

-- ② RLS 策略就位(ENABLE 不 FORCE —— owner/超级用户连接不受限,零行为变化)
--    注: ubmx_balance_snapshot 不含 tenant_id 列(经 account_id 间接归属),
--    直接策略待未来加列后再上(后置项),本次不纳入。
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'ubmx_account', 'ubmx_posting', 'ubmx_tx_order',
        'ubmx_pre_consume', 'ubmx_freeze', 'ubmx_reconcile_diff'
    ] LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = t) THEN
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
            IF NOT EXISTS (SELECT 1 FROM pg_policies
                           WHERE tablename = t AND policyname = 'tenant_isolation') THEN
                EXECUTE format($f$CREATE POLICY tenant_isolation ON %I
                    USING (tenant_id = current_setting('app.tenant_id', true)::bigint)$f$, t);
            END IF;
        END IF;
    END LOOP;
END $$;
