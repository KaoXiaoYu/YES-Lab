# 吉祥物与展示设置

- 沿用 MASTER 与现有成员管理样式；入口仅对指导老师展示。
- 指定账号行按姓名/账号、显示范围、吉祥物组织；两个选择框均有可见标签，使用原生 select 与 Vue v-model。
- 未配置账号显示梅琳娜；奶龙按账号独立选择，显示权限保持原优先级。搜索仅筛选展示，不丢失被筛掉账号的未保存选择。
- 选择框和保存按钮高度至少 44px，键盘焦点可见；375px 下双选择框各占一列且不溢出，长账号允许折行。
- 保存中禁用输入；失败保留选择与错误说明；保存成功通过 role=status 告知生效时机。
- 两套角色文案由 `src/services/notificationMascots.js` 集中维护，奶龙展示时不得混入梅琳娜标题、发送者、提示语或信箱介绍。面向混合收件人的业务说明使用中性“站内通知”。
- 奶龙通过 picture/source 响应 `prefers-reduced-motion`，减少动态时直接加载静态 SVG，避免外部 SVG 的媒体偏好刷新差异；消息弹窗与面板保持既有尺寸。
- UI/UX Pro Max 查询：`form select labels --stack vue` 未匹配，重试 `forms v-model --stack vue` 命中双向绑定规则；采纳原生表单和可见标签，未覆盖 MASTER。
