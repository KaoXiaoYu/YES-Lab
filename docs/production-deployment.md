# YES Lab 正式部署手册

## 1. 已确定的生产架构

正式环境采用一台 Linux 服务器运行 Docker Compose：

- `web`：Vue 静态文件 + Caddy，负责 HTTPS、HTTP/3、前端路由和 `/api` 反向代理。
- `api`：Java 21 + Spring Boot，只在容器网络内提供服务。
- `mysql`：MySQL 8.4，使用仓库外的持久目录。
- GitHub Actions：每次推送 `main` 先运行前后端测试，再构建并发布两个 GHCR 镜像；低内存服务器不承担编译工作。

应用配置同时兼容 Debian 12 和 Ubuntu LTS；仓库提供的首次部署脚本目前专门支持 Ubuntu 24.04 LTS。宿主机不需要安装 Java、Node、MySQL 或 Caddy，只需要 Git、Docker Engine 与 Docker Compose 插件。

目标服务器为 2 核 / 2 GB 内存 / 40 GB SSD，已为 MySQL、JVM、连接池和容器日志设置低资源参数。首次部署脚本会在系统没有 swap 时创建 2 GB swap；图片增多后，40 GB 磁盘会比 CPU 更早成为瓶颈。

## 2. 上线前条件

1. 准备一个域名，并将 A/AAAA 记录解析到服务器公网地址。
2. 安全组/防火墙开放 TCP 22、80、443 和 UDP 443；不要向公网开放 3306、8080。
3. 将 GitHub 仓库中的最新代码推送到 `main`，等待 `Test and publish images` 工作流成功。
4. 当前 `KaoXiaoYu/YES-Lab` 仓库和 `yes-lab-api`、`yes-lab-web` GHCR 包均按私有资源部署。为新服务器准备一把仓库只读 Deploy Key，并准备一个属于 `KaoXiaoYu`、至少具有 `read:packages` 权限的 GitHub classic PAT；两种凭据不能互相替代。
5. 确认 GitHub 仓库 Actions 已启用。推送到 `main` 后，`Test and publish images` 工作流应全部通过。

不需要在服务器安装 Java 21、Maven、Node.js 或 MySQL，镜像由 GitHub Actions 构建。Docker Engine 与 Compose 插件由首次部署脚本按照 Docker 官方 APT 仓库安装。

## 3. 通过 SSH 首次部署 Ubuntu 24.04

先从本机连接服务器：

```bash
ssh root@你的服务器公网IP
```

如果云厂商默认提供普通用户，则使用 `ssh 用户名@公网IP`，登录后先执行 `sudo -i` 进入 root shell，再按下文操作。不要在 `ubuntu` 用户下登录 GHCR、又切换到 `root` 运行部署，因为两者不共享 Docker 登录凭据。

### 3.1 配置私有仓库 Deploy Key

先安装 Git 并创建本服务器专用密钥：

```bash
apt-get update
apt-get install -y git
install -d -m 700 /root/.ssh
ssh-keygen -t ed25519 -C 'yes-lab-production-new-server' -f /root/.ssh/yeslab_deploy -N ''
cat /root/.ssh/yeslab_deploy.pub
```

如果 `/root/.ssh/yeslab_deploy` 已存在，不要覆盖或重复执行 `ssh-keygen`，只执行 `cat` 查看现有公钥。复制 `.pub` 输出的完整单行内容，在 GitHub 仓库进入 `Settings → Deploy keys → Add deploy key`，标题可填写 `YES Lab 新服务器`，不要勾选 `Allow write access`。旧服务器的 Deploy Key 可以继续保留。

添加后测试认证：

```bash
ssh -i /root/.ssh/yeslab_deploy -o IdentitiesOnly=yes -T git@github.com
```

第一次连接会询问 GitHub 主机指纹；Ed25519 指纹应为 `SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU`。确认一致后输入 `yes`。看到 `successfully authenticated` 表示 Deploy Key 生效；末尾提示 GitHub 不提供 shell 属于正常现象。

认证成功后再克隆。以下命令故意保持为单行，避免 SSH 终端复制时破坏续行符：

```bash
GIT_SSH_COMMAND="ssh -i /root/.ssh/yeslab_deploy -o IdentitiesOnly=yes" git clone git@github.com:KaoXiaoYu/YES-Lab.git /opt/yes-lab
git -C /opt/yes-lab config core.sshCommand "ssh -i /root/.ssh/yeslab_deploy -o IdentitiesOnly=yes"
test -f /opt/yes-lab/deploy/scripts/bootstrap-ubuntu.sh && echo "仓库克隆成功"
```

不要使用 GitHub 邮箱和登录密码克隆；GitHub 已停止接受账号密码进行 Git 身份验证。HTTPS 克隆虽然可以使用 PAT，但后续更新仍需单独管理 Git 凭据，生产服务器优先使用只读 Deploy Key。

### 3.2 安装 Docker 并登录私有 GHCR

从仓库目录第一次运行引导脚本：

```bash
cd /opt/yes-lab
./deploy/scripts/bootstrap-ubuntu.sh --domain yeslab.tech
```

引导脚本会安装 Docker、创建 swap 和生产配置。如果私有镜像尚未登录，它会安全停止在 `unauthorized`，不会删除数据库或上传目录，也不需要删除 `/opt/yes-lab`。此时在同一个 `root` 会话中安全输入 classic PAT：

```bash
read -rsp "请输入具有 read:packages 权限的 GitHub classic PAT: " GHCR_TOKEN
echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u KaoXiaoYu --password-stdin
unset GHCR_TOKEN
```

看到 `Login Succeeded` 后可先验证镜像，再原样重跑脚本：

```bash
docker pull ghcr.io/kaoxiaoyu/yes-lab-api:latest
docker pull ghcr.io/kaoxiaoyu/yes-lab-web:latest
cd /opt/yes-lab
./deploy/scripts/bootstrap-ubuntu.sh --domain yeslab.tech
```

如果服务器原本已经安装 Docker，也可以在第一次运行引导脚本前完成 GHCR 登录。Docker 凭据按服务器和 Linux 用户分别保存；以 `root` 部署时必须以 `root` 登录，不能只在 `ubuntu` 用户下登录。

将示例中的 `yeslab.tech` 换成真实域名时，不要填写 `https://`、端口或路径。默认会创建：

- 登录名：`teacher`
- 显示名：`汤洪大王`
- 内部编号：`T-001`

如需修改，可在首次运行时增加参数：

```bash
./deploy/scripts/bootstrap-ubuntu.sh \
  --domain yeslab.tech \
  --admin-user teacher \
  --admin-name '汤洪大王' \
  --admin-code T-001
```

脚本会自动完成：

- 校验 Ubuntu 24.04 与仓库结构。
- 从 Docker 官方仓库安装 Docker Engine、Buildx 和 Compose 插件。
- 在系统没有 swap 时创建 2 GB `/swapfile`。
- 创建仓库外的数据、上传和备份目录。
- 在服务器本地生成 MySQL 密码与 64 字节 JWT 密钥，并以 `0600` 权限写入 `deploy/.env.production`。
- 拉取 GHCR 镜像，依次启动 MySQL、API 和 Web，并等待健康检查。
- 只创建一个真实教师管理员，不加载 `core`、`member` 等演示账号和演示项目。
- 验证管理员已经写入数据库，然后立即关闭初始化开关并重建 API，使明文初始密码不留在配置文件或容器环境变量中。
- 创建每天约 03:30 执行的 systemd 备份定时器，本机备份保留 7 天。

脚本只在 SSH 终端显示管理员初始密码，不会写入服务器配置文件；看到后请立即存入密码管理器。重复运行脚本不会覆盖 `deploy/.env.production`、MySQL、上传文件或已有账号密码。

部署后检查：

```bash
cd /opt/yes-lab
sudo docker compose --env-file deploy/.env.production ps
curl https://你的域名/actuator/health
free -h
df -h
systemctl list-timers yeslab-backup.timer
```

健康接口应返回包含 `"status":"UP"` 的 JSON。Caddy 会在域名解析与 80/443 端口可达后自动申请并续期 HTTPS 证书。原理见 [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https)。

### 3.3 首次安装常见报错

| 终端提示 | 原因 | 处理方式 |
| --- | --- | --- |
| `No such file or directory`、`cd: /opt/yes-lab` | 仓库尚未克隆成功，当前目录中也没有部署脚本 | 先完成 Deploy Key 绑定并重新克隆，不要继续运行相对路径脚本 |
| `Permission denied (publickey)` | 本地密钥存在，但对应公钥没有绑定到该仓库，或绑定的是旧服务器公钥 | 执行 `cat /root/.ssh/yeslab_deploy.pub`，把该完整公钥添加到仓库 Deploy keys，再用 `ssh -T` 测试 |
| `unauthorized` 或 `denied` | 当前 Linux 用户尚未登录私有 GHCR，或 PAT 缺少 `read:packages` | 以实际运行部署的同一用户执行 `docker login ghcr.io`，再重跑脚本 |
| 其他镜像显示 `Interrupted` | Compose 在其中一个镜像失败后取消了其他并行拉取 | 先解决最先出现的 `Error`，这不代表 MySQL 或其他镜像损坏 |
| `cannot change to '/opt/yes-lab'` | 前面的 `git clone` 已失败，后续 `git -C` 只是连锁报错 | 不要继续执行后续命令，先修复 Git 认证并确认目录已经生成 |

脚本可以安全重跑，并会保留现有 `deploy/.env.production`、MySQL 数据和上传文件。不要通过删除整个 `/opt/yes-lab` 来处理镜像认证错误，因为该目录内的 `.env.production` 不受 Git 管理，删除后会丢失数据库密码和 JWT 密钥。若目录已经误删且要接管已有数据，应先从可信备份或旧服务器恢复原生产配置。

## 从旧服务器迁移正式数据

本节适用于新服务器已经按第 3 节部署完成，需要接管旧服务器正式业务数据的情况。完整迁移包含：

- `yeslab.sql`：账号、报名、成员、项目、比赛、主页配置和刷新会话等 MySQL 数据；
- `uploads.tar.gz`：头像、证书、比赛图片、项目封面和赞助商 Logo；
- 可选的 JWT 配置：用于尽量减少切换后的重新登录；
- 同版本或更新版本的 API/Web 镜像。

不要复制正在运行的 `/srv/yeslab/data/mysql` 目录。现有 `backup.sh` 使用 `mysqldump --single-transaction --quick` 生成逻辑备份，适合跨服务器恢复，也便于校验和回滚。

### A. 新服务器准备接收目录

在新服务器执行：

```bash
install -d -m 0750 /srv/yeslab/migration
cd /opt/yes-lab
docker compose --env-file deploy/.env.production ps
```

确认新服务器的 MySQL 正常运行。若 `deploy/.env.production` 中的 `YESLAB_DATA_ROOT` 不是 `/srv/yeslab/data`，后续上传目录路径都应改为实际值。

### B. 旧服务器停止写入并生成最终备份

在旧服务器执行：

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production stop web api
./deploy/scripts/backup.sh
ls -lah /srv/yeslab/backups
```

先停止 Web/API 是为了避免备份后继续产生新写入。此时不要停止 MySQL，备份脚本需要连接运行中的 MySQL。脚本成功后会输出一个 UTC 时间目录，例如 `/srv/yeslab/backups/20260907T120000Z`。

旧服务器从此应保持 Web/API 停止，直到确认迁移完成或明确决定放弃迁移。若需要缩短停机时间，可提前做一次演练备份；正式切换时仍需停写后再生成一次最终备份。

### C. 传输并校验备份

仍在旧服务器执行，将时间目录和 IP 换成实际值：

```bash
scp -r /srv/yeslab/backups/20260907T120000Z \
  root@新服务器IP:/srv/yeslab/migration/
```

在新服务器执行完整性校验：

```bash
cd /srv/yeslab/migration/20260907T120000Z
sed -E 's#  .*/#  #' SHA256SUMS | sha256sum -c -
```

旧版备份脚本在 `SHA256SUMS` 中记录的是旧服务器绝对路径，直接执行 `sha256sum -c SHA256SUMS` 会在新服务器报文件不存在；上面的命令会只保留文件名后再校验。新版脚本已改用相对路径，该命令同样兼容。`yeslab.sql` 和 `uploads.tar.gz` 都应显示 `OK`。若旧站从未产生上传文件，备份目录可能没有 `uploads.tar.gz`，此时只恢复数据库即可。

### D. 恢复上传目录

在新服务器停止 Web/API，并把新服务器当前的上传目录改名保留：

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production stop web api
./deploy/scripts/backup.sh

if [ -d /srv/yeslab/data/uploads ]; then
  mv /srv/yeslab/data/uploads \
    /srv/yeslab/data/uploads.before-migration-$(date -u +%Y%m%dT%H%M%SZ)
fi
```

先执行备份脚本会为新服务器当前数据库和上传目录创建一份可恢复快照。然后恢复旧服务器上传文件；若旧站没有上传压缩包，则创建空目录：

```bash
if [ -f /srv/yeslab/migration/20260907T120000Z/uploads.tar.gz ]; then
  tar -C /srv/yeslab/data -xzf \
    /srv/yeslab/migration/20260907T120000Z/uploads.tar.gz
else
  install -d -m 0750 /srv/yeslab/data/uploads
fi
chown -R 10001:10001 /srv/yeslab/data/uploads
```

UID/GID `10001:10001` 是当前 API 镜像内 `yeslab` 用户的身份。保留的 `uploads.before-migration-*` 目录可以在验收后再删除。

### E. 覆盖新服务器数据库

以下操作会删除新服务器当前 `yeslab` 数据库中的全部内容。执行前确认 SSH 会话连接的是新服务器，并确认新数据库没有需要保留的数据。MySQL 容器必须保持运行。

```bash
cd /opt/yes-lab

docker compose --env-file deploy/.env.production exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root -e "DROP DATABASE IF EXISTS yeslab; CREATE DATABASE yeslab CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'

docker compose --env-file deploy/.env.production exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root yeslab' \
  < /srv/yeslab/migration/20260907T120000Z/yeslab.sql
```

恢复使用新服务器现有的 `deploy/.env.production` 和其中的 MySQL 密码。数据库备份只包含 `yeslab` 业务库，不会覆盖新服务器 MySQL 的系统账号。不要在新服务器 MySQL 已初始化后直接用旧服务器的整份 `.env.production` 覆盖当前文件，否则旧密码可能与新 MySQL 数据目录中已经创建的账号密码不一致。

如果希望尽量维持原有登录状态，可以在首次接收正式流量前，将旧服务器的 `YESLAB_JWT_SECRET` 和 `YESLAB_JWT_ISSUER` 安全地写入新服务器环境文件，但不要连同旧数据库密码一起覆盖。JWT 密钥属于秘密，不能粘贴到聊天、Git、工单或公开存储；更换密钥不会破坏账号数据，但现有用户可能需要重新登录。

### F. 启动 API、运行迁移并验收

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production up -d --wait --wait-timeout 240 mysql api
docker compose --env-file deploy/.env.production logs --tail 120 api
docker compose --env-file deploy/.env.production exec -T api \
  curl --fail http://127.0.0.1:8080/actuator/health
```

API 启动时会由 Flyway 自动执行旧数据库尚未应用的增量迁移。健康检查通过后，再启动 Web：

```bash
docker compose --env-file deploy/.env.production up -d web
docker compose --env-file deploy/.env.production ps
```

正式切换前至少人工检查：

1. 旧服务器中已有的教师、成员和游客账号可以登录；
2. 成员主页、头像、项目封面、比赛证书与图集可以读取；
3. 主页配置和赞助商图片、文字正确；
4. 管理后台能读取报名、项目和比赛记录；
5. 新上传一张临时图片后可以读取，确认上传目录权限正确。

#### 迁移后登录返回 401

如果首页和健康检查正常，但提交登录后返回 `401` 与“账号或密码错误”，先在新服务器只读检查迁移后的账号：

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --table --user="$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT username, role, enabled + 0 AS enabled, CHAR_LENGTH(password_hash) AS hash_length FROM accounts ORDER BY role, username;"'
```

BCrypt 密码哈希正常长度为 60。按结果处理：

- 旧账号存在、`enabled=1`、`hash_length=60`：数据库和密码哈希已经迁移；使用该账号在旧服务器上的原密码。恢复旧数据库会覆盖新服务器首次部署时创建的同名管理员记录，因此新服务器首次部署时显示的随机密码可能不再有效。
- 账号不存在：确认导入的是旧服务器最终备份，并检查导入命令是否成功；不要在错误数据库中反复尝试密码。
- `enabled=0`：该账号已被停用，需要由另一名教师或核心学生管理员在成员管理中启用。
- 哈希长度不是 60：停止登录尝试，检查 SQL 备份和导入过程，不要直接把明文密码写入数据库。

`POST /api/v1/auth/login` 本身允许匿名访问，因此正确到达该接口后出现上述 JSON 401 通常不是 DNS、Caddy、CORS 或 JWT 密钥导致。更换 JWT 密钥会使旧访问令牌失效，但不会改变数据库中账号密码；用户使用正确账号密码重新登录后会获得新令牌。

账号状态正常时，在新服务器通过隐藏输入直接测试容器内登录接口，避免把密码写入命令历史或输出：

```bash
cd /opt/yes-lab
read -r -p "登录账号: " AUTH_USER
read -r -s -p "登录密码: " AUTH_PASS
echo
AUTH_USER="$AUTH_USER" AUTH_PASS="$AUTH_PASS" \
python3 -c 'import json,os; print(json.dumps({"username":os.environ["AUTH_USER"],"password":os.environ["AUTH_PASS"],"rememberMe":False}))' |
docker compose --env-file deploy/.env.production exec -T api \
  curl -sS -o /dev/null -w 'HTTP %{http_code}\n' \
  -H 'Content-Type: application/json' \
  --data-binary @- http://127.0.0.1:8080/api/v1/auth/login
unset AUTH_USER AUTH_PASS
```

- 返回 `HTTP 200`：账号密码在后端有效，清除浏览器中 `yeslab.tech` 的 Cookie/站点数据并硬刷新后重试。
- 返回 `HTTP 401`：输入密码与当前数据库哈希不匹配。分别在旧、新服务器运行下面的只读指纹查询；两边结果相同表示密码数据确实相同，旧浏览器可能只是依靠已有刷新会话保持登录，并未重新验证当前输入的密码；结果不同则应检查是否导入了错误或过早的备份。

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT SHA2(GROUP_CONCAT(SHA2(CONCAT(username, password_hash), 256) ORDER BY username), 256) FROM accounts;"'
```

该命令只输出整张账号凭据表的一个校验指纹，不输出密码或单个密码哈希。如果旧密码无法确认且系统内没有其他可登录管理员，先运行 `backup.sh`，再通过应用内置的生产管理员初始化器创建一个恢复管理员；它使用 Spring Security 生成 BCrypt 哈希，不修改已有账号，也不拉取或升级镜像：

```bash
cd /opt/yes-lab
./deploy/scripts/backup.sh

RECOVERY_PASSWORD="$(openssl rand -base64 24 | tr -d '\n')"
RECOVERY_CODE="T-RECOVERY-$(date +%s)"
printf '恢复管理员账号：recovery-admin\n恢复管理员密码：%s\n' "$RECOVERY_PASSWORD"

YESLAB_INITIAL_ADMIN_ENABLED=true \
YESLAB_INITIAL_ADMIN_USERNAME=recovery-admin \
YESLAB_INITIAL_ADMIN_PASSWORD="$RECOVERY_PASSWORD" \
YESLAB_INITIAL_ADMIN_DISPLAY_NAME='迁移恢复管理员' \
YESLAB_INITIAL_ADMIN_MEMBER_CODE="$RECOVERY_CODE" \
docker compose --env-file deploy/.env.production \
  up -d --force-recreate --wait --wait-timeout 240 api

docker compose --env-file deploy/.env.production exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --table --user="$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT username, role, enabled + 0 AS enabled FROM accounts WHERE username = '\''recovery-admin'\'';"'

docker compose --env-file deploy/.env.production \
  up -d --force-recreate --wait --wait-timeout 240 api
unset RECOVERY_PASSWORD RECOVERY_CODE
```

先保存终端显示的随机密码。第一次重建 API 会创建 `recovery-admin`，查询结果应显示角色 `TEACHER` 且 `enabled=1`；第二次重建 API 会恢复环境文件中默认关闭的初始化状态，避免初始化密码继续保留在容器环境中。随后使用恢复管理员登录并检查业务数据。不要直接在 MySQL 中写入明文密码或手工拼接 BCrypt 哈希。

确认后再把域名 A/AAAA 记录切到新服务器，并从公网执行：

```bash
curl --fail https://yeslab.tech/actuator/health
```

旧服务器应至少保留数天，但 Web/API 继续保持停止。新服务器开始接收写入后，不能只把 DNS 指回旧服务器；如果必须回滚，需要先停止新服务器写入并把新服务器最新数据库和上传文件迁回，否则切换后产生的数据会丢失。

更新
cd /opt/yes-lab
./deploy/scripts/deploy.sh

### G. 腾讯云域名指向其他厂商服务器

域名注册在腾讯云并不要求网站也运行在腾讯云。若 `yeslab.tech` 当前使用腾讯云云解析 DNS，在腾讯云控制台进入“云解析 DNS → 权威解析 → yeslab.tech → 记录管理”，修改或添加以下记录：

| 主机记录 | 记录类型 | 线路类型 | 记录值 | TTL |
| --- | --- | --- | --- | --- |
| `@` | `A` | 默认 | 新服务器公网 IPv4 | `600` |
| `www` | `CNAME` | 默认 | `yeslab.tech` | `600` |

`@` 代表根域名 `yeslab.tech`。记录值必须填写公网 IP，不能填写 `10.x`、`172.16—31.x` 或 `192.168.x` 等内网地址，也不要填写 `http://`、`https://`、端口或路径。若不需要 `www.yeslab.tech`，第二条可以不添加。

处理已有记录时：

- 将旧的 `@` A 记录改为新公网 IPv4，或者先禁用旧记录再添加新记录；不要同时保留新旧两个默认线路 A 记录，否则 DNS 会把访问随机分配到两台服务器。
- 新服务器没有公网 IPv6 时，删除或暂停旧的 `@` AAAA 记录，否则部分支持 IPv6 的访客仍会连接旧服务器。
- MX、TXT、域名验证和邮箱相关记录与网站服务器迁移无关，不要删除。
- 如果控制台提示“未使用云解析 DNS 地址”，需要按控制台给出的 NS 地址修改域名的 DNS 服务器；腾讯云注册且一直使用腾讯云解析的域名通常不需要此步骤。

解析修改后，可在任意 Linux/macOS 终端验证：

```bash
dig +short yeslab.tech A @1.1.1.1
dig +short yeslab.tech AAAA @1.1.1.1
dig +short www.yeslab.tech @1.1.1.1
curl --resolve yeslab.tech:443:新服务器公网IPv4 \
  --fail --head https://yeslab.tech
```

第一条应返回新服务器 IPv4；新服务器没有 IPv6 时，第二条应无输出。`curl --resolve` 可在等待 DNS 生效期间直接验证新服务器的 HTTPS 配置。新服务器安全组及系统防火墙必须允许 TCP 80、443；Caddy 首次签发证书时域名也必须已经指向新服务器且公网能够访问这两个端口。

备案遵循实际服务器接入商：

- 新服务器位于中国大陆时，应在新服务器所属云厂商办理首次备案或接入备案。域名可以继续留在腾讯云，不需要转移注册商。
- 新服务器位于中国香港或其他境外地区时，一般不通过腾讯云办理中国大陆服务器接入备案；仍需遵守服务器所在地及业务适用规定。

腾讯云操作入口和字段说明见 [快速添加域名解析](https://cloud.tencent.com/document/product/302/3446/)；腾讯云也明确说明，其注册域名可以解析到其他厂商服务器，并应在实际服务器接入商办理备案，见 [云解析 DNS 常见问题](https://cloud.tencent.com/document/product/302/12070) 和 [备案常见问题](https://cloud.tencent.com/document/product/243/19631)。

## 4. 数据安全与数据库接管

生产数据不会存入 Git：

- MySQL：`/srv/yeslab/data/mysql`
- 证书、比赛图片、项目主图和成员头像：`/srv/yeslab/data/uploads`
- 本机备份：`/srv/yeslab/backups`
- HTTPS 证书：Docker 命名卷 `caddy_data`

禁止执行 `docker compose down -v`，也不要删除 `/srv/yeslab/data`。

Flyway 的行为分两种：

- 空 MySQL 数据库：执行 V1 创建完整表结构，再执行 V2 增加刷新会话表。
- 已有、尚未被 Flyway 管理的 MySQL 数据库：将现有结构登记为 V1，再执行 V2；Hibernate 随后只做结构校验，不自动改表。

如果已有数据库结构与当前实体不匹配，API 会停止启动并保留原数据，需先分析差异再编写新迁移。不要临时改回 `ddl-auto=update`。Flyway 基线机制说明见 [Baseline migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations)。

当前方案假定正式业务数据已经在 MySQL 或将从空 MySQL 开始；本地 `backend/data/yeslab.mv.db` 不会自动导入 MySQL。如需把本地 H2 演示数据迁入生产库，应单独做一次经过校验的数据转换，不能直接复制数据库文件。

## 5. 日常发布

本地完成修改、测试并推送 `main` 后：

1. 等待 GitHub Actions 测试和镜像发布成功。
2. 登录服务器，进入 `/opt/yes-lab`。
3. 建议把 `YESLAB_IMAGE_TAG` 更新为本次提交完整 SHA。
4. 执行：

```bash
./deploy/scripts/deploy.sh
```

发布脚本会按以下顺序执行：

1. 检查服务器仓库没有未提交修改。
2. 在拉取代码前备份 MySQL 和上传文件。
3. 仅允许 `git merge --ff-only`，避免服务器产生合并提交。
4. 拉取指定镜像，先启动 MySQL，再启动并健康检查 API。
5. API 通过检查后才更新 Web。

迁移必须保持向前兼容，因此若新 API 启动失败，可将 `YESLAB_IMAGE_TAG` 改回上一提交 SHA 并再次执行 `docker compose ... up -d`。数据库不自动降级；需要回退数据库时必须先停机并从已验证备份恢复。

本次个人主页展示配置随 `V4__member_profile_showcase.sql` 发布：Flyway 只新增展示开关和两个有序关联表，不删除、覆盖成员、项目或比赛数据。部署脚本会在迁移前完成 MySQL 与上传目录备份；不要手工创建这些表，也不要修改已经执行过的 V1—V3。

## 6. 备份、验证与恢复原则

手动备份：

```bash
./deploy/scripts/backup.sh
```

脚本使用 `mysqldump --single-transaction --quick` 生成一致性 SQL 备份，同时打包上传目录、生成 SHA-256 校验文件，并按配置保留最近 7 天。相关选项见 [MySQL 8.4 mysqldump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)。

查看自动备份状态：

```bash
systemctl status yeslab-backup.timer
journalctl -u yeslab-backup.service --since today
ls -lah /srv/yeslab/backups
```

至少每月在另一台机器或临时数据库中进行一次恢复演练。服务器本地备份不能替代异地备份；建议再将备份目录同步到学校存储、私有对象存储或另一台受控主机。

恢复属于覆盖性操作，不放入自动部署脚本。实际恢复前应先：

1. 停止 `web` 和 `api`，保留当前数据目录副本。
2. 校验备份目录中的 `SHA256SUMS`。
3. 在临时数据库恢复并验证账号、成员、项目、比赛和主页数据。
4. 获得明确确认后，才恢复正式 MySQL 和上传目录。

## 7. JWT 登录方案

- 访问 JWT：HS256，密钥只由服务器环境变量提供，有效期 15 分钟，校验签发者、签名与过期时间。
- 刷新令牌：48 字节随机值，只以 SHA-256 摘要存入 MySQL；浏览器仅通过 `HttpOnly + Secure + SameSite=Lax` Cookie 持有原值。
- 每次刷新都会轮换刷新令牌，旧令牌立即失效；退出登录会吊销当前刷新令牌并清除 Cookie。
- 未勾选“记住我”：刷新 Cookie 为浏览器会话 Cookie，最长 12 小时，关闭浏览器后不会持久保存。
- 勾选“记住我”：Cookie 与服务端刷新会话均为 30 天。
- 访问 JWT 只保存在前端内存中，不写入 Local Storage 或 Session Storage。

生产环境必须通过 HTTPS 使用登录功能。JWT 密钥轮换会使已有访问令牌失效；如需无感轮换，应在后续加入带 `kid` 的双密钥过渡机制。
