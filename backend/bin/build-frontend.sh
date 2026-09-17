#!/usr/bin/env bash
# 前端产物构建入库脚本 (issue #6 方案 1: 前端本地 build,产物进 git)
#
# 用法:
#   bin/build-frontend.sh          本机构建前端 → 刷新 starter 入库产物 → 提示提交
#   bin/build-frontend.sh --check  防 stale 校验: frontend 源码晚于入库产物提交则失败 (供 CI/本地自查)
#
# 背景: 消费方经 JitPack/本地库拿到的 starter jar 只含已入库产物,构建链零 node 依赖;
# 前端构建只发生在发版期本机(pnpm 用本机环境,不经 maven)。
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "$BACKEND_DIR/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"
STATIC_REL="backend/benefit4j-starter/src/main/resources/static"
STATIC_DIR="$REPO_ROOT/$STATIC_REL"

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

echo "== 本机构建前端 (pnpm install + build) =="
(cd "$FRONTEND_DIR" && pnpm install --frozen-lockfile && pnpm build)

echo "== 刷新入库产物 → $STATIC_REL =="
# 清旧产物(保留 README.md 说明文件),拷贝新产物
rsync -a --delete --exclude='README.md' "$FRONTEND_DIR/dist/" "$STATIC_DIR/"

# 构建指纹(版本号取自 backend/pom.xml 的 benefit4j-parent)
VERSION="$(grep -A1 '<artifactId>benefit4j-parent</artifactId>' "$BACKEND_DIR/pom.xml" | grep -o '<version>[^<]*' | head -1 | sed 's/<version>//')"
printf '{"version":"%s","builtAt":"%s"}\n' "$VERSION" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" > "$STATIC_DIR/build-manifest.json"
echo "build-manifest.json: $(cat "$STATIC_DIR/build-manifest.json")"

echo
echo "== 入库产物变更 =="
git -C "$REPO_ROOT" status -s -- "$STATIC_REL" || true
echo
echo "提示: 产物需随版本提交入库:"
echo "  git add $STATIC_REL && git commit"
