# OpenLIMS 部署、初始化、升级与恢复

本手册与根目录 Dockerfile、`backend/Dockerfile`、`compose.yaml`、`deploy/Caddyfile`、三个运维脚本和 `.github/workflows/ci-images.yml` 对照。实际验证范围见[验证记录](open-source-verification.md)，本机通过的 H2 测试不等于生产 MySQL 8.4 或公网部署验收。

## 1. 环境与目录

本地开发需要 Node.js 22.13+、npm 10+、JDK 21、Maven Wrapper；步骤见[README](../README.md#本地启动)。若只有源码压缩包，没有 `.git`，仍可本地运行，但 `deploy.sh` 的 Git 升级与 Ubuntu 引导必须使用正式克隆。

生产基线为 Ubuntu 24.04 LTS amd64、Docker Engine + Compose 插件、MySQL 8.4、Java 21 容器、Caddy 2.11。CI 默认构建 amd64，不承诺 arm64 可直接拉取同一镜像。建议 2 核、2GB 内存、40GB 磁盘和 swap；当前 Compose 给 MySQL/API/Web 设置内存及日志上限，实际容量需按上传量和并发调整。

- 代码 `/opt/openlims`；环境 `deploy/.env.production`（权限 600，不提交）。
- 数据 `/srv/openlims/data/mysql`；上传 `/srv/openlims/data/uploads`。
- 上传包含成员头像、项目主图、成果/报名附件、招新附件、伙伴 Logo、首页 GLB。
- 备份 `/srv/openlims/backups`，默认 7 天；Caddy 证书/配置由 `caddy_data`、`caddy_config` 命名卷保存。
- 域名解析到服务器，开放 SSH TCP 22、Web TCP 80/443 和 HTTP/3 UDP 443；MySQL 3306、API 8080 不发布到公网。

服务器不需要额外安装 Java、Node、Maven、MySQL 或 Caddy。Docker 的安装方式按[官方 Ubuntu 文档](https://docs.docker.com/engine/install/ubuntu/)核对；仓库引导脚本遇到冲突包会停止，不自动删除已有容器环境。

## 2. 镜像发布与 Fork

GitHub Actions 在 PR 上执行前端检查/后端测试；推送 `main` 并通过后发布 `ghcr.io/<小写仓库所有者>/openlims-api` 与 `openlims-web`，标签 `latest` 和完整提交 SHA。无需手改 workflow 的个人用户名。仓库所有者可以在 Packages 中设公开访问，组织需允许 Actions 的 package 写权限；首次 package 关联/权限仍由 GitHub 设置决定。

Fork 后启用 Actions 并在 `main` 产生一次合法提交，确认 **Test and publish images** 成功后部署。根目录品牌配置会同时进入前后端镜像；仓库变量 `OPENLIMS_UI_PRESET` 选 `general`/`academic`/`engineering`/`life-science`。已有镜像无法通过运行时环境改变外观。

若自行构建（仓库根目录，amd64 Docker 主机）：

```bash
docker build --build-arg VITE_UI_PRESET=general -t openlims-web:local .
docker build -f backend/Dockerfile -t openlims-api:local .
```

将环境的 `OPENLIMS_WEB_IMAGE=openlims-web`、`OPENLIMS_API_IMAGE=openlims-api`、`OPENLIMS_IMAGE_TAG=local` 配合 `up` 使用；不要对此本地标签执行要求远程镜像的 `deploy.sh`/引导拉取步骤。自建前仍须 `npm run check`、`cd backend && ./mvnw package`，Dockerfile 的 API 构建跳过测试，由 CI/部署者先验证。

## 3. Ubuntu 首次引导

以下在专用服务器 root shell 中执行。公开仓库直接 HTTPS 克隆：

```bash
apt-get update
apt-get install -y git
git clone https://github.com/YESlab-UAVtech/OpenLIMS.git /opt/openlims
cd /opt/openlims
./deploy/scripts/bootstrap-ubuntu.sh \
  --domain lab.example.edu.cn \
  --admin-user teacher --admin-name '系统管理员' --admin-code T-001
```

Fork 加 `--image-namespace your-lowercase-owner` 并克隆自己的仓库；私有源码用只读 Deploy Key，克隆前将公钥加入目标仓库并核对 GitHub SSH 主机指纹，私钥不提交/不发送。

脚本支持 `--help`，只允许 Ubuntu 24.04/root，检查参数与 Git 克隆后才施工；安装 Docker、按需创建 2GB swap、生成随机数据库/JWT 密钥、创建数据目录/权限、拉取并启动 MySQL/API/Web、初始化首个管理员和 systemd 备份 timer。已有环境、数据库和账号不覆盖；传入新域名不会重写已有环境，需要自己编辑配置。

私有镜像若报 `unauthorized`/`denied`，在执行脚本的同一 Linux 用户下登录，然后原样重跑。登录用户名是有读取权限的账号，不必等于组织名：

```bash
read -rsp 'GitHub PAT: ' GHCR_TOKEN
echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USER --password-stdin
unset GHCR_TOKEN
```

PAT 需要 `read:packages` 及组织必要授权；不写在命令文本、配置或日志中。公开镜像无需登录。

首次管理员使用 `InitialAdminBootstrapConfig` 创建教师账号（教师与核心学生均为系统管理员），已有同名账号不会自动提升角色或改密码。随机初始密码在创建前显示于终端，立即保存并登录改密；成功后脚本重建 API，关闭 initial-admin 并移除容器环境中的初始密码。已有同名普通账号会停止并提示换名，内部编号占用也会停止。

生产 profile 始终关闭演示账号/示例项目初始化。首次登录后从主页编辑设置文案/方向/模型/伙伴，在招新管理和成员管理维护实际团队；基金首次单独确认期初。不要把本地演示账号用于公网。

## 4. 已有 Docker 的手动部署

这是不执行 apt/swap/systemd 引导的路径。先确保所选 MySQL 数据目录是新建空目录；既有库升级须先看第 6 节。

```bash
cd /opt/openlims
cp deploy/.env.production.example deploy/.env.production
chmod 600 deploy/.env.production
openssl rand -hex 24
openssl rand -hex 64
```

编辑环境文件：填写域名/CORS、正确镜像、数据库用户名、两个不同数据库密码、至少 32 字节随机 JWT 密钥和数据路径。使用生成值，不能保留 `replace-with-…`。文件会由备份脚本 `source`，用兼容 shell 的 `KEY=value`，空格值必须加引号，禁止命令替换。生产同源代理无需 `VITE_API_BASE_URL`。

```bash
sudo install -d -m 0750 /srv/openlims/data/mysql /srv/openlims/data/uploads /srv/openlims/backups
sudo chown -R 999:999 /srv/openlims/data/mysql
sudo chown -R 10001:10001 /srv/openlims/data/uploads
docker compose --env-file deploy/.env.production config --quiet
docker compose --env-file deploy/.env.production pull
docker compose --env-file deploy/.env.production up -d --wait --wait-timeout 180 mysql
```

权限对应当前镜像 UID，已有数据目录勿无审查地重设；自定义路径与镜像需相应调整。

首次管理员使用临时环境创建，不把密码永久写进文件：

```bash
read -rsp '首个管理员密码（16—72 位）: ' INITIAL_PASSWORD
echo
OPENLIMS_INITIAL_ADMIN_ENABLED=true \
OPENLIMS_INITIAL_ADMIN_USERNAME=teacher \
OPENLIMS_INITIAL_ADMIN_PASSWORD="$INITIAL_PASSWORD" \
OPENLIMS_INITIAL_ADMIN_DISPLAY_NAME='系统管理员' \
OPENLIMS_INITIAL_ADMIN_MEMBER_CODE=T-001 \
  docker compose --env-file deploy/.env.production up -d --wait --wait-timeout 240 api
unset INITIAL_PASSWORD
docker compose --env-file deploy/.env.production up -d --force-recreate --wait --wait-timeout 240 api
docker compose --env-file deploy/.env.production up -d --wait --wait-timeout 120 web
```

检查 API 日志、登录及角色，登录成功再改密。API 初次启动自动跑 Flyway、随后 Hibernate 校验结构。首次空库与已有结构基线不可混为一谈：prod 现有 `baseline-on-migrate` 仅跳过 V1 基线，不能推断任意手工旧库都能安全升级。

本路径不自动安装备份 timer：安排受控定时器执行 `backup.sh` 并监控失败，或在准备好引导前提后使用引导脚本补配置。业务环境变量见应用两个 YAML 文件；修改它们后要 `up -d` 重建容器，`restart` 不会应用新环境。

## 5. 服务管理与上线验收

```bash
cd /opt/openlims
docker compose --env-file deploy/.env.production ps
docker compose --env-file deploy/.env.production logs --tail 120 api
docker compose --env-file deploy/.env.production logs --tail 120 web
curl --fail https://lab.example.edu.cn/actuator/health
```

人工检查首页/页脚、注册登录/注销、真实成员与后台权限、头像/项目图/报名附件/证书/模型、任务提交/审核、榜单和基金；普通成员应拒绝管理 API，报名原图应拒绝无关账号。检查所有角色的消息与日程入口。容器 running 不代表服务通过业务验收。

暂停保留数据用 `docker compose --env-file deploy/.env.production stop`；恢复用 `up -d --wait --wait-timeout 240`。单独 `restart api` 只重启当前容器。禁止 `docker compose down -v`，禁止删除 `/srv/openlims/data`。

## 6. 升级与迁移约束

1. 开发机检查通过，提交/推送并确认 SHA 镜像已发布；生产不现场试写业务代码。
2. 先备份数据库/上传/环境密钥，并在隔离 MySQL 8.4 恢复演练，核对已有 `flyway_schema_history` 与已部署提交原 SQL。
3. 生产代码目录保持无未提交修改；环境文件的 `OPENLIMS_IMAGE_TAG` 填计划发布的完整提交 SHA，镜像路径与 Fork 一致。
4. 执行 `./deploy/scripts/deploy.sh`：已有 MySQL 运行则先备份，已有数据但数据库停机则安全停止；fetch/快进 `main`、校验配置、pull 镜像，依次等待 MySQL/API 健康后更新 Web。
5. 完成第 5 节验收，再记录实际版本/备份路径和上线结果。

环境 SHA 与更新后的 `main` 源码应对应同一构建；发布旧版本回滚用第 8 节，不用升级脚本。升级脚本的在线备份不冻结文件上传，重要升级建议先暂停 Web/API，按第 7 节取得完整一致恢复点再继续。升级失败时旧 Web 仍可能遇到不可用 API，需及时按日志处理或回滚，不能承诺零停机。

**历史迁移保护：** 上传前已取回官方 Git 历史，恢复上轮品牌更名改动的 V20 原件，V1—V22 等全部迁移与远端基线逐字节一致。内部旧变量名作为兼容性例外保留。部署既有库前仍须核对实际已部署提交，并对 `flyway_schema_history` 的 version/script/checksum 逐项核对；不同即停，不执行 `repair`、不手改历史 checksum、不改 `ddl-auto=update` 绕过。新结构通过更高版本的独立幂等迁移，保留审计历史。

所有迁移只写 MySQL 语法，H2 测试关闭 Flyway。预生产必须覆盖完整空库启动、旧数据升级、缺字段补缺与实体校验；任务 V11—V16、积分 V20、基金 V21、比赛 V22 等按各自需求/设计验收，特别是悬赏并发接取、积分幂等、基金重复初始化/并发支出/双撤销。MySQL 9.5 历史演练不能代替 MySQL 8.4。

## 7. 备份、校验与隔离恢复演练

```bash
cd /opt/openlims
./deploy/scripts/backup.sh
systemctl status openlims-backup.timer
journalctl -u openlims-backup.service --since today
```

引导安装的 timer 每天约 03:30（按服务器时区，另有随机延迟）执行，失败要监控。脚本生成 `openlims.sql`、存在上传目录时的 `uploads.tar.gz`、`SHA256SUMS`，按 UTC 时间加随机后缀命名并清理超过配置天数的完整备份；备份目录和数据目录不能重合/互相嵌套（会解析实际目录，包含符号链接）。失败产物留在 `.incomplete-*`，不得作为有效恢复点，清理不处理这些目录或其他无清单目录。使用 [mysqldump 官方选项](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)的事务内 SQL 快照，但 SQL 与上传包不是跨资源原子快照。

得到一致恢复点：安排维护窗口，停 Web/API（MySQL 保持运行），执行 backup，确认成功后 `up -d --wait` 恢复。停写失败/备份失败也应按实际情况恢复原服务；不要在仍有文件替换/清理时声称快照完整。保留备份到异地并加密，文件包含个人信息。环境密钥和 Caddy 证书卷不在 `backup.sh` 输出中，应单独安全备份。

恢复应先演练到新的数据目录，禁止直接清空现网。以下示例在隔离机器、仓库路径 `/opt/openlims` 执行，先把备份和安全保存的生产环境复制到该机器：

```bash
cd /path/to/backup
sha256sum -c SHA256SUMS
cd /opt/openlims
cp deploy/.env.production deploy/.env.restore
chmod 600 deploy/.env.restore
```

编辑 `.env.restore` 为新路径 `/srv/openlims/restore-test/data`、新域名、准确的备份版本镜像与匹配数据库/JWT 配置；保持初始管理员关闭。隔离机器没有现网服务/端口冲突，Compose 项目仍用同名 openlims，新路径必须从空目录开始。

```bash
sudo install -d -m 0750 /srv/openlims/restore-test/data/mysql /srv/openlims/restore-test/data/uploads
sudo chown -R 999:999 /srv/openlims/restore-test/data/mysql
sudo chown -R 10001:10001 /srv/openlims/restore-test/data/uploads
docker compose --env-file deploy/.env.restore up -d --wait --wait-timeout 180 mysql
docker compose --env-file deploy/.env.restore exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root openlims' < /path/to/backup/openlims.sql
sudo tar -xzf /path/to/backup/uploads.tar.gz -C /srv/openlims/restore-test/data
sudo chown -R 10001:10001 /srv/openlims/restore-test/data/uploads
docker compose --env-file deploy/.env.restore up -d --wait --wait-timeout 240 api web
```

若备份没有上传包，跳过 tar 并验证确实无附件。检查 Flyway、业务条数/基金余额/积分账本、管理员登录、私有附件及旧通知链接；原密钥不同会导致旧 JWT 失效，但账号密码仍保留。恢复演练成功后才决定正式切换，正式恢复也先停止写入并保存当前恢复点。恢复不会自动退还/冲正外部线下已发奖金。

临时本机 MySQL 必须经 `scripts/temp-mysql.sh init/start/status/stop`，带 TTL/有界日志，退出必须 stop，不跨会话留实例。不要手工后台起 mysqld。

## 8. 应用回滚与排错

将 `OPENLIMS_IMAGE_TAG` 改为上次成功 SHA，`pull api web` 后 `up -d --wait --wait-timeout 240`。镜像回滚不撤销 Flyway；新结构不兼容旧代码时只能前向修复或按已演练备份恢复。不要删除迁移记录。

| 现象                                               | 排查与处理                                                                           |
| -------------------------------------------------- | ------------------------------------------------------------------------------------ |
| 本地 `UnsupportedClassVersionError`/编译 target 错 | 确认当前终端 JDK 21，检查 `JAVA_HOME`/PATH，不能只看 IDE 设置                        |
| 本地 API 502/连接失败                              | 检查后端 8080 健康、Vite 代理、当前端口，确认不是旧实例/Java 8                       |
| `Permission denied` 执行 mvnw                      | Unix 克隆应保留可执行位；压缩包可 `chmod +x backend/mvnw`，Windows 用 cmd            |
| Maven 缺依赖/下载失败                              | 第一次需要 Maven Central 网络，检查代理/证书；不要以关闭 TLS 校验长期解决            |
| GHCR denied/找不到 SHA                             | 确认 Actions 成功、所有者小写、tag 存在、当前 Linux 用户已 login、有 package 权限    |
| API unhealthy/Flyway mismatch                      | 查 API 日志及原 SQL/历史；有差异停止部署，不能直接 repair                            |
| 上传权限拒绝/图片打不开                            | 查看 uploads bind mount、UID 10001、对应子目录、文件确实被备份；私有图片还需正确身份 |
| HTTPS 无法访问                                     | 核对 DNS A/AAAA、80/443、安全组和 Caddy 日志，不发布 8080 代替 HTTPS                 |
| 改环境/外观不生效                                  | 环境需重建容器；外观和品牌需重建两镜像，固定 SHA 避免缓存旧版本                      |
| 积分未立即增加                                     | 任务为到期定时结算（默认每小时第 5 分钟），检查状态/截止/结算开关与日志              |
| 备份失败/磁盘增长                                  | 查 timer 日志、权限、留存和 `df -h`；占用对不上先 `lsof +L1` 查已删除但仍占用文件    |

容器日志限制为 10MB × 3/服务，不使用无限追加后台日志。任何上线记录应区分已运行验证、静态检查与待生产实机验证。
