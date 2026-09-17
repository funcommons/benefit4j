#!/bin/bash
# benefit4j 启动脚本 (含 JVM 调优)
# 用法: ./run.sh   或   JAVA_OPTS="-Xms4g -Xmx4g" ./run.sh
#
# JVM 调优说明:
#   -Xms=-Xmx           避免堆扩张停顿 (2g, 生产按机器调)
#   G1GC + 100ms 暂停    压测 P99 98ms 接近, G1 控制 GC 暂停
#   UseStringDeduplication  去重 (ext JSON / OpenID 字符串多)

JAVA_OPTS="${JAVA_OPTS:--Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication}"

DIR="$(cd "$(dirname "$0")/.." && pwd)"
# 动态取最新构建的 fat jar(版本号随发版变化,勿硬编码)
JAR="$(ls -t "$DIR"/benefit4j-app/target/benefit4j-app-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
[[ -n "$JAR" ]] || { echo "未找到 benefit4j-app fat jar, 先 mvn -pl benefit4j-app -am package" >&2; exit 1; }
exec java $JAVA_OPTS -jar "$JAR" "$@"
