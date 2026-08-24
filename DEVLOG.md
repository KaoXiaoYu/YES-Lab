# YES Lab 开发日志

## 2026-08-24：公开展示端初版

- 完成 Vue 3 公开展示首页，包含项目、成果、成员、排行榜、动态和外部入口。
- 完成响应式布局、项目详情弹窗、排行榜切换和社交分享预览。
- 已公开发布站点：<https://yes-lab-public.funskii55.chatgpt.site>。
- 当前页面数据仍为前端静态演示数据。

## 2026-08-24：Spring Boot 后端搭建（已完成）

- 新增 Java 21、Spring Boot 4.1.1、Maven Wrapper 后端工程。
- 实现 `/api/v1/public` 下的首页聚合、项目、成员、排行榜和动态只读接口。
- 当前使用可替换的内存仓库提供演示数据，未引入数据库和后台写入功能。
- Vue 新增 API 适配层；配置 `VITE_API_BASE_URL` 时读取后端，接口不可用时回退到内置数据。
- 协作平台与管理中心只保留包结构、未来路由前缀和边界文档，未创建控制器或业务逻辑。
- 新增 `AGENTS.md` 使项目级协作指令可被 Codex 自动识别；原 `AGENT.md` 文件名不具备该能力。

### 验证结果

- Spring Boot 测试：3 项通过，0 失败。
- 健康检查：`/actuator/health` 返回 `UP`。
- 公开首页接口：返回 3 个项目、4 个公开成员和 5 类排行榜。
- 不存在的项目：返回 HTTP 404。
- Vue 生产构建：通过。

### 后续待办

- 确认正式数据库和后端部署环境后，将内存仓库替换为持久化实现。
- 获得真实成员、项目、成果及外部平台链接后替换演示数据。

## 2026-08-24：线上访问故障诊断

- Sites 项目状态为 active，最新版本已发布，访问模式为 public。
- 域名 DNS 和 TLS 正常，但访问 `yes-lab-public.funskii55.chatgpt.site` 被 Cloudflare 安全层直接返回 HTTP 403。
- 返回页面为 `Attention Required! / Sorry, you have been blocked`，请求尚未到达站点 Worker。
- 结论：故障发生在托管域名的 Cloudflare/WAF 访问层，不是 Vue、Spring Boot 或站点权限问题。
- 建议：绑定独立域名后复测，或改用目标访问地区稳定可达的静态托管平台。
