# framework4j-tenant 模块设计方案

> **版本** v1.0 · **更新** 2026-08-28 · **状态** 待评审 · **上游文档** [中间件中台租户设计 v2.1](./中间件中台租户设计.md) · **参考实现** benefit4j(6+1 项安全修复已验证)
>
> **目标** 把租户设计的「契约层」代码化:下一个中间件引入一个依赖 + 少量配置即合规,安全横切面单点维护、修一次全体受益。

---

## 1. 定位与边界

**framework4j-tenant** = 多租户中间件的**租户横切面** Spring Boot Starter。

| 管(横切面) | 不管(业务自理) |
|---|---|
| 租户主表 + 四类配置(§3.1) | 各中间件业务表(自带 tenant_id 列即可) |
| 三域身份守卫(平台/租户双面) | 业务 runtime 逻辑 |
| client_credentials 认证(防爆破/平台合成/宽限期) | 业务错误码/响应封装细节 |
| 密钥生命周期(reset/撤销会话/双版本) | 邮件/短信等通知渠道 |
| 注册码通道(发码/原子扣减/吊销) | OEM 前端(→ fc-web-sdk) |
| RLS 助手(策略迁移 + 连接层 SET) | 业务侧对账/计量 |

**依赖的 framework4j 模块**:accesstoken(会话/key 结构)、id(OpenID)、web(ApiResponse)、redis、audit、sensitive(AES-GCM 加密 TypeHandler)、datasource(MyBatis Plus)。

## 2. 关键设计决策

### D-1 租户表:框架自有表 `f4j_tenant`(非可配表名)

| 方案 | 判定 |
|---|---|
| **A. 框架自有表 `f4j_tenant`,DDL 由模块自带迁移统一管理** ✅ | 表结构 SSOT 在框架,杜绝各项目 DDL 漂移(这正是 10 年契约层的代码化);下游一次数据迁移即可 |
| B. 表名可配(DynamicTableName 映射到各项目现有表) | 零迁移但结构漂移不可控,框架升级被各项目魔改拖累 |

`f4j_tenant` 即文档 §3.1 的 `xmp_tenants` 原样(列名/约束一字不改,契约层冻结);`tenant_id` 语义 = `f4j_tenant.id`。

### D-2 三域守卫:注解 + 拦截器(替代各项目手工 @ModelAttribute)

```java
@PlatformDomain                    // 平台域: 仅平台身份(tenant_id==0)可达
@RestController @RequestMapping("/benefit/api/v1/platform/tenants")
public class TenantController { ... }

@TenantDomain                      // 租户域: 仅真实租户身份(tenant_id>0)可达
@RestController @RequestMapping("/benefit/api/v1/runtime/**")
public class XxxRuntimeController { ... }
```

拦截器扫描 handler 所属 controller 的注解统一校验,401(型别)/403(身份)语义与文档一致。**注解只定规则,拦截器按 path 注册才生效**(benefit4j 踩坑:注解≠自动触发)。

### D-3 认证端点:框架内置可关

模块自动注册 `POST {auth-path}`(默认 `/api/v1/auth/token`,可配),含防爆破/平台合成租户/宽限期双版本全套;`enabled=false` 时项目可自带端点、逻辑委托 `TenantAuthTemplate`。内置端点默认放行于 access-token 的 exclude-path(自动配置代填)。

### D-4 会话撤销:复用 accesstoken 的 key 结构

`TenantSessionRevoker` 按 `TokenKeyBuilder.accessMetadata(appName, type, hash)` 删 APP/OPS 两型 key(benefit4j 已验证的算法,源码同仓零猜测)。

## 3. 模块组成(七件套)

| # | 组件 | 内容 | benefit4j 对应(迁移源) |
|---|---|---|---|
| 1 | `f4j_tenant` 表 + `Tenant`/`TenantMapper` | 自带迁移(幂等);四类配置 JSONB;secret AES-GCM(LazyEncryptedFieldTypeHandler) | `ubma_tenant` + `UbmaTenantMapper`(数据迁移) |
| 2 | `@PlatformDomain` / `@TenantDomain` + `DomainGuardInterceptor` | 双面守卫(认 0 / 拒 0),401/403 映射 | `PlatformIdentityGuard` / `TenantIdentityGuard` + 手工 @ModelAttribute |
| 3 | `TenantAuthTemplate` + 内置 `TenantAuthEndpoint` | client_credentials、防爆破(5 次/15min,429)、平台合成租户(id=0)、宽限期双版本比对 | `DefaultBenefitAuthService` |
| 4 | `TenantSecretService` | reset(旧钥入 prev + 撤销全部会话)、明文只显一次、脱敏 | `DefaultBenefitPlatformService#postTenantsTenantIdSecret` + revoke |
| 5 | `RegistrationKeyService` + 开放域端点(可选) | 发码(次数/有效期/预绑配置档)、Redis 原子扣减、吊销、凭码即 ACTIVE | 无(新能力,文档 §6.2) |
| 6 | `UserIdContext` | `X-User-Id` 请求头解析(ThreadLocal,永不鉴权红线在 Javadoc+运行时断言) | 各 controller 手工取参 |
| 7 | `RlsAssistant` | 策略 SQL 生成(ENABLE 不 FORCE 起步)+ 连接层 `set_config` Filter(FULL 模式,配合 FORCE) | V1.4.1 迁移(手工 SQL) |

## 4. 配置面(全部有默认,零配置可用)

```yaml
framework4j:
  tenant:
    enabled: true
    auth:
      enabled: true                  # 内置认证端点
      path: /api/v1/auth/token
      max-fail: 5
      lock-minutes: 15
      token-type: TENANT             # 签发的 token 型别名(默认 TENANT,可配 APP 兼容存量)
      expire-seconds: 28800          # 8h(文档 §5.2 上限 12h)
    platform:
      client-id: ${PLATFORM_CLIENT_ID:PLATFORM}
      client-secret: ${PLATFORM_CLIENT_SECRET:}
    secret:
      grace-hours: 24
    registration-key:
      enabled: false                 # 通道 B,按需开
      default-uses: 1
      default-ttl-hours: 24
    rls:
      mode: OFF                      # OFF / POLICY(就位不 FORCE) / FULL(连接层 SET + FORCE)
```

**兼容开关**(benefit4j 迁移期):`token-type: APP` 使签发 token 型别与存量一致,存量会话不失效。

## 5. tenant-tck(合规测试集,独立 test-jar)

文档 §10 checklist 的机器可执行版,任何项目(哪怕不用本模块)引依赖跑绿即合规:

- 结构断言:租户表列/唯一键、业务表 tenant_id+索引打头(扫 information_schema)
- 行为断言:双面守卫(平台 token↔租户域 403、租户 token↔平台域 403)、防爆破锁定、reset 撤销会话、宽限期新旧钥、X-User-Id 不鉴权
- 用法:`testImplementation('...:framework4j-tenant-tck')` + 继承 `TenantComplianceSuite` 提供端点映射

## 6. benefit4j 迁移路径(零 API 破坏)

| 阶段 | 动作 | 风险 |
|---|---|---|
| P1 | framework4j v1.5.0 发布模块(不含 benefit4j 改动) | 零 |
| P2 | benefit4j 升依赖,删自有 guard/auth/secret 实现,controller 换 `@PlatformDomain`/`@TenantDomain`;`ubma_tenant → f4j_tenant` 数据迁移(V1.5.0:INSERT SELECT + 老表重命名归档);`token-type: APP` 兼容存量 | 中:全量 IT+smoke 回归;数据迁移可逆(老表保留) |
| P3 | 接入 tenant-tck;前端无感(token 透传) | 低 |
| P4 | 观察一个版本后删兼容开关(token-type 切 TENANT,存量 token 失效窗口公告) | 低 |

## 7. 实施计划(framework4j 仓,7 步,每步独立可交付)

| 步 | 内容 | 估时 | 验证 |
|---|---|---|---|
| 1 | 模块骨架(pom/自动配置/Properties)+ 依赖版本对齐 | 0.5d | demo 启动 |
| 2 | f4j_tenant 迁移+实体+Mapper+CRUD(加密/脱敏) | 1d | 模块 IT(CRUD/唯一索引) |
| 3 | 双守卫注解+拦截器+异常映射 | 0.5d | IT(双面 403/放行) |
| 4 | TenantAuthTemplate+内置端点(防爆破/合成租户/宽限期) | 1d | IT(移植泛化 TenantSecurityIT) |
| 5 | SecretService(reset 撤销会话)+ RegistrationKeyService | 1d | IT |
| 6 | UserIdContext + RlsAssistant(OFF/POLICY/FULL) | 0.5d | IT |
| 7 | tenant-tck test-jar + framework4j 文档 | 1d | benefit4j 试接入跑 tck |
| — | **合计** | **~5.5d** | 打 tag v1.5.0 → JitPack |

## 8. 风险与对策

| 风险 | 对策 |
|---|---|
| 双仓库联动(benefit4j 依赖未发布版本) | 严格按 P1 先发布后接入;本地 install 联调时注意 `-am` 与本地库旧 jar 陷阱(已记入记忆) |
| f4j_tenant 数据迁移丢配置 | 老表重命名归档不删,迁移脚本幂等可重放,回滚 = 改回表名 |
| 各项目存量 token 型别不一 | `token-type` 兼容开关 + P4 观察期切换 |
| 模块演进与文档契约层脱钩 | 模块 CHANGELOG 引用文档条款号(§x.y);tck 断言与 §10 checklist 一一对应 |

## 9. 与既有体系的关系

```
中间件中台租户设计 v2.1(契约层冻结 —— SSOT)
   ├── framework4j-tenant      后端实现方(本方案)
   ├── fc-web-sdk              前端实现方(useEmbedToken/useEmbedParams/四入口骨架 —— 后续沉淀)
   └── tenant-tck              双向验收(§10 的机器版)
```
