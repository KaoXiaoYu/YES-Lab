# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/openlims/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** OpenLIMS
**Updated:** 2026-10-04 · 通用开源适配
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
| Accent/CTA       | `#1D4ED8` | `--color-accent`           |
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

**Color Notes:** Default general preset uses blue-gray; other presets share semantic tokens.

### Typography

字体来自 `config/appearance.json`，默认系统无衬线；学术预设采用本地衬线标题、工程采用等宽标题，不拉取外部字体。源码预设是颜色/字型/圆角的权威来源，本页示例色值只用于通用预设；其他预设使用同样的语义与可访问性要求。

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
  background: #1d4ed8;
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

沿用真实公开首页、成员系统和管理布局；保留所有栏目、API、路由与交互，不能用营销模板或样品管理静态预览替代。

## Motion

保留有效的轮播暂停、聚焦/悬停、减少动态与页面隐藏规则；反馈 150–300ms，非必要入场减少动态时跳过，不要求 GSAP 或跳跃动画。

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

## OpenLIMS Brand Assets

- Primary wordmark: `config/branding.json` → `/openlims-logo.svg`, transparent 900×300 canvas, blue `#2563EB`.
- White wordmark: `config/branding.json` → `/logo-variants/openlims-white.svg`, for dark fixed surfaces.
- Website icon: `/favicon.svg`; sharing cover: `/og.svg` and `/og.png` (1200×630).
- Brand images use descriptive OpenLIMS alt text; dark navigation surfaces display white wordmarks.
- Applied ui-ux-pro-max query: `image logo alt text` (`ux`), adopting Alt Text and Image Optimization guidance.

## 开源外观预设（2026-10-04）

- `general`：默认通用蓝灰、系统字体、12px 圆角；`academic`：纸面/靛蓝、衬线标题、6px；`engineering`：青蓝网格、等宽标题、4px；`life-science`：绿色、柔和表面、20px。
- 配色通过 HTML 中注入的明暗 CSS 变量生效，形状和标题通过 `src/presets.css`，其他组件不得硬编码某个预设的色彩；优先保持布局与操作一致。
- `research administration minimal` 查询命中不适用的 newsletter 布局，未采纳；重试 `dashboard clean modular` 命中 Minimalism & Swiss Style，采用清晰层级、网格、语义色和轻动效，保留真实信息结构。Vue `CSS variables theming` 无匹配，采用已有 Vue/CSS 变量实现，不宣称来自数据库命中。
- 清理旧实验室身份文案/默认奖项和伙伴声明；既有业务数据库不迁移。保留模型轮播/上传，内置模型以第三方许可保留。
