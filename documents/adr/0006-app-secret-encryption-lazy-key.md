# ADR-0006: app_secret AES-256-GCM 加密 + TypeHandler lazy key

> **状态**: 已实施 · **日期**: 2026-08 · **版本**: V1.2.2

## 背景

`ubma_application.app_secret` 明文存储(安全风险)。需加密 + 自动加解密,但 framework4j `EncryptedFieldTypeHandler` 无无参构造(MyBatis 反射实例化失败)。

## 决策

自定义 `BenefitAppSecretTypeHandler` 继承 `BaseTypeHandler<String>`,**lazy key**(每次加解密时从 `SpringContextHolder` 取 `sensitiveAesKeyBytes` Bean),而非构造时取。

## 理由
- framework4j `EncryptedFieldTypeHandler` 构造需 32 字节 key,MyBatis `TypeHandlerRegistry.getInstance` 反射无参构造 → 失败
- 构造时取 key:Mapper 解析早于 SpringContextHolder aware → fallback key(与生产 key 不一致)→ 加密/解密错位
- lazy key:运行时(set/get)取,context 必就绪,保证 insert/select 用同一真 key

## 后果
- ✅ app_secret DB 存密文,读回明文,自动加解密
- ✅ 单测(mock 无容器)fallback key 兜底
- ✅ reset-secret 用 wrapper.set 手动 AesGcm 加密(绕 ext JSONB update cast)
- ⚠️ 每次加解密取 Bean(HashMap lookup,开销可忽略)
- ⚠️ ext 字段 JSONB update cast 需 `jdbcType=JdbcType.OTHER` + `?stringtype=unspecified`(治本)
