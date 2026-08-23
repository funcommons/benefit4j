# ADR-0003: 双模式架构 (user-starter 模式)

> **状态**: 已实施 · **日期**: 2026-08 · **版本**: v1.0+

## 背景

benefit4j 需适配三类部署:业务方同进程嵌入 / 跨进程调用 / 独立部署。需选架构模式。

## 决策

user-starter 模式:一份 jar + 两个 yml 开关(`mode` + `enable-api`),业务方只 inject Client 接口。

## 理由
- 参考 iam4j 双模式技术方案(user-spring-boot-starter 通用模式)
- 业务方代码零感知 local/remote(由 yml 决定 Client 实现)
- 开闭原则:HttpTransport 可替换(RestTemplate/WebClient/gRPC)

## 后果
- ✅ 三模式适配(local+enable-api=false 嵌入 / local+true 独立 / remote 跨进程)
- ✅ 4 Client 54 方法全真实实现(invoke + S2S JWT + 签名 + 重试)
- ⚠️ 单 starter 4 Client(未按域拆,域耦合紧收益低)
- ⚠️ remote S2S JWT 需业务方配 framework4j-access-token (Redis)
