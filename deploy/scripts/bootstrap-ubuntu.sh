#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_ROOT/deploy/.env.production"
SITE_ADDRESS=""
ADMIN_USERNAME="teacher"
ADMIN_DISPLAY_NAME="汤洪大王"
ADMIN_MEMBER_CODE="T-001"

usage() {
  cat <<'EOF'
用法：
  sudo ./deploy/scripts/bootstrap-ubuntu.sh --domain lab.example.com [选项]

选项：
  --domain DOMAIN        正式域名，不含 http:// 或路径（必填）
  --admin-user USER      首个管理员登录名，默认 teacher
  --admin-name NAME      首个管理员显示名，默认 汤洪大王
  --admin-code CODE      首个管理员内部编号，默认 T-001
  --help                 显示帮助

脚本只支持 Ubuntu 24.04 LTS。重复执行不会覆盖生产环境配置、数据库或上传文件。
EOF
}

fail() {
  echo "错误：$*" >&2
  exit 1
}

on_error() {
  local exit_code=$?
  echo "部署在第 ${BASH_LINENO[0]} 行停止（退出码 $exit_code）。已有数据库和上传文件没有被删除。" >&2
  exit "$exit_code"
}
trap on_error ERR

while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain)
      [[ $# -ge 2 ]] || fail "--domain 缺少值"
      SITE_ADDRESS="$2"
      shift 2
      ;;
    --admin-user)
      [[ $# -ge 2 ]] || fail "--admin-user 缺少值"
      ADMIN_USERNAME="$2"
      shift 2
      ;;
    --admin-name)
      [[ $# -ge 2 ]] || fail "--admin-name 缺少值"
      ADMIN_DISPLAY_NAME="$2"
      shift 2
      ;;
    --admin-code)
      [[ $# -ge 2 ]] || fail "--admin-code 缺少值"
      ADMIN_MEMBER_CODE="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "未知参数：$1"
      ;;
  esac
done

[[ "$EUID" -eq 0 ]] || fail "请使用 sudo 或 root 执行此脚本"
[[ -f /etc/os-release ]] || fail "无法识别操作系统"
# shellcheck disable=SC1091
source /etc/os-release
[[ "${ID:-}" == "ubuntu" && "${VERSION_ID:-}" == "24.04" ]] \
  || fail "此脚本仅支持 Ubuntu 24.04 LTS，当前为 ${PRETTY_NAME:-未知系统}"
[[ "$PROJECT_ROOT" != *[[:space:]]* ]] || fail "服务器项目路径不能包含空格：$PROJECT_ROOT"
[[ -d "$PROJECT_ROOT/.git" && -f "$PROJECT_ROOT/compose.yaml" ]] \
  || fail "请先将 YES-Lab 仓库克隆到服务器，再从仓库内运行脚本"

if [[ -z "$SITE_ADDRESS" && ! -f "$ENV_FILE" && -t 0 ]]; then
  read -r -p "请输入已解析到本服务器的域名（不含 https://）：" SITE_ADDRESS
fi
if [[ ! -f "$ENV_FILE" ]]; then
  [[ -n "$SITE_ADDRESS" ]] || fail "首次部署必须使用 --domain 指定域名"
  [[ "$SITE_ADDRESS" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] \
    || fail "域名格式无效；不要包含协议、端口、路径或空格"
fi
[[ "$ADMIN_USERNAME" =~ ^[A-Za-z0-9._-]{1,64}$ ]] \
  || fail "管理员登录名仅允许字母、数字、点、下划线和连字符"
[[ -n "$ADMIN_DISPLAY_NAME" && ${#ADMIN_DISPLAY_NAME} -le 80 ]] \
  || fail "管理员显示名长度必须为 1—80 位"
[[ "$ADMIN_MEMBER_CODE" =~ ^[A-Za-z0-9._-]{1,64}$ ]] \
  || fail "管理员内部编号仅允许字母、数字、点、下划线和连字符"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git openssl

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  conflicting_packages=()
  for package_name in docker.io docker-compose docker-compose-v2 docker-doc docker-buildx podman-docker containerd runc; do
    if dpkg-query -W -f='${db:Status-Status}' "$package_name" 2>/dev/null | grep -qx installed; then
      conflicting_packages+=("$package_name")
    fi
  done
  if [[ ${#conflicting_packages[@]} -gt 0 ]]; then
    fail "检测到可能冲突的 Docker 包：${conflicting_packages[*]}。为避免影响已有容器，脚本不会自动卸载，请先按 Docker 官方文档处理。"
  fi

  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
docker compose version

if [[ -z "$(swapon --show=NAME --noheadings)" ]]; then
  [[ ! -e /swapfile ]] || fail "/swapfile 已存在但未启用；为避免覆盖未知文件，请先人工检查"
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -qF '/swapfile none swap sw 0 0' /etc/fstab \
    || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  cat > /etc/sysctl.d/90-yeslab-memory.conf <<'EOF'
vm.swappiness=10
EOF
  sysctl --system >/dev/null
  echo "已创建并启用 2 GB swap。"
else
  echo "检测到系统已有 swap，保持现状。"
fi

if [[ ! -f "$ENV_FILE" ]]; then
  umask 077
  database_password="$(openssl rand -hex 24)"
  mysql_root_password="$(openssl rand -hex 24)"
  jwt_secret="$(openssl rand -hex 64)"
  cat > "$ENV_FILE" <<EOF
YESLAB_SITE_ADDRESS=$SITE_ADDRESS
YESLAB_CORS_ALLOWED_ORIGINS=https://$SITE_ADDRESS
YESLAB_IMAGE_TAG=latest
YESLAB_API_IMAGE=ghcr.io/kaoxiaoyu/yes-lab-api
YESLAB_WEB_IMAGE=ghcr.io/kaoxiaoyu/yes-lab-web
YESLAB_DATA_ROOT=/srv/yeslab/data
YESLAB_DATABASE_USERNAME=yeslab
YESLAB_DATABASE_PASSWORD=$database_password
YESLAB_MYSQL_ROOT_PASSWORD=$mysql_root_password
YESLAB_JWT_SECRET=$jwt_secret
YESLAB_JWT_ISSUER=yes-lab-api
YESLAB_BACKUP_ROOT=/srv/yeslab/backups
YESLAB_BACKUP_RETENTION_DAYS=7
EOF
  chmod 600 "$ENV_FILE"
  echo "已生成仅服务器保存的生产配置：$ENV_FILE"
else
  echo "检测到已有生产配置，保持不变：$ENV_FILE"
fi

data_root="$(awk -F= '$1 == "YESLAB_DATA_ROOT" { print substr($0, index($0, "=") + 1) }' "$ENV_FILE" | tail -n 1)"
backup_root="$(awk -F= '$1 == "YESLAB_BACKUP_ROOT" { print substr($0, index($0, "=") + 1) }' "$ENV_FILE" | tail -n 1)"
data_root="${data_root:-/srv/yeslab/data}"
backup_root="${backup_root:-/srv/yeslab/backups}"
[[ "$data_root" == /* && "$data_root" != "/" ]] || fail "YESLAB_DATA_ROOT 必须是安全的绝对路径"
[[ "$backup_root" == /* && "$backup_root" != "/" ]] || fail "YESLAB_BACKUP_ROOT 必须是安全的绝对路径"
install -d -m 0750 "$data_root/mysql" "$data_root/uploads" "$backup_root"
chown -R 999:999 "$data_root/mysql"
chown -R 10001:10001 "$data_root/uploads"

cd "$PROJECT_ROOT"
COMPOSE=(docker compose --env-file "$ENV_FILE")
"${COMPOSE[@]}" config --quiet

if ! "${COMPOSE[@]}" pull; then
  cat >&2 <<'EOF'
无法拉取 GHCR 镜像。请先确认 GitHub Actions 已成功发布两个镜像，并将
yes-lab-api 与 yes-lab-web 软件包设为 Public；若保持 Private，请先使用
具有 read:packages 权限的 GitHub classic PAT 执行 docker login ghcr.io。
EOF
  exit 1
fi

"${COMPOSE[@]}" up -d --wait --wait-timeout 180 mysql

account_role="$("${COMPOSE[@]}" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT role FROM accounts WHERE LOWER(username) = LOWER(\"'"$ADMIN_USERNAME"'\") LIMIT 1" 2>/dev/null' \
  || true)"

initial_password=""
if [[ -z "$account_role" ]]; then
  initial_password="$(openssl rand -base64 24 | tr -d '\n')"
  echo "正在创建首个教师管理员账号（不会创建演示账号或演示项目）……"
  echo "请现在保存管理员凭据：用户名 $ADMIN_USERNAME，初始密码 $initial_password"
  YESLAB_INITIAL_ADMIN_ENABLED=true \
  YESLAB_INITIAL_ADMIN_USERNAME="$ADMIN_USERNAME" \
  YESLAB_INITIAL_ADMIN_PASSWORD="$initial_password" \
  YESLAB_INITIAL_ADMIN_DISPLAY_NAME="$ADMIN_DISPLAY_NAME" \
  YESLAB_INITIAL_ADMIN_MEMBER_CODE="$ADMIN_MEMBER_CODE" \
    "${COMPOSE[@]}" up -d --wait --wait-timeout 240 api

  account_role="$("${COMPOSE[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT role FROM accounts WHERE LOWER(username) = LOWER(\"'"$ADMIN_USERNAME"'\") LIMIT 1"' \
    | tr -d '\r')"
  [[ "$account_role" == "TEACHER" ]] || fail "首个管理员账号创建后校验失败"

  # 立即用默认关闭状态重建 API，避免初始密码继续留在容器环境变量中。
  "${COMPOSE[@]}" up -d --force-recreate --wait --wait-timeout 180 api
elif [[ "$account_role" != "TEACHER" && "$account_role" != "CORE_STUDENT" ]]; then
  fail "账号 $ADMIN_USERNAME 已存在但不是管理员；请使用 --admin-user 指定其他登录名"
fi

"${COMPOSE[@]}" up -d --wait --wait-timeout 180 web

cat > /etc/systemd/system/yeslab-backup.service <<EOF
[Unit]
Description=YES Lab database and uploads backup
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
Environment=YESLAB_ENV_FILE=$ENV_FILE
ExecStart=$PROJECT_ROOT/deploy/scripts/backup.sh
EOF

cat > /etc/systemd/system/yeslab-backup.timer <<'EOF'
[Unit]
Description=Run YES Lab backup every day

[Timer]
OnCalendar=*-*-* 03:30:00
RandomizedDelaySec=30m
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now yeslab-backup.timer

configured_site="$(awk -F= '$1 == "YESLAB_SITE_ADDRESS" { print substr($0, index($0, "=") + 1) }' "$ENV_FILE" | tail -n 1)"
"${COMPOSE[@]}" ps
echo
echo "YES Lab 部署完成：https://$configured_site"
if [[ -n "$initial_password" ]]; then
  echo "首个管理员用户名：$ADMIN_USERNAME"
  echo "首个管理员初始密码：$initial_password"
  echo "请立即将该密码保存到密码管理器；脚本不会把明文密码写入服务器文件。"
else
  echo "检测到管理员账号已存在，本次没有创建或修改账号密码。"
fi
echo "若 HTTPS 暂时打不开，请确认域名 A/AAAA 记录及云安全组的 TCP 80/443、UDP 443。"
