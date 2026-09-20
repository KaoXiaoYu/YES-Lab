# YES Lab 项目协作约定

## 协作流程

1. 每次执行任务之前，先阅读根目录的 `DEVLOG.md`。
2. 每次完成任务后，更新根目录的 `DEVLOG.md`，记录完成内容、验证结果和待办事项。
3. 对需求存在疑问时，向用户询问后再作决定，不擅自扩大功能范围。
4. 涉及新模块或流程改造时，先确认需求清单与技术设计文档，审核通过后再施工；文档与口头描述冲突时，以书面文档为准。

## 当前能力范围

- 已实现：公开展示、公开成员详情、JWT 身份认证、游客招新流程、成员个人主页、成员管理、招新管理、项目团队管理、竞赛成果审核与外部新闻引用、讨论板、站内消息、积分账本与管理员积分发放。
- 已实现：**任务模块**（新手任务为全实验室共享的大任务、管理员在其下增删子任务、子任务全部完成后审核转正；普通任务与积分自动发放；任务进度与统一审核）。迁移为 `V11__task_module.sql`、`V12__retire_probation_stage.sql`；施工依据 `docs/task-module-requirements.md` 与 `docs/task-module-design.md`，两者冲突时以需求清单为准。
- 仍只保留权限与字段、不实现业务功能：**测验、写题**（`QUIZ_MANAGE`、`QUIZ_PARTICIPATE`、`QUESTION_WRITE`）。删除这些预留权限前必须先确认。
- 任务模块尚未完成的部分仅剩上线动作：`V11`/`V12` 的预生产 MySQL 演练、部署后「批量补发新手任务」，以及真实浏览器点击验收（报名者完成新手任务 → 管理员审核转正 → 普通任务发布与计分 → 驳回重提）。

## 招新流程口径

> 以下口径**已生效**：任务模块第 2 批（删除试用期阶段与精简成员状态）已施工完成，`RecruitmentView` 的阶段序列为 `SIGNUP → SCREENING → INTERVIEW → SKILL_TEST → FORMAL_MEMBER`。

- 流程为：报名 → 初筛 → 面试 → **技能测试** → **正式成员**，**没有试用期阶段**。
- 技能测试阶段被分配到**共享的新手任务大任务**，勾完全部子任务后提交、经管理员审核通过即**直接转为正式成员**，要求同时填写学号/内部编号与能力标签。管理员在大任务里增删子任务即时对所有在途报名者生效。
- `RecruitmentStage.PROBATION` 已停用；`MemberStatus` 只保留 `TRIAL`（试用）与 `OFFICIAL`（正式）可选，`CANDIDATE`、`PAUSED`、`EXITED` 已停用。

## 阶段与状态变更原则

- 删除阶段或状态时采用**行为上删除、枚举上保留仅供读取**：不修改数据库 ENUM、不迁移存量数据，只在状态机、界面选项与后端校验上停用，保证历史记录仍能正常反序列化与显示。
- 确需迁移存量数据时使用独立 Flyway 脚本，并保证可审计：写入状态历史、以系统身份标注操作人。

## 数据库迁移约定

- Flyway 迁移只写 MySQL 语法；测试环境关闭 Flyway 且使用 H2，因此**迁移脚本无法被自动化测试覆盖**。
- 每个迁移都必须在文档中列出人工验收步骤（备份、预生产演练、部署后核对数据），不得以「构建或测试通过」代替实机验证。
- 禁止执行 `docker compose down -v`，不得删除 `/srv/yeslab/data` 下的业务数据与上传文件。

## 网站与界面构建约定

- 所有影响界面的工作（新页面、改版、组件、可访问性、响应式、动效）必须先加载 `ui-ux-pro-max` skill 并按其流程取用规则，再决定视觉与交互方案；纯后端、API、数据库、部署改动不使用该 skill。
- skill 装在用户级 `~/.agents/skills/ui-ux-pro-max`，DSH 会自动发现。若某会话未发现，可直接调用脚本：
  `python3 ~/.agents/skills/ui-ux-pro-max/scripts/search.py "<query>" --design-system -p "yes-lab" --output-dir .`（技能正文里的 `${CLAUDE_PLUGIN_ROOT}` 在本项目不解析，勿照抄）。
- 动手前先读本项目设计系统存档 `design-system/yes-lab/MASTER.md`；若同一目录下存在 `pages/<page>.md`，以页面级规则覆盖 MASTER。未经用户授权不得用 `--force` 覆盖既有存档。
- 交付前自查：对比度 ≥ 4.5:1、触控目标 ≥ 44px、键盘可见焦点、`prefers-reduced-motion`、375/768/1024/1440 断点、图标用 SVG 不用 emoji；细则查 skill 的 `references/pro-rules.md`。
- 界面改动必须做**真机截图自检**，不得以「构建通过」代替视觉验收：本地起前后端（`npm run dev` + 后端 8080），用 `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome --headless=new --no-sandbox --remote-debugging-port=9222` 经 CDP 截取 1440/375 × 亮/暗，截图放 `.codex-run/ui-review/`（已 gitignore）。注意：Chrome 自带沙箱在 DSH 沙箱下无法初始化，必须加 `--no-sandbox`。
- 视觉规则发生变化时更新 `design-system/yes-lab/` 并在 DEVLOG 记录所用查询与采纳结论。
