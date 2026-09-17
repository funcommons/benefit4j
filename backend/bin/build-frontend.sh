#!/usr/bin/env bash
# 前端产物构建入库脚本 (issue #6 方案 1: dist 产物入库)
#
# 用法:
#   bin/build-frontend.sh          构建前端 → 刷新 starter 入库产物 → 提示提交
#   bin/build-frontend.sh --check  防 stale 校验: frontend 源码晚于入库产物提交则失败 (供 CI/本地自查)
#
# 背景: 消费方走 JitPack (com.github 坐标) 源码构建, JitPack 无 node 环境,
# 故构建产物提交进 git (backend/benefit4j-starter/src/main/resources/static/),
# 默认构建零 node 依赖; 本脚本只在发版期本地运行。
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$BACKEND_DIR/.." && pwd)"
STATIC_REL="backend/benefit4j-starter/src/main/resources/static"

if [[ "${1:-}" == "--check" ]]; then
  # 产物含构建时间戳 (vite define __BUILD_TIME__), 非可复现构建, 字节 diff 无意义;
  # 改用 git 历史比对: 前端源码最后提交时间 vs 入库产物最后提交时间
  src_ts="$(git -C "$REPO_ROOT" log -1 --format=%ct -- frontend/src frontend/package.json frontend/pnpm-lock.yaml frontend/vite.config.ts)"
  static_ts="$(git -C "$REPO_ROOT" log -1 --format=%ct -- "$STATIC_REL" || true)"
  if [[ -z "${static_ts}" ]]; then
    echo "❌ 入库产物从未提交过, 请先运行 bin/build-frontend.sh" >&2
    exit 1
  fi
  if [[ -n "${src_ts}" && "${src_ts}" -gt "${static_ts}" ]]; then
    echo "❌ 入库产物落后于前端源码提交 (src: $(date -r "${src_ts}" '+%F %T'), static: $(date -r "${static_ts}" '+%F %T'))" >&2
    echo "   请运行 bin/build-frontend.sh 刷新并提交" >&2
    exit 1
  fi
  echo "✅ 入库产物与前端源码同步"
  exit 0
fi

echo "== 构建前端并刷新入库产物 (-Pwith-frontend) =="
(cd "$BACKEND_DIR" && mvn -pl benefit4j-starter -am -Pwith-frontend package -DskipTests)

echo
echo "== 入库产物变更 =="
git -C "$REPO_ROOT" status -s -- "$STATIC_REL" || true
echo
echo "提示: 产物需随版本提交入库:"
echo "  git add $STATIC_REL && git commit"
