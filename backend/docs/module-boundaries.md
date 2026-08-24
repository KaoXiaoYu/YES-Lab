# 模块边界

## 当前实现：公开展示端

公开、只读 API 统一位于 `/api/v1/public`：

- `GET /home`：首页聚合数据
- `GET /projects`：项目列表
- `GET /projects/{slug}`：项目详情
- `GET /members`：可公开成员列表
- `GET /members/{slug}`：可公开成员详情
- `GET /rankings?board=总榜`：排行榜
- `GET /updates`：最新动态

公开端不包含登录、写操作、后台管理或权限修改。

## 仅预留：协作平台

- 未来路由前缀：`/api/v1/collaboration`
- 未来 Java 包：`cn.yeslab.platform.collaboration`
- 本阶段不创建 Controller，因此不会意外暴露接口。

## 仅预留：管理中心

- 未来路由前缀：`/api/v1/admin`
- 未来 Java 包：`cn.yeslab.platform.administration`
- 本阶段不创建 Controller，不实现登录、RBAC 或内容管理。

