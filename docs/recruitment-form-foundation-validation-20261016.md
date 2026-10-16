# 报名表基础能力验收与迁移步骤

日期：2026-10-16。范围：第二批数据、全局配置、不可变发布版本与权限接口；不代表动态报名、附件业务或三端页面已完成。

## 1. 实机环境与边界

- 本地现有 MySQL 9.5.0，InnoDB，默认 REPEATABLE READ；本机 Docker daemon 不可用，没有安装新软件或声称使用 MySQL 8.4。
- 专用实例只经 `scripts/temp-mysql.sh` 启动，目录 `/private/tmp/yeslab-form-v23-20261016`、端口 3399、45 分钟 TTL、20MB 日志上限；所有演练库和测试文件为本轮创建，与生产无连接。
- 上一批应用 `e293a03` 从 Git 历史导出至忽略目录编译，以 Flyway/ddl-auto=validate 初始化旧库至 V22；含小版本在内共 25 条历史记录。经旧接口实际注册、提交含技术回答的报名，并上传一份私有 WebP 作品；备份数据库、上传目录与历史校验和，再用新版升级。
- 新版同样开启 Flyway、Hibernate 只验证结构；不能让 ddl-auto=update 补结构后冒充迁移成功。结算定时器关闭，上传目录隔离，临时后端使用有界日志与 TTL。

## 2. 验收记录

- V22 旧库升级：V23 已由真实 Flyway 执行，Hibernate validate 通过；原 25 条迁移及校验和不变。旧报名仍为 legacy，新增版本/答案列为空且答案 revision 为 0，报名内容/状态/技术回答、作品字节和鉴权读取保持。
- 47 张既有业务表行数保持。登录验收必然新增 refresh_tokens，仅排除该会话表与新配置表、Flyway 表；不把验收登录误判为迁移改写数据。
- 直接重复 V23 SQL 通过；在隔离结构克隆中，错误列类型在添加新业务结构前拒绝，同名错误唯一键拒绝；已存在兼容 form_version_id 时补齐另两列并支持重复执行。
- 新增 19 项配置/权限/版本/校验/并发测试通过，后端全量 142 项回归通过；修复后真实 InnoDB 五轮双请求并发发布均成功且每轮只生成一个版本。空库完整迁移至 V23、默认十模块/唯一初始版本、草稿不改变发布及再次启动保留草稿/版本均已通过。
- 实机已复现上游读取顺序问题：并发发布可能返回“报名表版本记录缺失”500。修正为配置锁先于一致性读取后，五轮并发复验全部通过；见忽略目录 `mysql-before-lock-fix.log` 和最终报告。
- 全量回归发现既有 TaskApiTests 把正常提交任务截止日写死为 2026-10-15，本批仅调整该测试用实验室日期生成有效窗口，不修改任务服务、冻结、审核或结算行为。

本轮日志/报告：`.codex-run/batch2/` 下的 `foundation-tests.log`、`full-tests-final.log`、`mysql-report.json`、`ddl-guards-report.json`，以及旧库/升级/空库的启动日志。应用与临时实例收尾状态见 DEVLOG。

## 3. MySQL 8.4 与部署人工验收

以下仍待在具备 MySQL 8.4 的专用预生产副本执行。本机 9.5 演练与 H2/CI 不替代此项；未读取或修改生产数据库。

1. 备份目标数据库及完整上传目录，记录业务表数量/旧报名内容与阶段、作品文件清单/哈希；保存 `flyway_schema_history` 全部版本及校验和。可用 `mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF` 生成可移植副本，并在隔离库验证备份可读。连接信息按环境提供，不写入仓库。
2. 核对 MySQL 8.4、InnoDB、accounts/recruitment_applications 的 BINARY(16) 主键和迁移账户建表/ALTER/CREATE ROUTINE 权限。核对 V23 是否已应用；已有迁移 checksum 不符即停止，禁止修改旧 SQL 或 repair。
3. 分别准备空库与旧结构副本，开启 Flyway，以 ddl-auto=validate 启动对应构建。空库应执行全链；旧库只新增 V23。检查四张新表、三个新列及 15 个主键/唯一键/外键；版本号、附件 storedName、账号+requestKey 必须唯一。
4. 旧报名 form_version_id/form_answers_json 保持 NULL，form_answers_revision=0；旧阶段、回答、图片/文件保持。重复启动仅一份默认配置与初始发布，不重置草稿或历史版本。
5. 在专用副本直接重复新增 SQL，核对无重复表/列/约束，不改 Flyway 历史来模拟重跑。额外在一次性结构克隆中制造错误列类型和同名错误唯一键，确认 V23 明确失败；失败库弃用或从备份重建，不在生产尝试破坏性负例。
6. 管理员经 Authorization JWT 调用 GET `/api/v1/admin/recruitment/form`；PUT `/draft` 使用当前 expectedRevision，POST `/publish` 使用保存返回的 revision。草稿不改变发布，旧 revision 返回 409；并发编辑一成功一冲突，同草稿并发发布均成功且返回同一个版本 ID，重复多轮确认无 500 或额外版本。
7. 管理版本读取验证旧快照不可变、普通成员/游客/匿名管理访问被拒；本人 `/api/v1/recruitment/me/form` 正确识别 legacy/当前版本/已绑定原版本。基础层不提前验证尚未实现的动态提交或附件业务。
8. 部署后核对实际 SHA、V23 success/校验和、四表三列、默认配置/版本、旧报名和私有作品可读。上传目录不更名、不删除；新版本报名真正上线后回退应用须先评估旧版写入能力，必要时暂停写入并执行已审核恢复方案。
9. 临时实例的参数放在脚本子命令前，例如 `scripts/temp-mysql.sh --dir /private/tmp/yeslab-form-rehearsal --port 3399 --ttl 45 --log-cap 20 init`，然后同参数 start/status；收尾 stop 并写明已关停。禁止手工 mysqld、复用生产目录、down -v 或跨会话保留。

## 4. 后续交付门槛

动态提交/技术题快照、附件鉴权与事务、幂等/阶段并发需第三批完成；管理编辑器、申请者、审核三端共同接入及真实界面截图需第四批完成。基础配置可发布不意味着当前固定报名页面已经动态化。
