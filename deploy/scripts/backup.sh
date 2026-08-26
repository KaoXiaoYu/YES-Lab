#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${YESLAB_ENV_FILE:-$PROJECT_ROOT/deploy/.env.production}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少生产环境文件：$ENV_FILE" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

BACKUP_ROOT="${YESLAB_BACKUP_ROOT:-/srv/yeslab/backups}"
DATA_ROOT="${YESLAB_DATA_ROOT:-/srv/yeslab/data}"
RETENTION_DAYS="${YESLAB_BACKUP_RETENTION_DAYS:-7}"
if [[ ! "$RETENTION_DAYS" =~ ^[0-9]+$ ]]; then
  echo "YESLAB_BACKUP_RETENTION_DAYS 必须是非负整数" >&2
  exit 1
fi
if [[ "$BACKUP_ROOT" != /* || "$BACKUP_ROOT" == "/" ]]; then
  echo "YESLAB_BACKUP_ROOT 必须是安全的绝对路径" >&2
  exit 1
fi
if [[ "$DATA_ROOT" != /* || "$DATA_ROOT" == "/" ]]; then
  echo "YESLAB_DATA_ROOT 必须是安全的绝对路径" >&2
  exit 1
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DESTINATION="$BACKUP_ROOT/$STAMP"
install -d -m 0750 "$DESTINATION"

cd "$PROJECT_ROOT"
COMPOSE=(docker compose --env-file "$ENV_FILE")
if ! "${COMPOSE[@]}" ps --status running --services | grep -qx mysql; then
  echo "MySQL 容器未运行，拒绝生成不完整备份。" >&2
  exit 1
fi

"${COMPOSE[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --user=root --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF yeslab' \
  > "$DESTINATION/yeslab.sql"

if [[ -d "$DATA_ROOT/uploads" ]]; then
  tar -C "$DATA_ROOT" -czf "$DESTINATION/uploads.tar.gz" uploads
fi

sha256sum "$DESTINATION"/* > "$DESTINATION/SHA256SUMS"
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime "+$RETENTION_DAYS" -print -exec rm -rf -- {} +
echo "备份完成：$DESTINATION"
