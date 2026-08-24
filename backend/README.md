# YES Lab API

公开展示端的 Spring Boot API。当前数据仓库为内存演示实现，后续可在不改变 Controller 与 Service 的情况下替换成数据库实现。

## 技术基线

- Java 21
- Spring Boot 4.1.1
- Maven 3.9+

## 本地运行

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw spring-boot:run
```

服务默认运行在 `http://localhost:8080`，公开首页接口为：

```text
GET http://localhost:8080/api/v1/public/home
```

## 前端接入

复制根目录 `.env.example` 为 `.env.local`，保持：

```text
VITE_API_BASE_URL=http://localhost:8080
```

如果未配置后端地址或接口暂时不可用，公开端会继续显示内置演示数据。

