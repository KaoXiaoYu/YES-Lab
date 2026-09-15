# YES Lab

YES Lab（Yichun Embodied Science）是一个面向高校实验室的开源准备中平台，覆盖公开官网、成员主页、招新流程、项目协作、竞赛成果、讨论板和站内通知。项目采用前后端分离架构，本地可以使用 H2 零配置启动，生产环境提供 MySQL、Docker Compose、Caddy HTTPS 和 GitHub Actions 镜像发布方案。

![YES Lab 首页预览](public/og.png)

> 当前项目仍在整理开源发布条件。业务代码可以运行，但仓库尚未选择开源许可证；公开发布前请先阅读[许可证说明](#许可证)。

## 功能概览

- **公开展示**：实验室介绍、可替换 3D 模型轮播、研究方向、成员、项目、竞赛成果、新闻、赞助伙伴与内容管理。
- **账号与权限**：教师、核心学生、普通成员、游客四类角色；JWT 访问令牌与可轮换刷新令牌。
- **招新管理**：报名、初筛、面试预约与叫号、技能测试占位、试用期和正式成员转换。
- **成员主页**：公开资料、富文本介绍、头像、项目和获奖成果展示顺序。
- **项目团队**：负责人、指导老师、成员与项目管理员，支持项目资料、主图和公开展示。
- **竞赛成果**：队长提交、证书和图集上传、管理员审核、首页排序与比赛倒计时。
- **讨论与通知**：公开浏览讨论，成员发帖、回复和点赞；机器人“梅琳娜”推送业务通知。
- **成员积分**：按竞赛、项目、实验室贡献及运营成果发放，支持多人分配、月度封顶、凭证留痕和撤销冲正。
- **生产运维**：MySQL + Flyway、Docker Compose、Caddy 自动 HTTPS、备份和 Ubuntu 24.04 引导脚本。

测验、写题、公开积分排行榜写入、竞赛批量导入和项目即时聊天尚未实现。相关权限或字段仅用于保留模块边界，请勿将其视为可用功能。

## 技术栈

| 层级     | 技术                                                                        |
| -------- | --------------------------------------------------------------------------- |
| 前端     | Vue 3、Vue Router、Vite、Tiptap、Three.js、Lucide Icons                     |
| 后端     | Java 21、Spring Boot 4.1、Spring Security、Spring Data JPA、Bean Validation |
| 身份认证 | HS256 JWT、BCrypt、HttpOnly 刷新 Cookie、服务端令牌摘要与轮换               |
| 数据库   | 本地 H2 文件数据库；生产 MySQL 8.4 + Flyway                                 |
| 内容安全 | OWASP Java HTML Sanitizer、上传文件签名和大小校验                           |
| 部署     | Docker、Docker Compose、Caddy、GHCR、GitHub Actions                         |
| 工程质量 | ESLint、Prettier、Maven Wrapper、JUnit / Spring Boot Test                   |

## 架构与目录

```text
YES Lab/
├── src/                         # Vue 页面、组件、路由和 API 客户端
├── public/                      # 静态图片、Logo 与 3D 模型
├── backend/
│   ├── src/main/java/           # Spring Boot 业务代码
│   ├── src/main/resources/      # 本地/生产配置与 Flyway 迁移
│   ├── src/test/                # 后端自动化测试
│   └── docs/                    # API 模块边界与权限说明
├── deploy/                      # Caddy、生产环境示例和运维脚本
├── docs/                        # 生产部署手册
├── .github/workflows/           # CI 与容器镜像发布
├── compose.yaml                 # MySQL、API、Web 生产编排
└── DEVLOG.md                    # 关键决策、验证记录和待办
```

浏览器默认请求同源 `/api`。本地由 Vite 将 `/api` 和 `/actuator` 代理到 Spring Boot；生产由 Caddy 提供静态站点、HTTPS 和反向代理。API 在本地使用 H2，在 `prod` profile 下由 Flyway 管理 MySQL 结构。

更细的后端边界见[模块说明](backend/docs/module-boundaries.md)，角色规则见[访问控制设计](backend/docs/access-control.md)。

## 快速开始

### 1. 准备环境

- Node.js `>= 22.13.0`
- npm `>= 10`
- Java 21（JDK，而不是仅 JRE）
- Git

Maven 无需全局安装，仓库已经包含 Maven Wrapper。MySQL 也不是本地开发的必需项。

确认版本：

```bash
node --version
npm --version
java -version
```

如果 macOS 同时安装了多个 JDK，可以用下面的命令选择 Java 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### 2. 获取代码并安装前端依赖

```bash
git clone https://github.com/KaoXiaoYu/YES-Lab.git
cd YES-Lab
npm ci
```

项目统一使用 npm，依赖以 `package-lock.json` 为准。修改依赖时使用 `npm install`，普通拉取和 CI 使用 `npm ci`。

### 3. 启动后端

新开一个终端：

```bash
cd backend
./mvnw spring-boot:run
```

Windows PowerShell 使用：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认地址为 <http://127.0.0.1:8080>，健康检查为 <http://127.0.0.1:8080/actuator/health>。首次启动会创建 `backend/data/` 下的 H2 数据库和上传目录，并写入本地演示数据。

### 4. 启动前端

再开一个终端，在仓库根目录运行：

```bash
npm run dev
```

访问终端显示的地址，通常是 <http://127.0.0.1:5173>。如果 5173 已被占用，Vite 会选择其他端口，以终端输出为准。

### 5. 使用演示账号

以下账号只会在本地默认配置中初始化：

| 账号      | 密码                   | 角色                  | 可体验内容                 |
| --------- | ---------------------- | --------------------- | -------------------------- |
| `teacher` | `YesLab-Teacher-2026!` | 教师 / 系统管理员     | 全部管理功能               |
| `core`    | `YesLab-Core-2026!`    | 核心学生 / 系统管理员 | 与教师相同的管理权限       |
| `member`  | `YesLab-Member-2026!`  | 普通成员              | 个人主页、项目、成果、讨论 |

游客流程：打开 `/register` 使用邮箱注册，然后进入 `/application` 填写报名表。

生产 profile 强制关闭这些演示账号。请勿把本地默认密码用于公开环境。

## 使用教程

### 公开官网与主页内容

1. 未登录访问首页即可查看实验室简介、成员、项目、成果和新闻。
2. 使用教师或核心学生账号登录。
3. 打开“主页编辑”，维护品牌文案、研究方向、栏目标题、赞助伙伴和展示顺序。
4. 成员、项目、比赛和新闻的详细数据仍在各自管理模块维护；主页编辑只控制聚合展示。

### 首页 3D 模型轮播

1. 使用教师或核心学生账号进入“主页编辑 → 3D 模型”。
2. 点击“添加模型”，选择一个 `.glb` 文件。仅支持 GLB 2.0，单个文件最大 20MB；服务端会校验扩展名、MIME 类型、GLB 文件头、版本和声明长度。
3. 填写公开展示标题与说明，按需启用或停用该项。至少需要保留并启用一个模型，最多配置 8 个。
4. 使用上移、下移按钮调整轮播顺序，也可以直接替换某一项的 GLB 文件。上传成功后还需要点击页面底部“保存主页内容”才会发布。
5. 公开首页每 8 秒自动切换。访客可使用上一项、下一项、圆点导航、暂停和重置按钮，也可拖动或用方向键查看；鼠标悬停、键盘焦点和系统“减少动态效果”偏好会暂停自动轮播。

项目内置的 Go2 与 Skydio X2 会作为旧配置的默认两个轮播项。上传模型会自动适配画面尺寸，并播放 GLB 中的第一段动画；未包含动画的模型仍有轻微旋转展示。为控制首页流量和显存，请在导出前压缩网格与纹理，不要把 20MB 上限当作推荐大小。

### 招新流程

1. 游客注册后填写自己的报名表。
2. 管理员在“招新管理”推进 `报名 → 初筛 → 面试 → 技能测试 → 试用期`。
3. 管理员发布面试场次并配置面试官，进入面试阶段的游客可以预约或取消。
4. 面试官在场次中叫号、记录结论或将未到场者移至队尾。
5. 进入试用期后，管理员可转换为正式成员；原报名和面试历史会保留。

### 成员与个人主页

1. 管理员在“成员管理”维护姓名、内部编号、角色、学籍信息、状态和能力标签。
2. 成员在“编辑个人主页”维护头像、标语、联系方式和富文本介绍。
3. 成员可以选择公开展示的项目与已认证成果，并调整顺序。
4. 公开接口不会返回内部编号和内部联系方式。

### 项目团队

1. 管理员创建项目并指定负责人，可选指导老师、成员和项目管理员。
2. 负责人管理团队结构；负责人、项目管理员和系统管理员维护项目资料及主图。
3. 开启“允许公开展示”后，项目会进入公开数据范围。
4. 当前团队空间用于资料和成员协作，不包含即时消息或群文件。

### 竞赛成果、讨论与通知

- 实验室成员可作为队长提交比赛记录；已结束比赛必须上传证书并等待管理员审核。
- 审核通过后，比赛详情、图集和关联成员成果可以公开展示。
- 讨论板允许匿名浏览；正式成员可发帖、回复和点赞，管理员可置顶和处理内容。
- “梅琳娜”只发送系统业务通知，不提供成员之间的私信接口。

### 成员积分

1. 教师或核心学生通过积分接口登记事项名称、类别、成果日期、任务总分、唯一来源编号、凭证链接和成员分配。
2. 竞赛成果按个人发放，每位有效成员获得完整奖项分值；项目和运营任务按总分分配，成员所得之和必须等于任务总分。
3. 实验室贡献每人每月最多 100 分；运营执行和传播效果分别每人每月最多 200 分。补录按成果发生月份校验，不按审核月份计算；超出月度上限时流水同时记录应得分和实际计入分。
4. 成员可读取本人积分分类汇总和最近 200 笔流水。发放记录不能直接修改或删除；发现错误时由管理员创建反向流水撤销。
5. 指导教师不参与积分统计，积分管理员不能给自己发分；每次发放会由梅琳娜通知对应成员。

当前后端提供积分发放、撤销、规则口径、管理员最近记录和本人明细接口；尚未提供积分管理页面及真实公开排行榜。

## 常用命令

| 命令                                   | 说明                                   |
| -------------------------------------- | -------------------------------------- |
| `npm run dev`                          | 启动 Vite 开发服务器                   |
| `npm run build`                        | 构建生产前端                           |
| `npm run preview`                      | 本地预览生产构建                       |
| `npm run lint`                         | 检查前端 JavaScript / Vue 代码         |
| `npm run lint:fix`                     | 自动修复可安全处理的 lint 问题         |
| `npm run format`                       | 使用 Prettier 统一前端、配置和文档格式 |
| `npm run format:check`                 | 检查格式但不修改文件                   |
| `npm run check`                        | 依次执行 lint、格式检查和生产构建      |
| `cd backend && ./mvnw test`            | 运行后端测试                           |
| `cd backend && ./mvnw spring-boot:run` | 启动本地 API                           |

提交前至少运行：

```bash
npm run check
cd backend && ./mvnw test
```

## 配置说明

### 前端

默认不需要环境文件。只有前后端确实部署在不同源时，才复制 `.env.example` 为 `.env.local`：

```bash
cp .env.example .env.local
```

```dotenv
VITE_API_BASE_URL=https://api.example.com
```

### 本地后端

常用可选变量：

| 变量                          | 默认值                                        | 用途                       |
| ----------------------------- | --------------------------------------------- | -------------------------- |
| `SERVER_PORT`                 | `8080`                                        | API 端口                   |
| `YESLAB_DATABASE_URL`         | `jdbc:h2:file:./data/yeslab;AUTO_SERVER=TRUE` | 数据库连接                 |
| `YESLAB_BOOTSTRAP_ENABLED`    | `true`                                        | 是否初始化本地演示数据     |
| `YESLAB_JWT_SECRET`           | 本地开发值                                    | JWT 签名密钥；生产必须替换 |
| `YESLAB_CORS_ALLOWED_ORIGINS` | 本地地址与已配置站点                          | 跨域来源白名单             |
| `YESLAB_*_DIRECTORY`          | `./data/...`                                  | 各类上传文件目录           |

首页上传的 GLB 默认保存在 `backend/data/homepage-models/`；生产环境保存到上传卷中的 `homepage-models/`，会被现有上传目录备份和迁移流程一并处理。

完整配置以 [`application.yml`](backend/src/main/resources/application.yml) 和 [`application-prod.yml`](backend/src/main/resources/application-prod.yml) 为准。生产环境变量示例位于 [`deploy/.env.production.example`](deploy/.env.production.example)。

不要提交 `.env.local`、`deploy/.env.production`、数据库、上传文件、令牌或真实账号信息；这些路径已由 `.gitignore` 排除。

## 数据与迁移

- 本地业务数据和上传文件默认位于 `backend/data/`，不进入 Git。
- 生产数据位于 MySQL 和仓库外上传目录，代码更新不会携带业务数据。
- 生产数据库结构由 `backend/src/main/resources/db/migration/` 中的 Flyway 脚本管理。
- 已发布的 Flyway 迁移不可修改或重排；结构变更必须新增更高版本迁移。
- 正式升级前先备份数据库和上传目录，并完成过至少一次恢复演练。

## 生产部署

仓库提供面向 Ubuntu 24.04 的完整部署基线：GitHub Actions 在 `main` 分支通过检查后构建 Web/API 镜像并发布到 GHCR，服务器通过 Docker Compose 运行 MySQL、Spring Boot 和 Caddy。

```text
推送 main
   ↓
GitHub Actions：前端检查 + 后端测试
   ↓
构建并发布 Web/API 镜像（latest + 提交 SHA）
   ↓
服务器 deploy.sh：备份 → 更新代码 → 拉取镜像
   ↓
MySQL → API/Flyway → 健康检查 → Web/Caddy
```

生产部署涉及域名、HTTPS、镜像权限、数据库密钥、首次管理员、备份和回滚。下面给出完整操作入口；首次上线和数据恢复仍应同时对照[正式部署手册](docs/production-deployment.md)。

### 服务器要求

- Ubuntu 24.04 LTS；其他发行版需要手动安装并验证 Docker。
- 建议至少 2 核 CPU、2 GB 内存、40 GB 磁盘，并配置 swap。
- 一个已经解析到服务器的域名。
- 云安全组开放 TCP 22、80、443 和 UDP 443，不开放 3306、8080。
- 可读取仓库的 Deploy Key；私有 GHCR 镜像还需要具有 `read:packages` 权限的 classic PAT。

服务器不需要安装 Java、Node.js、Maven、MySQL 或 Caddy，它们都在容器或构建流水线中运行。

部署 Fork 时，需要把克隆地址、`.github/workflows/ci-images.yml` 中的 GHCR 命名空间，以及生产环境中的 `YESLAB_API_IMAGE`、`YESLAB_WEB_IMAGE` 和 `docker login` 用户名替换为自己的 GitHub 账号；GHCR 镜像路径必须使用小写。

### 首次部署到 Ubuntu 24.04

以下操作在服务器的 root shell 中执行。先创建只读 Deploy Key：

```bash
apt-get update
apt-get install -y git
install -d -m 700 /root/.ssh
ssh-keygen -t ed25519 -C 'yes-lab-production' -f /root/.ssh/yeslab_deploy -N ''
cat /root/.ssh/yeslab_deploy.pub
```

将输出的 `.pub` 公钥添加到 GitHub 仓库的 **Settings → Deploy keys**，不要勾选写权限。然后测试并克隆：

```bash
ssh -i /root/.ssh/yeslab_deploy -o IdentitiesOnly=yes -T git@github.com
GIT_SSH_COMMAND="ssh -i /root/.ssh/yeslab_deploy -o IdentitiesOnly=yes" \
  git clone git@github.com:KaoXiaoYu/YES-Lab.git /opt/yes-lab
git -C /opt/yes-lab config core.sshCommand \
  "ssh -i /root/.ssh/yeslab_deploy -o IdentitiesOnly=yes"
```

先运行幂等引导脚本，把示例域名换成真实域名：

```bash
cd /opt/yes-lab
./deploy/scripts/bootstrap-ubuntu.sh \
  --domain lab.example.edu.cn \
  --admin-user teacher \
  --admin-name '系统管理员' \
  --admin-code T-001
```

脚本会安装 Docker、创建 2 GB swap、生成生产密钥和仓库外数据目录。若 GHCR 镜像是私有的，首次运行通常会在镜像拉取阶段提示 `unauthorized` 或 `denied` 并安全停止；此时 Docker 已经安装，可以用同一个 Linux 用户登录镜像仓库：

```bash
read -rsp 'GitHub PAT: ' GHCR_TOKEN
echo
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u KaoXiaoYu --password-stdin
unset GHCR_TOKEN
```

PAT 不应写入命令、配置文件或聊天记录。若镜像已经设为公开，可以跳过登录。

登录成功后原样重跑引导脚本：

```bash
cd /opt/yes-lab
./deploy/scripts/bootstrap-ubuntu.sh \
  --domain lab.example.edu.cn \
  --admin-user teacher \
  --admin-name '系统管理员' \
  --admin-code T-001
```

脚本会继续启动 MySQL/API/Web、创建首个教师管理员，并配置每天约 03:30 的自动备份。脚本最后显示的随机管理员密码只出现于终端，请立即保存到密码管理器。

引导脚本可以重复运行，已有 `deploy/.env.production`、数据库、上传文件和账号密码不会被覆盖。不要为了重试而删除 `/opt/yes-lab` 或 `/srv/yeslab/data`。

### 服务器启动、停止与查看状态

所有命令都在 `/opt/yes-lab` 执行：

```bash
cd /opt/yes-lab

# 启动全部服务并等待健康检查
docker compose --env-file deploy/.env.production \
  up -d --wait --wait-timeout 240

# 查看容器状态
docker compose --env-file deploy/.env.production ps

# 查看 API 或 Web 日志；Ctrl+C 退出日志，不会停止容器
docker compose --env-file deploy/.env.production logs --tail 200 -f api
docker compose --env-file deploy/.env.production logs --tail 200 -f web

# 停止业务服务但保留容器、数据库和上传文件
docker compose --env-file deploy/.env.production stop

# 单独重启 API
docker compose --env-file deploy/.env.production restart api
```

`restart` 只重启当前容器，不会拉取新镜像或应用新的环境变量；发布代码请使用下一节的 `deploy.sh`。Compose 已设置 `restart: unless-stopped`，Docker 服务和机器正常重启后容器会自动恢复。若此前手工执行过 `stop`，需要再次执行 `up -d`。

不要执行 `docker compose down -v`；`-v` 会删除 Caddy 命名卷。也不要删除 `/srv/yeslab/data`，其中保存正式 MySQL 与上传文件。

### 在线更新

推荐通过“提交 SHA 镜像”更新，而不是让服务器现场编译：

1. 在开发机完成修改并运行 `npm run check`、`cd backend && ./mvnw test`。
2. 提交并推送到 `main`，等待 GitHub Actions 的 **Test and publish images** 全部成功。
3. 在 GitHub 提交页面复制本次完整的 40 位提交 SHA。
4. SSH 登录服务器，将 `deploy/.env.production` 中的 `YESLAB_IMAGE_TAG` 改为该 SHA。
5. 执行发布脚本并完成上线检查。

服务器命令：

```bash
cd /opt/yes-lab
nano deploy/.env.production
# 将 YESLAB_IMAGE_TAG=latest 改为 YESLAB_IMAGE_TAG=本次完整提交SHA

./deploy/scripts/deploy.sh
```

发布脚本会自动：

- 拒绝覆盖服务器仓库中的未提交修改；
- 在更新前备份 MySQL 和上传目录；
- 以 `git merge --ff-only` 更新 `main`；
- 校验 Compose 配置并拉取指定 SHA 镜像；
- 先启动 MySQL，再启动 API 并执行 Flyway；
- API 健康检查通过后才更新 Web。

更新结束后检查：

```bash
docker compose --env-file deploy/.env.production ps
docker compose --env-file deploy/.env.production logs --tail 120 api
curl --fail https://lab.example.edu.cn/actuator/health
```

还应人工检查首页、登录、成员头像、项目封面、竞赛图片以及一个管理员页面。不要只凭容器显示 `running` 判断发布成功。

### 手动备份与自动备份

执行一次完整备份：

```bash
cd /opt/yes-lab
./deploy/scripts/backup.sh
```

备份包含 MySQL 逻辑 SQL、完整上传目录和 SHA-256 校验文件，默认写入 `/srv/yeslab/backups/<UTC时间>/` 并保留 7 天。

检查自动备份：

```bash
systemctl status yeslab-backup.timer
journalctl -u yeslab-backup.service --since today
ls -lah /srv/yeslab/backups
```

本机备份不能替代异地备份。建议把备份同步到学校存储或私有对象存储，并至少每月在临时数据库中做一次恢复演练。

### 应用版本回滚

如果新 API 未通过健康检查，发布脚本不会切换 Web。回滚前先找到上一次验证通过的提交 SHA，然后修改生产环境文件：

```bash
cd /opt/yes-lab
nano deploy/.env.production
# 将 YESLAB_IMAGE_TAG 改回上一次完整提交 SHA

docker compose --env-file deploy/.env.production pull api web
docker compose --env-file deploy/.env.production up -d --wait --wait-timeout 240
docker compose --env-file deploy/.env.production ps
```

应用镜像回滚不会自动撤销 Flyway 数据库迁移。所有迁移都应保持向前兼容；如果必须回退数据库，应停止 Web/API、校验备份，并按[正式部署手册的恢复流程](docs/production-deployment.md#6-备份验证与恢复原则)人工处理，不能直接修改 Flyway 历史或已执行 SQL。

### 常见服务器问题

| 现象                            | 常见原因                                                    | 处理                                                                |
| ------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------------------- |
| `unauthorized` / `denied`       | 当前 Linux 用户未登录私有 GHCR，或 PAT 缺少 `read:packages` | 使用实际部署用户重新执行 `docker login ghcr.io`                     |
| `Permission denied (publickey)` | Deploy Key 未绑定、使用了错误私钥或仓库 SSH 配置缺失        | 检查 `.pub` 是否添加到目标仓库，并重新运行 `ssh -T`                 |
| API `unhealthy`                 | 数据库未就绪、Flyway 失败、配置缺失或实体与表结构不匹配     | 查看 `logs --tail 200 api`，不要改回 `ddl-auto=update`              |
| HTTPS 无法访问                  | DNS 尚未生效或 80/443 被安全组拦截                          | 核对 A/AAAA 记录、TCP 80/443 和 Caddy 日志                          |
| 磁盘持续增长                    | 上传文件、备份或 Docker 日志累积                            | 检查 `df -h`、备份留存和 `/srv/yeslab/data`，不要直接删除数据库目录 |
| 服务器仓库有未提交修改          | 线上直接改过受 Git 管理的文件                               | 先审查并妥善保存改动，保持生产仓库只通过发布流程更新                |

## 文档索引

- [后端快速说明](backend/README.md)
- [API 模块边界](backend/docs/module-boundaries.md)
- [角色与访问控制](backend/docs/access-control.md)
- [正式部署、备份与迁移](docs/production-deployment.md)
- [贡献指南](CONTRIBUTING.md)
- [安全策略](SECURITY.md)
- [开发日志](DEVLOG.md)

## 参与贡献

欢迎通过 Issue 和 Pull Request 参与。提交前请先阅读[贡献指南](CONTRIBUTING.md)，保持变更聚焦、补充必要测试，并同步更新相关文档和 `DEVLOG.md`。安全漏洞不要提交公开 Issue，请按[安全策略](SECURITY.md)私下报告。

## 许可证

当前仓库尚未包含 `LICENSE`，因此即使仓库可公开访问，也不代表代码已经获得复制、修改或再分发授权。仓库所有者应在正式开源前根据预期用途选择许可证（例如 MIT、Apache-2.0 或 GPL-3.0），加入对应 `LICENSE` 后再更新本节。

`public/models/` 中的第三方 3D 模型不随项目未来许可证重新授权。其来源、修改说明和各自的 Apache-2.0 / BSD-3-Clause 文本已保留在该目录，分发时必须继续保留这些文件。
