# 模块边界

## 公开展示端

公开、只读 API 位于 `/api/v1/public`，继续提供首页、项目、成员、排行榜和动态数据，不要求登录。

- `GET /api/v1/public/member-profiles`：读取已正式公开的成员目录。
- `GET /api/v1/public/member-profiles/{id}`：读取成员公开主页；不返回内部编号和内部联系方式。
- `GET /api/v1/public/project-teams`：读取已开启公开展示且未归档的项目团队。
- `GET /api/v1/public/project-teams/{id}`：读取单个公开项目；不返回项目管理员和内部权限信息。
- `GET /api/v1/public/project-teams/{id}/cover`：读取公开项目已上传的主图；无上传时由前端使用默认图。
- `GET /api/v1/public/competitions`：读取管理员选入首页的已认证比赛，按管理员排序值排列。
- `GET /api/v1/public/competitions/{id}`：读取任意已认证比赛的图文详情。
- `GET /api/v1/public/competitions/{competitionId}/images/{imageId}`：读取已认证比赛的公开图片。
- `GET /api/v1/public/news`：读取公开的外部新闻引用，按发布日期倒序。

`GET /api/v1/public/home` 同时返回可维护的 `homepageContent`，公开前端据此渲染首屏、带跳转地址的研究方向、栏目文案、概览、关于我们特色卡片、备用比赛成果、赞助伙伴和外部入口，并应用管理员选择的指导老师、核心成员与项目顺序。

## 公开主页内容管理

具有 `CONTENT_MANAGE` 权限的系统管理员可访问：

- `GET /api/v1/admin/homepage`：读取当前主页配置、最后保存时间和操作账号。
- `PUT /api/v1/admin/homepage`：整体校验并保存一版主页配置。

主页配置采用单一持久化版本，覆盖实验室品牌文案、研究方向名称与导航地址、各展示栏目文案、概览条、关于我们特色卡片、备用比赛成果、备用动态、赞助伙伴、外部入口和首页内容选择。研究方向导航地址仅允许页内锚点、站内绝对路径或 HTTP(S) URL。成员资料、项目详情、比赛记录与新闻引用继续由各自模块维护；主页配置只保存其展示对象 ID 和顺序，不复制业务数据。

## 身份认证

- `POST /api/v1/auth/register`：注册游客账号。
- `POST /api/v1/auth/login`：账号密码登录并签发 JWT。
- `GET /api/v1/auth/me`：读取当前账号、角色与权限。

JWT 使用 HS256 签名，API 保持无状态；生产环境必须替换 `YESLAB_JWT_SECRET`。当前只签发短期访问令牌，不实现刷新令牌和主动吊销。

## 游客招新

- `GET /api/v1/recruitment/me`：读取自己的报名表、当前阶段和变更历史。
- `PUT /api/v1/recruitment/me`：在报名阶段创建或修改自己的报名表。

流程固定为：报名 → 初筛 → 面试 → 技能测试 → 试用期 → 正式成员；任意非终态可进入“未通过”。技能测试阶段只保留 `linkedQuizId`，不接测验业务。

## 成员个人主页

- `GET /api/v1/member/profile`：读取自己的规范成员资料和成长数据占位。
- `PUT /api/v1/member/profile`：本人编辑头像地址、内部联系方式、主页标语和富文本内容。

姓名、编号、专业、班级、年级、成员状态和能力标签仍由管理员维护。富文本在后端通过 OWASP HTML Sanitizer 白名单清洗。

## 招新管理

教师和核心学生均拥有系统管理员权限，可访问：

- `GET /api/v1/admin/recruitment/applications`
- `GET /api/v1/admin/recruitment/interviewers`
- `PATCH /api/v1/admin/recruitment/applications/{id}/stage`
- `PUT /api/v1/admin/recruitment/applications/{id}/interview`
- `POST /api/v1/admin/recruitment/applications/{id}/convert`

一键转成员会保留原报名与面试历史，将游客账号角色改为普通成员，并创建规范成员资料。

## 成员管理

教师和核心学生可访问：

- `GET /api/v1/admin/members`
- `GET /api/v1/admin/members/{id}`
- `PUT /api/v1/admin/members/{id}`

管理员维护姓名、编号、角色、状态、专业、班级、年级、内部联系方式和能力标签。教师角色会自动清空不适用的专业、班级和年级；头像、标语和主页正文继续由成员本人维护。

## 项目团队管理

- `GET /api/v1/projects`：系统管理员读取全部项目；普通成员读取自己参与的项目。
- `GET /api/v1/projects/member-options`：读取可加入项目的试用/正式成员。
- `POST /api/v1/projects`：仅系统管理员创建项目并指定负责人、可选指导老师、成员和项目管理员。
- `GET /api/v1/projects/{id}`：读取有权访问的项目团队空间。
- `PUT /api/v1/projects/{id}`：负责人、项目管理员或系统管理员维护项目资料、指导老师与公开开关。
- `PUT /api/v1/projects/{id}/cover`：负责人、项目管理员或系统管理员上传或替换项目主图。
- `GET /api/v1/projects/{id}/cover`：项目参与者或系统管理员通过 JWT 读取内部项目主图。
- `PUT /api/v1/projects/{id}/team`：负责人或系统管理员维护团队名称、成员和项目管理员；只有系统管理员能更换负责人。

项目主图支持 JPG、PNG、WebP，最大 8MB，并校验文件签名；未上传时统一显示 YES Lab 默认图。指导老师只能关联教师账号，且独立于负责人、成员和项目管理员，不因关联而自动获得团队角色。当前团队空间不包含即时消息、文件聊天或已读状态。

## 竞赛成果与新闻管理

- `GET /api/v1/competitions`：系统管理员读取全部记录；普通成员读取已认证记录及自己担任队长或被关联的记录。
- `POST /api/v1/competitions`：试用/正式成员提交比赛；提交账号自动成为队长。
- `PUT /api/v1/competitions/{id}`：队长修改未认证记录，系统管理员可修改全部记录。
- `PUT /api/v1/competitions/{id}/certificate`：队长或管理员替换证书；已结束比赛重新进入待审核。
- `PUT /api/v1/competitions/{id}/images`：队长或管理员替换比赛图集，最多 8 张。
- `GET /api/v1/competitions/{id}/certificate`：只有队长和系统管理员可读取私有证书。
- `PATCH /api/v1/admin/achievements/competitions/{id}/review`：系统管理员通过或驳回已结束比赛。
- `PATCH /api/v1/admin/achievements/competitions/{id}/display`：系统管理员维护首页开关和手动排序。
- `/api/v1/admin/achievements/news`：系统管理员创建、读取和修改外部新闻引用。

已结束比赛必须填写获奖结果和比赛日期并上传 PDF/JPG/PNG 证书；未结束比赛必须填写省赛、国赛时间和指导老师。队员与指导老师可关联成员系统账号，也可只保留展示姓名；关联项目仅允许队长选择自己参与的项目。证书不进入公开响应，公开图集仅在比赛认证通过后可访问。

## 暂不实现

- 测验与写题业务接口。
- 积分计算、积分变更与排行榜写入。
- 新闻正文抓取或复制；当前只保存外部标题、来源、链接、摘要和发布日期。
- 竞赛记录删除、批量导入和通用操作审计。
- JWT 刷新、吊销、密码重置与完整通用审计系统。
