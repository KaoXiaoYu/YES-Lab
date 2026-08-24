# YES Lab

YES Lab 实验室系统当前只实现公开展示端，仓库采用前后端分离结构：

- 根目录：Vue 3 公开展示页面
- `backend/`：Java 21 + Spring Boot 4.1.1 只读公开 API
- `backend/docs/module-boundaries.md`：未来协作平台和管理中心的预留边界

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

复制 `.env.example` 为 `.env.local` 后，Vue 会从 `http://localhost:8080/api/v1/public/home` 获取数据。未配置或后端不可用时，页面自动使用内置演示数据。

当前阶段不会暴露 `/api/v1/collaboration` 或 `/api/v1/admin` 接口。

