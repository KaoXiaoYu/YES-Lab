# YES Lab

YES Lab 实验室系统采用前后端分离结构：

- 根目录：Vue 3 公开展示、登录注册、游客报名、成员与招新管理、成员个人主页、项目团队空间和竞赛成果管理
- `backend/`：Java 21 + Spring Boot 4.1.1、Spring Security、短效 JWT、JPA；本地 H2、生产 MySQL 8.4 + Flyway
- `backend/docs/access-control.md`：角色、权限矩阵、成员字段和招新状态机
- `backend/docs/module-boundaries.md`：当前 API 边界与暂不实现的模块

## 启动公开展示前端

```bash
npm install
npm run dev
```

## 启动公开展示 API

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw spring-boot:run
```

本地开发时前端统一请求同源 `/api`，Vite 会将其代理到 `http://127.0.0.1:8080`。只有前后端部署在不同域名时才需要在 `.env.local` 中设置 `VITE_API_BASE_URL`。公开首页接口不可用时仍可使用内置演示数据；登录、报名、成员详情及管理页面必须启动后端。

登录后，公开首页右上角会显示当前账号的头像和姓名。教师与核心学生可从成员系统顶部进入“成员管理”；个人主页的富文本编辑器位于独立的“编辑个人主页”页面。

系统管理员可在“成员管理”中直接创建学生管理员账号。该账号角色固定为 `CORE_STUDENT`，创建后立即拥有与教师相同的系统管理权限，并同时建立规范的学生成员档案。成员可在“编辑个人主页”上传、替换或移除头像，系统管理员也可在成员管理页协助维护；支持 JPG、PNG、WebP，单张不超过 4 MB。

新用户注册账号必须使用邮箱，密码长度为 6—18 位；邮箱统一转换为小写，并自动带入报名表的邮箱栏。原有手机号账号及实验室内部账号仍可正常登录。报名表分别保存邮箱、手机号码、微信号和自我介绍，招新管理页同步展示；旧报名的联系方式保留，升级时自动识别其中的邮箱或手机号。教师、核心学生和正式成员的内部账号继续由管理员维护。

教师与核心学生还可进入独立的“主页编辑”页面（`/admin/homepage`），统一维护实验室名称与简介、首屏文案、带跳转地址的研究方向、各栏目标题说明、概览条、关于我们特色卡片、备用比赛成果、首页动态、赞助伙伴和外部入口，以及首页展示的指导老师、核心成员和项目。成员资料、项目详情、比赛成果和新闻正文仍在对应管理模块维护，主页编辑页提供统一入口和展示选择，避免同一份数据出现两套来源。

“项目团队”模块支持管理员创建项目并指定负责人、可选指导老师、成员与项目管理员。负责人可修改团队名称和成员角色，负责人、项目管理员和系统管理员可上传项目主图；未上传时显示 YES Lab 默认图。公开展示开关决定该项目是否出现在访客首页。当前“团队空间”定位为项目资料与成员协作入口，尚未实现即时聊天。

“竞赛成果”模块由提交人作为队长创建记录。已结束比赛必须上传证书，经教师或核心学生审核后才能公开；管理员可设置首页展示及手动排序。比赛详情支持文字说明和最多 8 张 JPG、PNG 或 WebP 图片，编辑时可单独删除图集图片；关联成员会自动在个人公开主页显示获奖记录。未结束比赛记录省赛/国赛时间、指导老师和可选关联项目。系统右下角会显示最近一场未结束比赛的按天倒计时：未登录用户看全体最近场次，登录成员只看自己作为队长、关联队员或指导老师参与的最近场次。成果管理同时维护外部新闻引用，首页新闻按发布日期倒序。

招新管理支持多人面试官的线下面试场次、候选人预约/取消、固定顺序面试号、单人叫号、未到场移至队尾、面试结论及提前结束释放队列。面试通过的报名者自动从招新管理列表隐藏；取消或提前结束的场次保留 24 小时后由后台任务永久删除。讨论板向所有访客公开浏览，正式成员可使用富文本编辑器发布带格式文字、链接、网络图片和代码块的讨论及一级回复，并进行点赞；发帖时可选择发布不可修改的公告，由“梅琳娜”向全部启用账号推送。指导老师和核心成员可在发布后置顶或取消置顶，单条置顶直接展示，多条置顶折叠为可展开列表；过长的帖子、回复和成员主页讨论动态均提供展开/收起。帖子与回复共用不可回收的连续内容编号，可按发布时间、编号、点赞数和回复数排序，也可按标题、正文关键字或编号即时搜索。作者头像和姓名可进入其公开成员主页，主页同步展示该成员发布的讨论和回复。互动、面试变更与结果均由站内机器人“梅琳娜”推送，多个新消息在前端折叠提示。指导老师可以在成员管理中按角色设置梅琳娜的默认展示范围，并为指定账号单独显示或隐藏；成员没有手工发送站内消息的接口。

证书和比赛图片上传优先按文件签名识别，并扫描非标准 JPG 的前导字节；对于扩展名为 `.jpg/.jpeg`、实际由部分扫描软件导出为 BMP 的文件，后端会解码、限制尺寸并转成真正的 JPEG，而不是只修改响应类型。纯文本等不可解码内容仍会拒绝。上传表单会先显示本地图片/PDF 预览，只有后端返回证书与对应图片记录才视为保存成功。比赛审核通过后，图片证书会直接作为公开成果详情页的主图展示，PDF 证书直接嵌入页面；审核前不可公开访问。公共证书、比赛图集、头像和项目封面使用带版本参数的长期浏览器缓存，静态 Logo 与赞助商图片也由 Caddy 设置缓存。

成员可在“编辑个人主页”的“主页展示内容”中勾选、隐藏并调整本人公开项目和已认证奖项的顺序。项目、比赛、招新面试官和首页成员配置等成员选择入口使用可搜索下拉框：输入姓名或学号/内部编号后，候选人物会直接显示在输入框下方，并支持鼠标选择以及方向键、回车操作。学号只在登录后的内部接口中使用，不进入公开成员或公开项目数据。

公开首页会把每次成功读取的数据库内容保存为浏览器端公开快照，后续刷新时先同步应用该快照，再请求服务器获取最新内容，因此不会先闪现旧的内置演示页。页面打开时会立即更新，保持打开时仍按现有 30 秒周期同步；内置写死内容仅用于该浏览器从未成功访问且 API 不可用时的应急兜底。该快照只包含公开数据，不包含账号、联系方式或学号。

本地开发使用 H2 文件数据库，数据位于 `backend/data/`；证书和比赛图片默认保存在 `backend/data/achievements/`，项目主图默认保存在 `backend/data/projects/covers/`，成员头像默认保存在 `backend/data/members/avatars/`。可通过对应的 `YESLAB_*_DIRECTORY` 环境变量修改文件目录。生产环境使用 MySQL 8.4、Flyway 和仓库外持久上传目录，强制设置独立 JWT 密钥并关闭演示账号初始化。Flyway V7 只新增面试、站内消息和讨论板表；V7.1 新增讨论内容编号表并按既有内容时间顺序回填编号；V7.1.1 为讨论主题增加公告与置顶状态列；V7.1.2 增加梅琳娜角色与账号展示设置。迁移都不改名、删除或重建既有业务表，发布时由 API 自动执行，无需手工运行 SQL。

登录采用 15 分钟访问 JWT 与可轮换刷新令牌。访问令牌只放在浏览器内存；未勾选“记住我”时只维持浏览器会话，勾选后持久登录 30 天。刷新令牌使用 `HttpOnly + Secure + SameSite=Lax` Cookie，服务端仅保存摘要。

## Git 更新与服务器数据

Git 只同步代码和数据库迁移脚本，不同步账号、报名、成员、项目、比赛、主页配置和上传文件等业务数据。`backend/data/` 已加入 `.gitignore`，因此服务器执行 `git pull` 不会覆盖当前 H2 数据库和上传文件；但不应删除、重建或用新目录覆盖该持久化目录。

正式部署已配置 MySQL 8.4 + Flyway、仓库外持久目录、升级前备份、Caddy HTTPS、GitHub Actions 镜像构建和 Docker Compose。当前 Git 仓库与 GHCR 镜像均按私有资源部署：新服务器必须先把本机 Deploy Key 公钥绑定到仓库并克隆代码，再用具有 `read:packages` 权限的 GitHub classic PAT 登录 GHCR，最后在仓库目录运行引导脚本。不要在克隆失败后直接运行相对路径脚本，也不要为了重试删除已经生成的 `.env.production`。完整命令、报错对照、发布、备份和回滚方法见 [正式部署手册](docs/production-deployment.md)。

## 从旧服务器迁移正式数据

新服务器已经部署完成时，推荐迁移 MySQL 逻辑备份和完整上传目录，不要直接复制正在运行的 MySQL 数据目录。账号、报名、成员、项目、比赛和主页配置位于数据库中；头像、证书、比赛图片、项目封面和赞助商 Logo 位于上传目录中，两部分都要迁移。

先在新服务器创建接收目录：

```bash
install -d -m 0750 /srv/yeslab/migration
```

在旧服务器停止外部写入并生成最终备份。MySQL 必须保持运行，不能在执行备份脚本前停止：

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production stop web api
./deploy/scripts/backup.sh
ls -lah /srv/yeslab/backups
```

记下最新的 UTC 时间目录，例如 `20260907T120000Z`，然后仍在旧服务器执行：

```bash
scp -r /srv/yeslab/backups/20260907T120000Z \
  root@新服务器IP:/srv/yeslab/migration/
```

接下来所有命令都在新服务器执行。先校验备份并停止新站：

```bash
cd /srv/yeslab/migration/20260907T120000Z
sed -E 's#  .*/#  #' SHA256SUMS | sha256sum -c -

cd /opt/yes-lab
docker compose --env-file deploy/.env.production stop web api
./deploy/scripts/backup.sh
```

这条校验命令同时兼容旧版备份脚本写入的绝对路径和新版脚本写入的相对路径。正常结果应显示 `uploads.tar.gz: OK` 和 `yeslab.sql: OK`。

这一步先为新服务器当前状态留一份可恢复备份。随后恢复上传目录。以下路径使用默认的 `YESLAB_DATA_ROOT=/srv/yeslab/data`；如果生产配置使用其他路径，请同步替换：

```bash
if [ -d /srv/yeslab/data/uploads ]; then
  mv /srv/yeslab/data/uploads \
    /srv/yeslab/data/uploads.before-migration-$(date -u +%Y%m%dT%H%M%SZ)
fi
if [ -f /srv/yeslab/migration/20260907T120000Z/uploads.tar.gz ]; then
  tar -C /srv/yeslab/data -xzf \
    /srv/yeslab/migration/20260907T120000Z/uploads.tar.gz
else
  install -d -m 0750 /srv/yeslab/data/uploads
fi
chown -R 10001:10001 /srv/yeslab/data/uploads
```

下面的命令会删除新服务器当前的 `yeslab` 数据库，再导入旧服务器数据。执行前必须确认当前 SSH 会话连接的是新服务器，而且新库中没有需要保留的数据：

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root -e "DROP DATABASE IF EXISTS yeslab; CREATE DATABASE yeslab CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'

docker compose --env-file deploy/.env.production exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root yeslab' \
  < /srv/yeslab/migration/20260907T120000Z/yeslab.sql
```

恢复时继续使用新服务器现有的 `deploy/.env.production`。不要在 MySQL 已经初始化后用旧服务器的整份环境文件覆盖它，否则环境文件中的旧数据库密码可能与新服务器 MySQL 已创建的账号不一致。业务账号和密码哈希已经包含在 `yeslab.sql` 中；如果未迁移旧 JWT 密钥，用户可能需要重新登录。

最后启动服务并验证：

```bash
cd /opt/yes-lab
docker compose --env-file deploy/.env.production up -d --wait --wait-timeout 240 mysql api
docker compose --env-file deploy/.env.production logs --tail 120 api
docker compose --env-file deploy/.env.production exec -T api \
  curl --fail http://127.0.0.1:8080/actuator/health
docker compose --env-file deploy/.env.production up -d web
docker compose --env-file deploy/.env.production ps
```

检查登录、成员头像、竞赛成果图片、赞助商图片和后台上传后，再把域名 A/AAAA 记录切换到新服务器。旧服务器的 Web/API 应继续保持停止，避免两个数据库同时接收写入。完整检查、可选 JWT 配置迁移和回滚边界见 [正式部署手册](docs/production-deployment.md#从旧服务器迁移正式数据)。

## 本地演示账号

仅供本地开发，首次启动后端时自动创建：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `teacher` | `YesLab-Teacher-2026!` | 教师 / 系统管理员 |
| `core` | `YesLab-Core-2026!` | 核心学生 / 系统管理员 |
| `member` | `YesLab-Member-2026!` | 普通成员 |

游客账号请从 `/register` 自行注册。部署前通过环境变量替换演示密码，或设置 `YESLAB_BOOTSTRAP_ENABLED=false`。

常用安全配置：

```bash
export YESLAB_JWT_SECRET='至少32字节的随机生产密钥'
export YESLAB_BOOTSTRAP_ENABLED=false
```

## 在 VS Code 中一键启动

项目后端使用 Spring Boot 自带的嵌入式 Tomcat，不需要单独配置本机 Tomcat。

首次运行时，在项目根目录执行一次：

```bash
npm install
```

之后在 VS Code 中：

1. 使用 VS Code 打开整个 `YES Lab` 文件夹。
2. 按 `Command + Shift + P`，执行 `Tasks: Run Task`。
3. 选择 `YES Lab: 一键启动本地开发`。
4. 等待前端和后端两个终端都完成启动，然后访问 <http://127.0.0.1:5173/>。

后端 API 地址为 <http://127.0.0.1:8080/api/v1/public/home>，健康检查地址为 <http://127.0.0.1:8080/actuator/health>。停止服务时执行 `Tasks: Terminate Task`，或停止对应的 VS Code 终端。

### 亮色 / 暗色与赞助商管理

- 页面顶部可切换亮色与暗色模式；默认亮色，当前浏览器记住选择，刷新和切换页面后保持一致。
- 公开比赛详情的关联成员、队长和指导老师展示已有头像，点击头像或姓名进入公开个人主页；未上传或图片加载失败时显示姓名首字。
- 管理员在“主页编辑 → 赞助伙伴”上传 Logo（JPG、PNG、WebP，最大 4MB），维护名称、简介、官网、合作说明、合作类型、合作方向与排列顺序；上传后点击“保存主页内容”更新首页。
- 赞助商上传使用独立随机地址；生产文件位于 `/var/lib/yeslab/uploads/sponsors/avatars`，随现有上传目录一起持久化和备份。
- 当前协作功能升级需同时更新 API 与 Web；API 启动时由 Flyway 自动执行尚未应用的迁移：`V7_1__discussion_content_numbers.sql` 只负责编号映射和既有内容编号回填，`V7_1_1__discussion_announcements_and_pins.sql` 为主题增加公告与置顶状态列，`V7_1_2__melina_visibility.sql` 增加梅琳娜展示范围设置。建议继续使用现有先备份后更新的部署脚本，无需手工执行迁移 SQL。
