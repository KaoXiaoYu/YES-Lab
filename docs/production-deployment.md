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
4. 将 GitHub 个人主页 Packages 中的 `yes-lab-api` 和 `yes-lab-web` 设为 `Public`。GHCR 首次发布的包默认是私有的；若保持私有，需要在服务器使用仅含 `read:packages` 权限的 classic PAT 执行 `docker login ghcr.io`。
5. 确认 GitHub 仓库 Actions 已启用。推送到 `main` 后，`Test and publish images` 工作流应全部通过。

不需要在服务器安装 Java 21、Maven、Node.js 或 MySQL，镜像由 GitHub Actions 构建。Docker Engine 与 Compose 插件由首次部署脚本按照 Docker 官方 APT 仓库安装。

## 3. 通过 SSH 首次部署 Ubuntu 24.04

先从本机连接服务器：

```bash
ssh root@你的服务器公网IP
```

如果云厂商默认提供普通用户，则使用 `ssh 用户名@公网IP`，并为下面的系统命令保留 `sudo`。

进入服务器后执行：

```bash
sudo apt-get update
sudo apt-get install -y git
sudo git clone https://github.com/KaoXiaoYu/YES-Lab.git /opt/yes-lab
cd /opt/yes-lab
sudo ./deploy/scripts/bootstrap-ubuntu.sh --domain lab.example.edu.cn
```

将最后一行的 `lab.example.edu.cn` 换成真实域名，不要填写 `https://`、端口或路径。默认会创建：

- 登录名：`teacher`
- 显示名：`汤洪大王`
- 内部编号：`T-001`

如需修改，可在首次运行时增加参数：

```bash
sudo ./deploy/scripts/bootstrap-ubuntu.sh \
  --domain lab.example.edu.cn \
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

如果镜像拉取提示 `denied`，通常是 GHCR 包仍为 Private。可以将两个包改为 Public，或先登录：

```bash
echo '你的 classic PAT' | sudo docker login ghcr.io -u KaoXiaoYu --password-stdin
```

不要把 PAT 写进脚本或 Git；使用完可执行 `sudo docker logout ghcr.io`，但私有镜像下次更新前需要重新登录。

## 4. 数据安全与数据库接管

生产数据不会存入 Git：

- MySQL：`/srv/yeslab/data/mysql`
- 证书、比赛图片和项目主图：`/srv/yeslab/data/uploads`
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
