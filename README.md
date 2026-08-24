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
