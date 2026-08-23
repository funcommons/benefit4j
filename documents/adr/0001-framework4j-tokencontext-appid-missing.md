# ADR-0001: framework4j TokenContext 不填 app_id claim

> **状态**: 已确认 · **日期**: 2026-08-23 · **类型**: 框架限制

## 背景

benefit4j 双模式 remote 落地后, e2e 测试 `seedSubscription` (建 item) 失败, 后端报 `10106 数据操作失败: null value in column "app_id" violates not-null constraint`。

## 根因分析

1. `DefaultBenefitAuthService.postToken` L52 `claims.put("app_id", app.getId())` — claims 存了 app_id (Long 雪花)
2. framework4j `AccessTokenGenerator.generateToken(type, claims)` — 把 claims 存 Redis value (字节码 L284 `put("claims", claims)`), 但 **JWT payload 不含 app_id**
3. JWT decode payload 确认: `{"sub":"APP","type":"APP","exp":...,"nonce":"...","hash":"...","jti":"..."}` — 无 app_id
4. `TokenInterceptor` 填 `TokenContext.set(type, claims, expire)` — claims 从 Redis 或 JWT 取
5. `BenefitTenantController.appId()` 调 `TokenContext.getClaim("app_id")` → **null**
6. `postBenefitItems(appId=null, req)` → `item.setAppId(null)` → PG NOT NULL 违反

## 影响

- **Controller 层 `appId()` 依赖 TokenContext app_id claim, 全 tenant/platform 域接口受影响**
- IT 不受影响 (直接传 appId, 不走 Controller appId())
- e2e 受影响 (走 HTTP → Controller appId() → null)
- 生产 HTTP 调用同样受影响 (三方调 tenant/platform 接口 → app_id null)

## 临时绕过

无 benefit4j 侧绕过 (TokenContext 是 framework4j 管控的 ThreadLocal)。

## 建议修复 (framework4j 侧)

二选一:

### 方案 A: generateToken 存 claims 到 JWT
```java
// AccessTokenGenerator.generateToken: 把 claims 存 JWT payload (而非只存 Redis)
String jwt = Jwts.builder()
    .setClaims(claims)  // 加这一行, 把 app_id 等业务 claims 存 JWT
    .setId(jti)
    .setSubject(type)
    ...
```
拦截器从 JWT 解析 claims → TokenContext, 不依赖 Redis。

### 方案 B: 拦截器从 Redis 取 claims 填 TokenContext
```java
// TokenInterceptor.preHandle: 从 Redis 取 claims (generateToken 存的 value.claims)
Map<String, Object> redisValue = redisTemplate.opsForValue().get(hashKey);
Map<String, Object> claims = (Map) objectMapper.readValue(redisValue.get("claims"));
TokenContext.set(type, claims, expire);
```

## 验证方法

修复后 e2e `subscriptions.spec.ts > seedSubscription` 应通过:
```bash
cd frontend && npx playwright test e2e/subscriptions.spec.ts --reporter=list
```

## 关联

- e2e 7/8 passed, 唯一 failed = seedSubscription (本 issue)
- benefit4j 无法绕过, 需 framework4j v1.2.7+ 修复
