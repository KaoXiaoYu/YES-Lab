# YES Lab API

YES Lab API 是平台的 Spring Boot 后端，提供身份认证、公开内容、成员、招新、项目、竞赛成果、讨论板和站内通知接口。

## 技术基线

- Java 21
- Spring Boot 4.1
- Spring Security + JWT + BCrypt
- Spring Data JPA
- 本地 H2；生产 MySQL 8.4 + Flyway
- Maven Wrapper

## 本地运行

从本目录执行：

```bash
./mvnw spring-boot:run
```

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS 上如果默认 Java 不是 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw spring-boot:run
```

服务默认运行在 <http://127.0.0.1:8080>：

- 健康检查：`GET /actuator/health`
- 公开首页：`GET /api/v1/public/home`
- 公开首页 3D 模型：`GET /api/v1/public/homepage/models/{id}`
- 当前账号：`GET /api/v1/auth/me`

本地配置默认启用 H2 文件数据库，数据写入 `backend/data/`。生产环境必须启用 `prod` profile，并提供 MySQL、JWT、CORS 和持久化上传目录配置。

具有 `CONTENT_MANAGE` 权限的账号可通过 `POST /api/v1/admin/homepage/models` 上传单个最大 20MB 的 GLB 2.0 文件。返回的公开 URL 需要写入主页配置并保存后才会进入轮播；文件默认保存在 `data/homepage-models/`。

## 测试与构建

```bash
./mvnw test
./mvnw package
```

测试使用独立的内存 H2 配置，不会读取或修改 `backend/data/` 中的本地开发数据。

## 包结构

```text
cn.yeslab.platform
├── achievement      # 竞赛成果、证书、图集和新闻
├── common           # 通用 API 响应与错误处理
├── config           # 安全、Web、本地数据和首管理员配置
├── discussion       # 讨论、回复、点赞、公告与置顶
├── identity         # 账号、角色、JWT 与刷新会话
├── member           # 成员资料、头像和公开主页
├── notification     # 梅琳娜站内通知与展示设置
├── project          # 项目资料、团队和主图
├── publicsite       # 公开聚合接口与主页 CMS
└── recruitment      # 报名、面试、状态机和作品集
```

接口和业务边界见 [`docs/module-boundaries.md`](docs/module-boundaries.md)，角色权限见 [`docs/access-control.md`](docs/access-control.md)。完整启动、前端联调和生产部署说明见[根目录 README](../README.md)。

## 数据库变更约定

- 本地 profile 使用 Hibernate `ddl-auto=update` 方便开发。
- 生产 profile 使用 `ddl-auto=validate`，只允许 Flyway 管理结构。
- 不修改已经发布的迁移文件；每次结构变更新增更高版本 SQL。
- 迁移应兼容已有正式数据，避免无备份的删除或重建操作。
