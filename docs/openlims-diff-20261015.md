# OpenLIMS 最新变更与 YES Lab 对比

日期：2026-10-15。用途：为本项目设计提供源码依据；不代表已同步或已施工。

## 固定基线与验证证据

- YES Lab：`cf6b52fe36ad6077b6f42b8dab4fc2a19a025d02`，本地 HEAD、origin/main 与 GitHub API 一致。
- OpenLIMS：[`3b624a6209c64b0c70841a2d02c899d909f9315b`](https://github.com/YESlab-UAVtech/OpenLIMS/tree/3b624a6209c64b0c70841a2d02c899d909f9315b)，main 最新提交，提交时间 2026-10-13 19:09（北京时间）。
- 比较范围：此前已同步的 `a2df1b67468c664ec0cef3d254f992edee6415d6` 至上述最新提交；GitHub compare 与下载源码交叉核对。
- 上游 [Actions 38388257108](https://github.com/YESlab-UAVtech/OpenLIMS/actions/runs/38388257108) 整体 success；test、openlims-web、openlims-api 三个 job 全部 success。这只证明上游提交的 CI，不能替代 YES Lab 的测试、MySQL 演练或部署验证。
- 上游源码快照及比较清单在忽略目录 `.codex-run/upstream-openlims-3b624a6/`、`.codex-run/openlims-latest-compare.json`。

## 功能对比

| 项目           | OpenLIMS 最新源码                                                    | YES Lab 当前源码                           | 本项目设计处理                                        |
| -------------- | -------------------------------------------------------------------- | ------------------------------------------ | ----------------------------------------------------- |
| 成员顶栏       | Logo 返回公开首页；后台为直接链接；按宽度折叠                        | 已有宽度测量，但仍有公开首页项和后台子菜单 | 增量调整入口，保留本地 Logo、权限、光晕及已有导航行为 |
| 个人主页       | ProfileTodayPanel 合入 ProfileView；/today 兼容跳转                  | TodayView 与 ProfileView 独立              | 统一入口并保留本人待办、资料、积分、密码与补资料守卫  |
| 公开热力图     | 新增公开聚合 DTO 与读取接口                                          | 无对应公开积分接口                         | 仅返回总分、服务端日期、每日汇总；先校验公开成员资格  |
| 任务管理       | AdminTaskManagementView，三个同级分类；旧入口兼容                    | 三个独立视图；侧栏含悬赏管理               | 只提取外壳与入口，保留现有任务服务及操作              |
| 全局报名表基础 | schema、草稿 revision、不可变版本、默认模板、权限接口、初始化器、V23 | 无这些结构和接口；最高迁移 V22             | 可参考并适配 `cn.yeslab` 包名，须先审计本项目迁移历史 |
| 动态报名/附件  | 实体预留，无动态提交与附件业务接口                                   | 固定表单及私有作品图片                     | 按本项目设计新增完整提交、下载与审核读取              |
| 报名表三端页面 | 仍为固定报名页与审核页，无编辑器                                     | 同样未接入                                 | 共用模块渲染器，编辑器、申请者、审核三端共同接入      |
| MySQL 实机     | 上游文档明确 V23 演练未执行                                          | 本项目未演练新增结构                       | 独立记录实机结果，不能引用上游 H2/CI 代替             |

源码依据：[导航/主页/任务设计](https://github.com/YESlab-UAVtech/OpenLIMS/blob/3b624a6209c64b0c70841a2d02c899d909f9315b/docs/navigation-profile-task-design.md)、[报名表设计及第一批交接](https://github.com/YESlab-UAVtech/OpenLIMS/blob/3b624a6209c64b0c70841a2d02c899d909f9315b/docs/recruitment-form-design.md)。上游文档中保留了部分“拟定/尚待审核”旧措辞；本次以实际源码和 DEVLOG 的实施记录判断完成范围，不把上游审批当作 YES Lab 审批。

## 必须保留的本地差异

- `PortalShell.vue` 与此前上游有明显差异；现有按宽度导航和 YES Lab 品牌应做局部合并。公开首页已有姓名反色与弹簧光晕，原需求未要求调整公开首页栏目，本轮设计不追加该项。
- `AdminOnboardingTaskView.vue` 与此前上游不同，包含本地进度、截止后审核提示、延长与补发能力；不得整文件覆盖。`AdminRecruitmentView.vue` 也有本地差异。
- 普通/悬赏任务保留 TaskSubmissionProgress、补发、名单、排序、抽屉、奖金履约及本地实际截止规则。历史文档与当前业务行为的矛盾另行处理，本设计不更改规则。
- 默认报名 schema 从本地固定字段和 DTO 推导，保留兴趣方向、技能、意向标签、自我介绍、经历、展示、链接、作品和技术题；无版本旧报名按原内容显示。
- 不同步上游 VS Code、code.cpp、演示数据、品牌、部署命名、既有迁移等无关改动。

## 交付边界

本对比/设计阶段只新增本项目需求清单与技术设计并更新 DEVLOG；当时业务代码、设计系统和生产数据不变。用户随后批准按批次增量实施，实施情况见本项目 DEVLOG；不能把上游第一批成果记作本项目已经完成。
