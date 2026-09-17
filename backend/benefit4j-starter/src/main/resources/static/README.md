# 勿手改

本目录为前端构建产物入库目录(issue #6 方案 1),由 `bin/build-frontend.sh`
(即 `mvn -pl benefit4j-starter -am -Pwith-frontend package`)生成并随版本提交。

- JitPack / 裸 `mvn package` / CI:只打包本目录已入库产物,零 node 依赖
- 发版流程:改前端 → 跑构建脚本 → 本目录产物随版本一起 commit + tag
