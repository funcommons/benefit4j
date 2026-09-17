# Changelog

本文件记录 benefit4j（用户权益中台）的版本演进。

## [未发布] 2026-09-17

### 新增 — 控制台入 starter classpath,单部署物(issue #6)

家族前端部署统一化裁决落地(对齐 lotask4j#4 / token-hub#14,家族首个实现):

- **产物入库(方案 1)**: 前端构建产物提交进 `benefit4j-starter/src/main/resources/static/`(git 跟踪,~8.4MB);JitPack/裸 `mvn package`/CI 只打包已入库产物,**零 node 依赖**;发版期 `bin/build-frontend.sh`(= `mvn -pl benefit4j-starter -am -Pwith-frontend package`)刷新产物随版本提交,`--check` 模式按 git 提交时间防 stale
- **SPA fallback**: `Benefit4jConsoleAutoConfiguration` + `PathResourceResolver`——真实静态资源优先;未命中仅对非 API 前缀 GET fallback 到 `index.html`;排除前缀默认 `/benefit/api/`、`/api/`、`/open/`、`/actuator`、`/error`(含 framework4j-tenant 认证端点),`benefit4j.console.fallback-excludes` 可配;末段带扩展名的路径不吞(静态资源 404 语义)
- **开关**: `benefit4j.console.enabled` 默认 **true**(家族终态口径,壳零配置;消费方可关闭或声明自有 configurer bean 覆盖);产物缺失时 fallback 自然失效返回 404
- **构建指纹**: `build-manifest.json` 随产物生成(version + builtAt),`GET /build-manifest.json` 可查
- **前端零改动**: `BASE_URL=''` 相对路径天然同源;dev vite 热更/proxy 路径不动
- **验收口径修正**(原标准 3): `mvn package -Pwith-frontend` 产出含最新前端的 jar 并刷新入库产物;裸 `mvn package` 打包已入库产物
- **回归**: 后端 449/449(IT 含 smoke + 单测,新增 fallback 11 例);前端 build(vue-tsc + vite)绿、vitest 非 sdk 262 绿(sdk 79 为既有红基线,禁区不动);真进程验证 9200 六项全过(`/` 出控制台、深链接 200、API/认证/静态资源 404 语义、build-manifest 可查)

## [未发布] 2026-08-28

### 变更 — 术语彻改:应用(app) → 租户(tenant)

零业务规则变更的纯重命名(趁零外部接入方窗口完成,API 为破坏性变更):

- **DB**: V1.4.0 迁移——22 列 `app_id`→`tenant_id`、`ubma_application`→`ubma_tenant`(`app_secret`→`tenant_secret`)、37 索引/约束名同步;分区父表改名自动传播;幂等可重放
- **Java**: `UbmaApplication`→`UbmaTenant`、~1000 处 `appId/app_id`→`tenantId/tenant_id`;JWT claim `app_id`→`tenant_id`(存量 token 失效);it/app yml 双键(`appid`/`app_id`)收敛为 `[tenant_id]`
- **API**: `/platform/applications`→`/platform/tenants`,query/path 字段同步;`remote-app-id` 配置→`remote-tenant-id`
- **前端**: `Apps.vue`→`Tenants.vue`、路由 `/apps`→`/tenants`、i18n 全量「应用」→「租户」
- **不变**: token 型别 APP/OPS(技术角色)、`PLATFORM_CLIENT_ID/SECRET`(平台凭据)、`client_id/client_secret`(OAuth 参数)、`@OpenId`
- **回归**: 后端 421/421(IT 146 含 smoke 4 + 单测 279)、前端 typecheck/build/vitest 基线一致;真实进程验证 `/platform/tenants` CRUD + smoke 4/4

### 修复 — 测试基建

- vitest 误扫 `e2e/`(Playwright 用例)致 `npm test` 恒挂 79 个 → 显式 exclude

## [1.0.0] - 2026-08-23

首个正式版本。覆盖权益全生命周期：发放 → 核销 → 退款 → 补偿，多源额度桶 + TCC 两阶段扣减，双模式集成（local / remote / 独立部署）。

### 新增 — 核心功能

- **多源额度桶**：单订阅挂多桶（SUBSCRIPTION/TOPUP/COMPENSATION），按 `bucket_priority` 排空，跨桶扣减。扩展 `ubma_subscribe_item` 而非新表（见 [ADR-0002](documents/adr/0002-multi-source-bucket-extend-subscribe-item.md)）。
- **TCC 两阶段扣减**：`reserve`（预扣）→ `commit`（确认）/ `release`（回滚），并发乐观锁 + version 重试 2 次，partialAllowed 部分扣减 / 严格模式整笔拒绝。
- **补偿与退款**：`POST /runtime/refunds` 退款回桶，`POST /tenant/compensations` ADD/SUB 调账（建桶/扣减），状态机 ACTIVE↔EXHAUSTED / →CANCELED / →EXPIRED。
- **批量发放**：`POST /tenant/subscriptions/batch` 数组循环 + `external_order_id` 幂等。
- **对账与归档**：`POST /ops/reconcile` 差异报告，`POST /ops/jobs/archive-consumes` 旧分区 detach/归档。

### 新增 — 双模式架构

- **local / remote / 独立部署** 三形态，`HttpTransport` 抽象（RestTemplate 默认 / WebClient 响应式可选），`AuthenticatedHttpTransport` 装饰器注入 S2S JWT + HMAC 签名 + Resilience4j 重试（3 次×500ms）。54 个 remote client 方法（见 [ADR-0003](documents/adr/0003-dual-mode-starter.md)）。

### 新增 — 安全与资金安全

- **framework4j 13 模块全接入**：`@RequiresToken` 鉴权 / `@RequiresSignature` 接口签名 / `@RateLimit` 限流 / `Idempotency-Key` 幂等 / `@Auditable` 审计 + Hash Chain / `@Sensitive` AES-256-GCM 字段加密 / SQL 追踪。
- **runtime 域 HMAC-SHA256 签名**：前端 `benefitClient` 拦截器对 `/benefit/api/v1/runtime/**` 注入 `X-Access-Key/X-Timestamp/X-Nonce/X-Signature`，与后端 `framework4j-signature` 对齐（`METHOD\nPATH\nTS\nNONCE\nBODY_MD5_HEX` + Base64 HMAC-SHA256）。
- **P0 资金安全**：扣减并发重试 + partialAllowed=false 不足回滚 + `DuplicateKey→409` / `RateLimit→429` / `Signature→401` 异常 handler + 兜底 500。
- **tenant_secret AES-256-GCM 加密**：`BenefitAppSecretTypeHandler` lazy key（见 [ADR-0006](documents/adr/0006-app-secret-encryption-lazy-key.md)）。

### 新增 — 性能与数据

- **连接池 + JVM 调优**：Druid max-active=50 / Redis pool=50 / G1GC -Xmx2g，单实例 QPS 3604（+68%），P99 27ms（-41%）。
- **PG 原生分区**：`ubma_consume` 按 `created_at` 月分区，主键 `(id, created_at)`（见 [ADR-0007](documents/adr/0007-pg-native-partition-not-shardingsphere.md)）。
- **outbox 事件表**：本地事务写业务+outbox 原子，`OutboxPublisher` @Scheduled poll PENDING→SENT，拆服务时改 MQ 零改业务（见 [ADR-0004](documents/adr/0004-outbox-not-seata.md)）。
- **Flyway schema 演进**：`spring.flyway.url` 独立 datasource 绕过 Druid Wall（见 [ADR-0005](documents/adr/0005-flyway-druid-wall-bypass.md)）。

### 新增 — 测试

- **e2e 67 个**：smoke 12（关键写路径冒烟）+ regression 55（视觉回归 light/dark + 响应式 3 viewport + 交互 + 失败态拦截器 401/429/409 + 路由守卫），Playwright projects 分层。
- **集成测试 60 + 单元测试 273**，全绿。

### 新增 — 文档

- 产品说明书 / 部署手册 / 压测报告与容量规划 / 接入指南 / 7 篇 ADR。

### 依赖

- backend: Spring Boot 3.2.7 / MyBatis Plus 3.5.7 / Druid 1.2.22 / Redisson 3.27.0 / framework4j 1.2.8 / Flyway 9.22.3 / PostgreSQL 16+
- frontend: Vue 3.5 / Pinia 3 / Element Plus 2.13 / Playwright 1.62.1 / Vite 7

### 已知限制（后续版本）

- #5 `ubma_migration` 跨 set 升降级未实现（核心业务缺口）
- #12 webhook/事件通知（outbox 无消费者）
- #13 灰度/AB、#14 计费报表、#15 实体去 Ubma 前缀、#16 DTO 去动词前缀
