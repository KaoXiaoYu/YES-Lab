# 参与贡献

感谢你愿意改进 YES Lab。为了让变更容易审查、验证和回滚，请遵循下面的协作约定。

## 开始之前

1. 阅读 [`README.md`](README.md)、[`DEVLOG.md`](DEVLOG.md) 和相关模块文档。
2. 对较大的功能先提交 Issue，说明用户场景、范围、数据变化和权限影响。
3. 不要自行扩展尚未实现的测验、积分或即时聊天业务；这类功能需要先确认产品与安全边界。
4. 安全漏洞不要提交公开 Issue，请阅读 [`SECURITY.md`](SECURITY.md)。

## 本地开发

```bash
npm ci
npm run dev
```

另开终端启动后端：

```bash
cd backend
./mvnw spring-boot:run
```

需要 Node.js 22.13+ 和 Java 21。详细步骤及演示账号见 [`README.md`](README.md#快速开始)。

## 分支与提交

- 从最新的 `main` 创建短生命周期分支。
- 推荐分支名：`feat/简短主题`、`fix/简短主题`、`docs/简短主题`。
- 一个 Pull Request 聚焦一个问题，避免混入无关格式化或重构。
- 提交信息使用简洁的祈使描述，例如 `修复面试预约并发校验`。
- 不提交 `.env*`、真实业务数据、上传文件、访问令牌、数据库备份或生产日志。

## 代码规范

- 前端使用 ESLint 和 Prettier；不要手工绕过检查。
- Vue 组件保持单一职责，页面级请求集中使用 `src/services/` 中的客户端。
- 后端沿现有领域包组织 controller、service、repository、model 和 api，不跨层直接操作数据。
- 权限校验必须在后端完成；前端路由和按钮隐藏只用于交互体验。
- 富文本、外部链接和上传文件必须继续经过服务端白名单或内容校验。
- 已发布的 Flyway 文件不可修改；数据库结构变更必须新增迁移。

## 提交前验证

```bash
npm run check
cd backend && ./mvnw test
```

如修改了部署脚本，再运行：

```bash
bash -n deploy/scripts/backup.sh
bash -n deploy/scripts/bootstrap-ubuntu.sh
bash -n deploy/scripts/deploy.sh
```

涉及界面时请检查桌面端、窄屏、键盘焦点和亮色/暗色主题。涉及权限时至少覆盖允许与拒绝两条路径。

## 文档与开发日志

- 新增或改变用户可见行为时更新 `README.md` 或相应模块文档。
- 改变环境变量、部署步骤或数据目录时同步更新示例配置和部署手册。
- 每次完成任务后在 `DEVLOG.md` 记录完成内容、验证结果和明确待办。

## Pull Request 检查清单

- [ ] 变更范围清晰，没有夹带无关修改。
- [ ] 没有提交秘密、真实数据或生成产物。
- [ ] 前端 `npm run check` 通过。
- [ ] 后端 `./mvnw test` 通过，或说明未运行原因。
- [ ] 新增行为有相应测试或明确的人工验证记录。
- [ ] 数据库变化使用新的 Flyway 迁移。
- [ ] README、模块文档与 DEVLOG 已同步。
