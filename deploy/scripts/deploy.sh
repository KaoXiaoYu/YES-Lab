#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${YESLAB_ENV_FILE:-$PROJECT_ROOT/deploy/.env.production}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少生产环境文件：$ENV_FILE" >&2
  exit 1
fi

cd "$PROJECT_ROOT"
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "服务器仓库存在未提交修改，已停止部署。" >&2
  exit 1
fi

COMPOSE=(docker compose --env-file "$ENV_FILE")
DATA_ROOT="$(awk -F= '$1 == "YESLAB_DATA_ROOT" { print substr($0, index($0, "=") + 1) }' "$ENV_FILE" | tail -n 1)"
DATA_ROOT="${DATA_ROOT:-/srv/yeslab/data}"

if "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx mysql; then
  YESLAB_ENV_FILE="$ENV_FILE" "$SCRIPT_DIR/backup.sh"
elif [[ -d "$DATA_ROOT/mysql" ]] && find "$DATA_ROOT/mysql" -mindepth 1 -print -quit | grep -q .; then
  echo "检测到已有 MySQL 数据但数据库容器未运行；请先恢复数据库服务，部署已停止。" >&2
  exit 1
fi

git fetch origin main
git merge --ff-only origin/main
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" pull
"${COMPOSE[@]}" up -d mysql

for _ in {1..30}; do
  [[ "$("${COMPOSE[@]}" ps --format json mysql | grep -c '"Health":"healthy"' || true)" -gt 0 ]] && break
  sleep 2
done

"${COMPOSE[@]}" up -d api
for _ in {1..40}; do
  [[ "$("${COMPOSE[@]}" ps --format json api | grep -c '"Health":"healthy"' || true)" -gt 0 ]] && break
  sleep 2
done

if [[ "$("${COMPOSE[@]}" ps --format json api | grep -c '"Health":"healthy"' || true)" -eq 0 ]]; then
  "${COMPOSE[@]}" logs --tail 120 api
  echo "API 未通过健康检查，Web 未切换。请检查迁移日志后使用上一提交 SHA 回滚镜像。" >&2
  exit 1
fi

"${COMPOSE[@]}" up -d web
"${COMPOSE[@]}" ps
echo "部署完成。数据库与上传文件未被 Git 覆盖，升级前备份已保留。"
