# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** YES Lab
**Generated:** 2026-08-24 11:37:32
**Category:** Research Lab / University Department
**Design Dials:** Variance 7/10 (Balanced / Modern) | Motion 5/10 (Standard) | Density 5/10 (Standard)

---

## Global Rules

### Color Palette

| Role             | Hex       | CSS Variable               |
| ---------------- | --------- | -------------------------- |
| Primary          | `#1E3A5F` | `--color-primary`          |
| On Primary       | `#FFFFFF` | `--color-on-primary`       |
| Secondary        | `#2563EB` | `--color-secondary`        |
| On Secondary     | `#FFFFFF` | `--color-on-secondary`     |
| Accent/CTA       | `#A16207` | `--color-accent`           |
| On Accent/CTA    | `#FFFFFF` | `--color-on-accent`        |
| Background       | `#F8FAFC` | `--color-background`       |
| Foreground       | `#0F172A` | `--color-foreground`       |
| Card             | `#FFFFFF` | `--color-card`             |
| Card Foreground  | `#0F172A` | `--color-card-foreground`  |
| Muted            | `#E9EEF5` | `--color-muted`            |
| Muted Foreground | `#475569` | `--color-muted-foreground` |
| Border           | `#CBD5E1` | `--color-border`           |
| Destructive      | `#DC2626` | `--color-destructive`      |
| On Destructive   | `#FFFFFF` | `--color-on-destructive`   |
| Ring             | `#1E3A5F` | `--color-ring`             |

**Color Notes:** Institutional navy + research accent + serif headings

### Typography

- **Heading Font:** EB Garamond
- **Body Font:** Crimson Text
- **Mood:** academic, old-school, university, research, serious, traditional
- **Google Fonts:** [EB Garamond + Crimson Text](https://fonts.googleapis.com/css2?family=Crimson+Text:wght@400;600;700&family=EB+Garamond:wght@400;500;600;700;800&display=swap)

**CSS Import:**

```css
@import url('https://fonts.googleapis.com/css2?family=Crimson+Text:wght@400;600;700&family=EB+Garamond:wght@400;500;600;700;800&display=swap');
```

### Spacing Variables

_Density: 5/10 — Standard_

| Token         | Value             | Usage                     |
| ------------- | ----------------- | ------------------------- |
| `--space-xs`  | `4px` / `0.25rem` | Tight gaps                |
| `--space-sm`  | `8px` / `0.5rem`  | Icon gaps, inline spacing |
| `--space-md`  | `16px` / `1rem`   | Standard padding          |
| `--space-lg`  | `24px` / `1.5rem` | Section padding           |
| `--space-xl`  | `32px` / `2rem`   | Large gaps                |
| `--space-2xl` | `48px` / `3rem`   | Section margins           |
| `--space-3xl` | `64px` / `4rem`   | Hero padding              |

### Shadow Depths

| Level         | Value                          | Usage                       |
| ------------- | ------------------------------ | --------------------------- |
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)`   | Subtle lift                 |
| `--shadow-md` | `0 4px 6px rgba(0,0,0,0.1)`    | Cards, buttons              |
| `--shadow-lg` | `0 10px 15px rgba(0,0,0,0.1)`  | Modals, dropdowns           |
| `--shadow-xl` | `0 20px 25px rgba(0,0,0,0.15)` | Hero images, featured cards |

---

## Component Specs

### Buttons

```css
/* Primary Button */
.btn-primary {
  background: #a16207;
  color: white;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}

.btn-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* Secondary Button */
.btn-secondary {
  background: transparent;
  color: #1e3a5f;
  border: 2px solid #1e3a5f;
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 200ms ease;
  cursor: pointer;
}
```

### Cards

```css
.card {
  background: #f8fafc;
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-md);
  transition: all 200ms ease;
  cursor: pointer;
}

.card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}
```

### Inputs

```css
.input {
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 200ms ease;
}

.input:focus {
  border-color: #1e3a5f;
  outline: none;
  box-shadow: 0 0 0 3px #1e3a5f20;
}
```

### Modals

```css
.modal-overlay {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.modal {
  background: white;
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-xl);
  max-width: 500px;
  width: 90%;
}
```

---

## Style Guidelines

**Style:** Swiss Modernism 2.0

**Keywords:** Grid system, Helvetica, modular, asymmetric, international style, rational, clean, mathematical spacing

**Best For:** Corporate sites, architecture, editorial, SaaS, museums, professional services, documentation

**Key Effects:** display: grid, grid-template-columns: repeat(12 1fr), gap: 1rem, mathematical ratios, clear hierarchy

### Page Pattern

**Pattern Name:** Portfolio Grid

- **Conversion Strategy:** Visuals first. Filter by category. Fast loading essential.
- **CTA Placement:** Project Card Hover + Footer Contact
- **Section Order:** Hero (Name/Role) > Project Grid (Masonry) > About/Philosophy > Contact

---

## Motion

**Stagger List** (Standard) — Trigger: load or scroll | Duration: 300-450ms | Easing: `back.out(1.4)`

```js
gsap.from('.grid-item', {
  opacity: 0,
  scale: 0.92,
  y: 16,
  duration: 0.4,
  stagger: { each: 0.06, from: 'start', grid: 'auto' },
  ease: 'back.out(1.4)',
})
```

**Framework notes:** grid: 'auto' lets GSAP infer rows/columns from a CSS grid layout for a natural wave stagger; Use matchMedia('(prefers-reduced-motion: reduce)') to skip non-essential motion and render the final state immediately

- ✅ Combine with from: 'center' for a bento-grid layout to draw the eye inward first
- ❌ Don't use back.out on dense data tables; the overshoot reads as sloppy on informational UI
- ⚡ Group DOM writes; avoid interleaving layout reads (getBoundingClientRect) between staggered tweens

---

## Anti-Patterns (Do NOT Use)

- ❌ Low hierarchy
- ❌ no publication filtering
- ❌ cluttered visuals

### Additional Forbidden Patterns

- ❌ **Emojis as icons** — Use SVG icons (Heroicons, Lucide, Simple Icons)
- ❌ **Missing cursor:pointer** — All clickable elements must have cursor:pointer
- ❌ **Layout-shifting hovers** — Avoid scale transforms that shift layout
- ❌ **Low contrast text** — Maintain 4.5:1 minimum contrast ratio
- ❌ **Instant state changes** — Always use transitions (150-300ms)
- ❌ **Invisible focus states** — Focus states must be visible for a11y

---

## Pre-Delivery Checklist

Before delivering any UI code, verify:

- [ ] No emojis used as icons (use SVG instead)
- [ ] All icons from consistent icon set (Heroicons/Lucide)
- [ ] `cursor-pointer` on all clickable elements
- [ ] Hover states with smooth transitions (150-300ms)
- [ ] Light mode: text contrast 4.5:1 minimum
- [ ] Focus states visible for keyboard navigation
- [ ] `prefers-reduced-motion` respected
- [ ] Responsive: 375px, 768px, 1024px, 1440px
- [ ] No content hidden behind fixed navbars
- [ ] No horizontal scroll on mobile

## OpenLIMS 前端与外观预设（2026-10-07，用户批准）

- 本节与页面覆盖优先于上面的历史配色/字型表：采用 OpenLIMS 现有组件与 CSS，品牌资产继续为 YES Lab；不通过保留旧 CSS 阻断新前端。
- config/appearance.json 为运行中的配色、字体及圆角配置，默认 general（蓝灰/系统字体/12px）；academic（靛蓝纸面/衬线首页标题/6px）、engineering（青蓝网格/等宽首页标题/4px）、life-science（森林绿/系统字体/20px）为构建选项。现有字型覆盖范围沿用源代码，登录页标题仍为系统字型。
- Vite 注入明暗语义变量与 data-ui-preset，presets.css 管理形状/首页标题/工程网格；页脚指向 YES Lab 仓库。导航、路由、权限与任务业务保持现有实现，本地 TaskSubmissionProgress、补发折叠和审核排序保留。
- 保留源码配置的 YES Lab 名称/Logo、首页既有文案与 API 存储内容；浏览器主题/认证/变更事件继续使用 yeslab 键。部署镜像和数据路径不更名。

## OpenLIMS 最新页面同步（2026-10-07，用户批准）

- 固定 a2df1b6 上游页面、语义 tokens、统一 motion、持久 AdminLayout、抽屉/确认/Toast/SaveBar、工作总览与讨论搜索/本地草稿。默认 general 与四预设、YES Lab 身份和原内容保持；新版页面布局覆盖旧布局描述，业务规则不变。
- ui-ux-pro-max 查询 `drawer focus trap escape unsaved form reduced motion --domain ux`：采纳 Reduced Motion、Excessive Motion 与表单反馈规则。仅在 transform/opacity 上做受控过渡；系统 reduce 时关闭非必要动效；抽屉/对话框验证 Escape、焦点约束/返回与未保存保护。
- 全部任务页面继续共用 TaskSubmissionProgress 的 12px 条高、灰色未交齐/绿色交齐与无子任务 0/100 边界。最新子任务工作区同样使用它；审核列表新增状态筛选但保持原优先级和组内提交率降序，补发折叠和选择不丢失。
- 本轮以当前源码真机截图核对 1440/1024/768/375 明暗、公开/讨论/后台/任务页面，以及其他三预设的桌面手机明暗；核心控件按 44px、可见焦点、正文 AA、无横向溢出验收。

- 同步验收适配：浅色首页渐变标题使用较深色段，保持暗色渐变；导航/确认/抽屉/筛选/关闭等控件至少 44px。YES Lab 原长标题在手机使用 36px 起的字号，hero 网格采用 minmax(0, 1fr)，模型标题允许换行，避免根节点 overflow: clip 掩盖内部文案与控件裁切。

## 首页与成员顶栏细节（2026-10-07，用户批准）

- 姓名采用头像底图反色和独立细轮廓，卡片文字区加强深色渐变；白色头像最不利背景计算对比度约 6.25:1。
- 首页使用淡蓝紫鼠标光晕，仅桌面精细鼠标且无减少动态偏好时启用；只更新 transform/opacity，离开、失焦、隐藏及卸载时清理，手机停用。
- 光晕按用户追加需求改为弹簧滞后/超调回弹与速度拉伸（最多 12%），移动时循环变色，静止停止更新；该装饰层额外更新 hue-rotate filter，其余内容颜色不变。`spring animation excessive motion reduced motion --domain ux` 查询采纳减少动态与节制动效规则。
- 成员顶栏按实际容器、入口、账号和后台菜单宽度显示入口；全部可放下时没有“更多”，否则仅溢出项折叠，菜单高度有界并可滚动。保留各入口权限和键盘行为。
- ui-ux-pro-max 查询 `text image overlay contrast --domain ux`，采纳 4.5:1 对比度规则；Chrome 1440/1024/768/375 明暗首页及 1920 至 375 成员顶栏验收，见 `.codex-run/ui-review/home-polish-*`。
