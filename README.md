# OpenLIMS

OpenLIMS（Open Laboratory Information Management System）是面向实验室的公开展示、成员成长和科研协作管理平台。它把官网、个人主页、招新、项目、竞赛、讨论、通知、任务、悬赏、积分和基金放在同一套账号与权限体系中。本项目沿用已有业务实现，适合高校、科研团队和学生创新实验室。

项目仓库：[YESlab-UAVtech/OpenLIMS](https://github.com/YESlab-UAVtech/OpenLIMS)。项目代码采用 [MIT](LICENSE)；第三方资源仍按各自许可分发。

## 已实现的模块

| 模块               | 入口                                                               | 用途                                                                                    |
| ------------------ | ------------------------------------------------------------------ | --------------------------------------------------------------------------------------- |
| 公开官网           | `/`                                                                | 简介、研究方向、项目、成员、成果、新闻、伙伴、3D 模型轮播、真实积分预览、基金摘要与日程 |
| 主页内容编辑       | `/admin/homepage`                                                  | 文案、栏目开关、精选与排序、伙伴 Logo、外链、GLB 上传与轮播                             |
| 公开成员与成果     | `/members/:profileId`、`/competition-results/:competitionId`       | 安全的公开资料、项目和已审核获奖成果                                                    |
| 账号与权限         | `/login`、`/register`、个人页密码表单                              | 教师、核心学生、成员、游客；JWT、刷新轮换、注销、改密与管理员重置                       |
| 成员管理与个人主页 | `/admin/members`、`/profile`、`/profile/edit`、`/complete-profile` | 角色、状态、档案、标签、头像、富文本、展示顺序、积分热力图与个人日程                    |
| 招新与面试         | `/application`、`/admin/recruitment`                               | 报名、初筛、场次预约、叫号、评价、技能测试、共享新手任务与直接转正                      |
| 项目团队           | `/projects`、`/projects/new`、`/projects/:projectId`               | 负责人、导师、团队成员、项目管理员、主图、进度和公开展示                                |
| 竞赛、成果与新闻   | `/competitions`、`/admin/achievements`                             | 团队参赛登记、私有报名截图、完赛结果、证书/图集审核与外部新闻引用                       |
| 讨论               | `/discussions`                                                     | 公开阅读、成员发帖/回复/点赞、公告、置顶及内容管理                                      |
| 通知               | `/inbox`、通知中心                                                 | 业务系统消息、已读状态与按账号配置吉祥物；没有成员私信                                  |
| 普通与新手任务     | `/tasks`、`/admin/tasks`、`/admin/tasks/onboarding`                | 分配、子任务、提交、审核、普通任务按人补发、新手延期与转正                              |
| 悬赏与奖金         | `/bounties`、`/admin/bounties`                                     | 自主接取、名额、先完成先得奖金、顺延与线下发放/本人领取台账                             |
| 积分               | `/points`、`/admin/points`、个人页                                 | 库内项目/获奖竞赛关联发放、限额、账本、冲正、榜单与到期任务结算                         |
| 实验室基金         | `/fund`、首页摘要                                                  | 单个人民币账户的期初、收支、余额、撤销和审计记录                                        |

逐个模块的角色、操作流程和限制见[使用指南](docs/user-guide.md)。测验、写题、项目即时聊天和竞赛批量导入没有实现；相关权限与历史字段保留，不把预留项作为可用功能。

## 技术栈与结构

- 前端：Vue 3、Vue Router、Vite 8、Tiptap、Three.js、Lucide SVG、ESLint/Prettier。
- 后端：Java 21、Spring Boot 4.1.1、Spring Security、JPA、Bean Validation、HTML 白名单清理与上传校验。
- 本地数据库：H2 文件库（Flyway 关闭）；测试使用隔离 H2；生产：MySQL 8.4、Flyway、Hibernate `validate`。
- 生产：Docker Compose、Caddy HTTPS、GHCR、GitHub Actions。访问 JWT 15 分钟、HttpOnly 刷新 Cookie，业务请求用 `Authorization: Bearer …`。

```text
config/               # 共享品牌与可配置外观
src/                  # 真实页面、组件、路由和 API 客户端
public/               # Logo、分享图、内置模型及第三方许可
backend/src/          # 业务实现、配置、V1—V22 等历史迁移与测试
backend/docs/         # API 边界、访问控制
scripts/              # 业务冒烟、受控临时 MySQL
deploy/              # Caddy、环境示例、部署/备份/引导脚本
docs/                # 操作手册、需求、设计与验证记录
design-system/       # 当前设计规则
```

本地 Vite 把 `/api`、`/actuator` 转发到 8080；生产 Caddy 提供 SPA、HTTPS 和 API 代理，路由刷新由 `index.html` 回退处理。数据库、上传、备份和环境密钥均不进入 Git。

## 本地启动

需要 Node.js 22.13+、npm 10+、JDK 21。Maven Wrapper 自带 Maven；本地无需安装 MySQL。

```bash
git clone https://github.com/YESlab-UAVtech/OpenLIMS.git
cd OpenLIMS
npm ci
node --version
npm --version
java -version
```

如 macOS 的默认 Java 是 8，在启动后端的终端先执行 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` 和 `export PATH="$JAVA_HOME/bin:$PATH"`。Linux/Windows 选择相应的 JDK 21 路径。

终端一：

```bash
cd backend
./mvnw spring-boot:run
```

Windows 使用 `mvnw.cmd spring-boot:run`。终端二在仓库根目录执行：

```bash
npm run dev -- --host 127.0.0.1
```

访问终端显示的地址（通常 `http://localhost:5173`）；健康检查 `http://localhost:8080/actuator/health`。Vite 端口被占用时会换端口，不要把新端口的页面误当成旧进程。VS Code 的一键启动任务目前按 macOS 的 JDK 选择方式配置，其他系统直接用上述两个终端。

本地首次启动幂等创建演示账号与示例项目：

| 账号      | 密码                     | 角色           |
| --------- | ------------------------ | -------------- |
| `teacher` | `OpenLIMS-Teacher-2026!` | 教师管理员     |
| `core`    | `OpenLIMS-Core-2026!`    | 核心学生管理员 |
| `member`  | `OpenLIMS-Member-2026!`  | 正式成员       |

游客通过注册创建。演示账号只在 bootstrap 开启时创建，已有账号不会重置密码；演示数据写入 `backend/data/`。生产 profile 强制关闭演示初始化，首次管理员单独创建。管理员重置密码当前为固定值 `OpenLIMS521`，需要可信渠道告知本人并立即改密。

## 品牌与外观配置

名称和图片路径只改 [`config/branding.json`](config/branding.json)：`name`、`displayName`、`fullName`、`description`、`logo`、`logoOnDark`、`favicon`、`shareImage`、`repositoryUrl`。替换 Logo 时把文件放到 `public/`，同时提供深色底版本；默认 SVG 字标内的文字需要随图片本身修改。前端、HTML 元数据和 Java 默认内容共享该配置，修改后重建前后端。源码身份优先于历史 CMS 名称，其他主页文案和内容仍从主页编辑维护，既有数据不被批量改写。

[`config/appearance.json`](config/appearance.json) 提供四套共用真实业务页面的外观：

| 配置值            | 适合场景                 | 风格                         |
| ----------------- | ------------------------ | ---------------------------- |
| `general`（默认） | 通用科研团队             | 蓝灰、系统无衬线、均衡圆角   |
| `academic`        | 基础研究、人文及学术机构 | 靛蓝纸面、衬线标题、细线分区 |
| `engineering`     | 计算机、机器人及工程团队 | 青蓝、等宽标题、网格与直角   |
| `life-science`    | 生命科学、环境及交叉研究 | 森林绿、柔和表面与较大圆角   |

修改 `defaultPreset`，或复制 `.env.example` 为 `.env.local` 设置 `VITE_UI_PRESET=academic`。环境变量优先，非法配置会明确报错。每套可独立修改 `light`、`dark` 语义颜色、`fontBody`、`fontHeading`、`radius`；修改后重启 Vite/重新构建。亮暗切换仍按浏览器保存。无外部字体服务依赖，科研领域文字/模型可由主页编辑自行调整。

CI 构建通过仓库变量 `OPENLIMS_UI_PRESET` 选择外观，默认 `general`；本地 Docker 构建可传 `--build-arg VITE_UI_PRESET=engineering`。这是构建时配置，生产环境文件修改不能改变已有镜像。

## 检查与生产部署

```bash
npm run check
cd backend
./mvnw package
```

`npm run check` 包含 lint、全仓格式检查和生产构建；`package` 包含测试与 JAR 打包。业务冒烟脚本会创建数据，只能用于隔离环境，先审查鉴权和可重复执行条件。

[正式部署手册](docs/production-deployment.md) 包含环境要求、Fork 发布、初始管理员、Ubuntu 引导/手动部署、升级、备份恢复及排错。生产不使用 H2/演示密码，也不使用 `npm run preview`。

**既有数据库升级限制：** 上传前已取回官方仓库历史，恢复上轮仅改过变量名的 V20 原件，全部历史迁移与远端基线逐字节一致。历史 SQL 内部旧变量名为兼容性例外保留，不作为产品品牌。升级前仍必须核对实际已部署提交与 `flyway_schema_history`，有差异停止部署。禁止改 checksum 或执行 `repair` 掩盖问题。H2 测试不覆盖 MySQL SQL；新增结构一律使用更高版本迁移，预生产 MySQL 8.4 演练和备份恢复仍是上线前必要步骤。

## 文档与参与贡献

- [模块使用指南](docs/user-guide.md)、[品牌与外观技术方案](docs/open-source-adaptation.md)
- [部署、初始化、升级与恢复](docs/production-deployment.md)
- [后端启动](backend/README.md)、[API 模块边界](backend/docs/module-boundaries.md)、[角色权限](backend/docs/access-control.md)
- [验证结果与限制](docs/open-source-verification.md)、[开发日志](DEVLOG.md)
- [贡献指南](CONTRIBUTING.md)、[安全策略](SECURITY.md)

项目代码采用 MIT。`public/models/` 中模型的来源、修改说明及 Apache-2.0/BSD-3-Clause 许可必须继续保留，不能用 MIT 替代第三方许可。第三方依赖和实验室自行上传的内容同样不被本项目许可证重新授权。
