-- =============================================================================
-- V1.0.1__seed_tmpl_mock.sql
-- 模板层 mock 数据 — 与 documents/UBM_Mock_Templates.md 一一对应
-- 范围：每个行业选 1-2 个代表作（共 13 个 set + 22 个 item）
-- ID 区间：item 1.9e18 起始；set 2.9e18 起始；ref 3.9e18 起始
-- 上线后此文件应替换为正式的运维导入脚本或迁移到外部 seeding 工具
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) 模板层权益项 — ubmp_benefit_tmpl_item
-- 字段：id, name, icon, description, default_deduction, status, created_at, updated_at, is_deleted
-- -----------------------------------------------------------------------------
INSERT INTO ubmp_benefit_tmpl_item (id, name, icon, description, default_deduction, status, created_at, updated_at, is_deleted) VALUES
  (1900000000000000001, '免邮特权',          'ri-truck-line',           '每月 N 次免邮',                  1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000002, '优先客服',          'ri-customer-service-2-line','24h 专属客服',                 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000003, '专享券包',          'ri-coupon-3-line',        '每月 N 元券包',                  1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000004, '生日礼包',          'ri-gift-2-line',          '生日月礼',                       1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 整包型一次性发放，单次核销扣 1
  (1900000000000000005, '优先发货',          'ri-rocket-2-line',         '当日发顺丰',                     1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000006, '积分加速',          'ri-speed-up-line',         'N 倍积分',                       0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000007, '免广告观看',        'ri-video-line',            '跳过贴片广告',                   0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000008, '4K 画质',          'ri-hd-line',              '4K + HDR',                        0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000009, '抢先看',            'ri-time-line',            '提前看剧集',                     0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000010, '离线下载',          'ri-download-2-line',       '缓存下载',                       0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000011, '多端同步',          'ri-device-line',          '多设备登录',                     0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000012, '无损音质',          'ri-headphone-line',       'FLAC / Hi-Res',                   0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000013, '优先派单',          'ri-taxi-line',            '高峰优先接单',                   0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000014, '打车券包',          'ri-coupon-line',          '每月打车券',                     1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000015, '机场贵宾厅',        'ri-plane-line',            '全球贵宾厅',                     1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000016, '快速安检',          'ri-shield-check-line',    '快速通道',                       1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000017, '云存储扩容',        'ri-cloud-line',           '+N GB',                           0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000018, 'PDF 导出',          'ri-file-pdf-2-line',       '无水印导出',                     0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000019, '去水印',            'ri-markup-line',           '导出无水印',                     0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000020, 'AI 额度',          'ri-robot-2-line',          '每月 N 次 AI 调用',              1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000021, '编程 Token 额度',  'ri-terminal-box-line',     '每月 N token (输入+输出合计)',  1000, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000022, 'AI 代码补全',      'ri-code-s-slash-line',     '行内补全不限次',                  0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000023, 'AI Agent 步数',    'ri-robot-line',            '每月 N 次 Agent 调用',           1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000024, '长上下文窗口',      'ri-window-2-line',         '200K+ token 上下文',             0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000025, '代码库索引',        'ri-search-eye-line',       '跨文件语义检索',                  0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000026, 'AI PR Review',     'ri-git-pull-request-line', '自动 Code Review',               0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000027, '优先抢单',          'ri-flashlight-line',       '抢单加权',                       0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000028, '商家折扣',          'ri-store-2-line',          '合作商家折扣',                   0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000029, '免费停车',          'ri-parking-box-line',      '商户 N 小时停车',                1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000030, '优先登机',          'ri-flight-takeoff-line',   '提前登机',                       1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000031, '免费托运',          'ri-luggage-line',          'N 公斤托运',                     1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000032, '图书折扣',          'ri-book-line',            '电子书折扣',                     0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  (1900000000000000033, '免广告新闻',        'ri-newspaper-line',        '去除信息流广告',                  0, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- -----------------------------------------------------------------------------
-- 2) 模板层权益集 — ubmp_benefit_tmpl_set
-- 字段：id, name, duration, duration_unit, priority, quota, refresh_cycle,
--       refresh_cycle_unit, status, created_at, updated_at, is_deleted
-- -----------------------------------------------------------------------------
INSERT INTO ubmp_benefit_tmpl_set (id, name, duration, duration_unit, priority, quota, refresh_cycle, refresh_cycle_unit, status, created_at, updated_at, is_deleted) VALUES
  -- 电商：京东 PLUS 年卡
  (2900000000000000001, '京东 PLUS 年卡 (参考)', 365, 'day', 10, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- 长视频：爱奇艺星钻 VIP
  (2900000000000000002, '爱奇艺星钻 VIP (参考)', 365, 'day', 10, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- 音乐：网易云黑胶 VIP
  (2900000000000000003, '网易云黑胶 VIP (参考)', 365, 'day', 10, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- 网约车：滴滴橙意
  (2900000000000000004, '滴滴橙意会员 (参考)', 1, 'month', 10, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- 外卖：美团神券
  (2900000000000000005, '美团神券会员 (参考)', 1, 'month', 8, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- O2O：大众点评黑钻
  (2900000000000000006, '大众点评黑钻 (参考)', 365, 'day', 8, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- 工具：WPS 超级会员
  (2900000000000000007, 'WPS 超级会员 (参考)', 365, 'day', 8, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- 出行：携程钻石
  (2900000000000000008, '携程钻石会员 (参考)', 365, 'day', 10, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- 阅读：知乎盐选
  (2900000000000000009, '知乎盐选会员 (参考)', 365, 'day', 8, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- AI 编程：GitHub Copilot Business
  (2900000000000000010, 'GitHub Copilot Business (参考)', 1, 'month', 10, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
  -- AI 编程：Claude Code Pro
  (2900000000000000011, 'Claude Code Pro (参考)', 1, 'month', 10, 0, 1, 'month', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- -----------------------------------------------------------------------------
-- 3) 模板层关联明细 — ubmp_benefit_tmpl_ref
-- 字段：id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit,
--       created_at, updated_at, is_deleted
-- 注：quota = 0 表示不限次；refresh_cycle = 0 + unit='month' 表示不刷新
-- -----------------------------------------------------------------------------

-- 京东 PLUS (set 2900000000000000001)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000001, 2900000000000000001, 1900000000000000001, 5,   1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 免邮 5次/月
  (3900000000000000002, 2900000000000000001, 1900000000000000002, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 优先客服 不限
  (3900000000000000003, 2900000000000000001, 1900000000000000003, 100, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 专享券 100元/月
  (3900000000000000004, 2900000000000000001, 1900000000000000004, 1,   0, 'year',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 生日礼包 1次/年
  (3900000000000000005, 2900000000000000001, 1900000000000000005, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 优先发货 不限
  (3900000000000000006, 2900000000000000001, 1900000000000000006, 5,   1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 积分加速 5倍/月

-- 爱奇艺星钻 (set 2900000000000000002)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000007, 2900000000000000002, 1900000000000000007, 0,  0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 免广告 不限
  (3900000000000000008, 2900000000000000002, 1900000000000000008, 0,  0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 4K 不限
  (3900000000000000009, 2900000000000000002, 1900000000000000009, 0,  0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 抢先看 不限
  (3900000000000000010, 2900000000000000002, 1900000000000000010, 50, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 离线下载 50次/月
  (3900000000000000011, 2900000000000000002, 1900000000000000011, 5,  0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 多端同步 5台

-- 网易云黑胶 (set 2900000000000000003)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000012, 2900000000000000003, 1900000000000000012, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 无损 不限
  (3900000000000000013, 2900000000000000003, 1900000000000000007, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 免广告 不限
  (3900000000000000014, 2900000000000000003, 1900000000000000010, 300, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 离线下载 300次/月

-- 滴滴橙意 (set 2900000000000000004)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000015, 2900000000000000004, 1900000000000000013, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 优先派单 不限
  (3900000000000000016, 2900000000000000004, 1900000000000000014, 50,  1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 打车券 50元/月
  (3900000000000000017, 2900000000000000004, 1900000000000000002, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 优先客服 不限

-- 美团神券 (set 2900000000000000005)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000018, 2900000000000000005, 1900000000000000003, 60, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 专享券 60元/月
  (3900000000000000019, 2900000000000000005, 1900000000000000001, 6,  1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 免邮 6次/月
  (3900000000000000020, 2900000000000000005, 1900000000000000027, 0,  0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 优先抢单 不限
  (3900000000000000021, 2900000000000000005, 1900000000000000004, 1,  0, 'year',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 生日礼包 1次/年

-- 大众点评黑钻 (set 2900000000000000006)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000022, 2900000000000000006, 1900000000000000028, 0, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 商家折扣 不限
  (3900000000000000023, 2900000000000000006, 1900000000000000029, 4, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 免费停车 4小时/月
  (3900000000000000024, 2900000000000000006, 1900000000000000002, 0, 0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 优先客服 不限
  (3900000000000000025, 2900000000000000006, 1900000000000000004, 1, 0, 'year',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 生日礼包 1次/年

-- WPS 超级会员 (set 2900000000000000007)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000026, 2900000000000000007, 1900000000000000017, 100, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 云存储 100GB/月
  (3900000000000000027, 2900000000000000007, 1900000000000000018, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- PDF导出 不限
  (3900000000000000028, 2900000000000000007, 1900000000000000019, 0,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 去水印 不限
  (3900000000000000029, 2900000000000000007, 1900000000000000020, 500, 1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- AI额度 500次/月

-- 携程钻石 (set 2900000000000000008)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000030, 2900000000000000008, 1900000000000000030, 0,  0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 优先登机 不限
  (3900000000000000031, 2900000000000000008, 1900000000000000031, 30, 1, 'year',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 免费托运 30KG/年
  (3900000000000000032, 2900000000000000008, 1900000000000000016, 8,  1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 快速安检 8次/月
  (3900000000000000033, 2900000000000000008, 1900000000000000015, 4,  1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 贵宾厅 4次/月
  (3900000000000000034, 2900000000000000008, 1900000000000000002, 0,  0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 优先客服 不限

-- 知乎盐选 (set 2900000000000000009)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000035, 2900000000000000009, 1900000000000000032, 0, 0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 图书折扣 不限
  (3900000000000000036, 2900000000000000009, 1900000000000000033, 0, 0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 免广告新闻 不限
  (3900000000000000037, 2900000000000000009, 1900000000000000018, 0, 0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- PDF导出 不限

-- GitHub Copilot Business (set 2900000000000000010)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000038, 2900000000000000010, 1900000000000000022, 0,       0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 代码补全 不限
  (3900000000000000039, 2900000000000000010, 1900000000000000021, 300000,  1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- Token 300K/月
  (3900000000000000040, 2900000000000000010, 1900000000000000023, 300,     1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- Agent 300步/月
  (3900000000000000041, 2900000000000000010, 1900000000000000026, 0,       0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- PR Review 不限
  (3900000000000000042, 2900000000000000010, 1900000000000000025, 0,       0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 代码库索引 不限
  (3900000000000000043, 2900000000000000010, 1900000000000000002, 0,       0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 优先客服 不限

-- Claude Code Pro (set 2900000000000000011)
INSERT INTO ubmp_benefit_tmpl_ref (id, set_id, item_id, quota, refresh_cycle, refresh_cycle_unit, created_at, updated_at, is_deleted) VALUES
  (3900000000000000044, 2900000000000000011, 1900000000000000021, 1000000,  1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- Token 1M/月
  (3900000000000000045, 2900000000000000011, 1900000000000000023, 200,      1, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- Agent 200步/月
  (3900000000000000046, 2900000000000000011, 1900000000000000024, 500000,   0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),  -- 上下文 500K
  (3900000000000000047, 2900000000000000011, 1900000000000000025, 0,        0, 'month', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);  -- 代码库索引 不限

-- =============================================================================
-- 完成
-- =============================================================================