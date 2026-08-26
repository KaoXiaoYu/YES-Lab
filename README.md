# YES Lab

YES Lab 实验室系统采用前后端分离结构：

- 根目录：Vue 3 公开展示、登录注册、游客报名、成员与招新管理、成员个人主页、项目团队空间和竞赛成果管理
- `backend/`：Java 21 + Spring Boot 4.1.1、Spring Security、JWT、JPA 与 H2
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

前端默认连接 `http://localhost:8080`，也可复制 `.env.example` 为 `.env.local` 后修改地址。公开首页接口不可用时仍可使用内置演示数据；登录、报名、成员详情及管理页面必须启动后端。

登录后，公开首页右上角会显示当前账号的头像和姓名。教师与核心学生可从成员系统顶部进入“成员管理”；个人主页的富文本编辑器位于独立的“编辑个人主页”页面。

教师与核心学生还可进入独立的“主页编辑”页面（`/admin/homepage`），统一维护实验室名称与简介、首屏文案、带跳转地址的研究方向、各栏目标题说明、概览条、关于我们特色卡片、备用比赛成果、首页动态、赞助伙伴和外部入口，以及首页展示的指导老师、核心成员和项目。成员资料、项目详情、比赛成果和新闻正文仍在对应管理模块维护，主页编辑页提供统一入口和展示选择，避免同一份数据出现两套来源。

“项目团队”模块支持管理员创建项目并指定负责人、可选指导老师、成员与项目管理员。负责人可修改团队名称和成员角色，负责人、项目管理员和系统管理员可上传项目主图；未上传时显示 YES Lab 默认图。公开展示开关决定该项目是否出现在访客首页。当前“团队空间”定位为项目资料与成员协作入口，尚未实现即时聊天。

“竞赛成果”模块由提交人作为队长创建记录。已结束比赛必须上传证书，经教师或核心学生审核后才能公开；管理员可设置首页展示及手动排序。比赛详情支持文字说明和最多 8 张 JPG、PNG 或 WebP 图片，关联成员会自动在个人公开主页显示获奖记录。未结束比赛记录省赛/国赛时间、指导老师和可选关联项目。成果管理同时维护外部新闻引用，首页新闻按发布日期倒序。

本地开发使用 H2 文件数据库，数据位于 `backend/data/`；证书和比赛图片默认保存在 `backend/data/achievements/`，项目主图默认保存在 `backend/data/projects/covers/`。可通过 `YESLAB_ACHIEVEMENTS_DIRECTORY` 和 `YESLAB_PROJECTS_DIRECTORY` 修改文件目录。生产环境必须设置新的 `YESLAB_JWT_SECRET`、关闭演示账号初始化、替换正式数据库，并将上传文件迁移到具备访问控制的对象存储。

## Git 更新与服务器数据

Git 只同步代码和数据库迁移脚本，不同步账号、报名、成员、项目、比赛、主页配置和上传文件等业务数据。`backend/data/` 已加入 `.gitignore`，因此服务器执行 `git pull` 不会覆盖当前 H2 数据库和上传文件；但不应删除、重建或用新目录覆盖该持久化目录。

正式部署建议使用独立 PostgreSQL 实例和 Flyway 迁移，上传文件放在代码仓库之外的持久目录、Docker volume 或对象存储。每次更新按“备份数据库与上传文件 → 拉取代码 → 执行迁移/构建 → 重启服务 → 健康检查”的顺序进行。当前项目尚未接入 PostgreSQL 与 Flyway，在首次正式上线前完成即可。

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
