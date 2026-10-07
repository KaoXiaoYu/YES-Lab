<script setup>
import { brand } from '../config/site'
import ThemeToggle from './ThemeToggle.vue'
import NotificationCenter from './NotificationCenter.vue'
import {
  BadgePlus,
  ClipboardCheck,
  Gift,
  ClipboardList,
  FolderKanban,
  Home,
  LayoutDashboard,
  LayoutTemplate,
  ListChecks,
  LogOut,
  Medal,
  MoreHorizontal,
  Search,
  Sun,
  MessageSquareText,
  Newspaper,
  ShieldCheck,
  Trophy,
  UserRound,
  UsersRound,
  Wallet,
} from '@lucide/vue'
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDismissibleLayer } from '../composables/useDismissibleLayer'
import { authState, logout } from '../services/authApi'
import { commandShortcutLabel, openCommandPalette } from '../services/commandPalette'

const props = defineProps({
  // Kept for compatibility; page headings no longer show decorative English labels.
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
})

// Inside AdminLayout the sidebar and top bar persist across admin routes;
// this component then only renders the page heading and content.
const adminLayout = inject('adminLayout', null)
watchEffect(() => {
  if (adminLayout) adminLayout.title = props.title
})

const router = useRouter()
const route = useRoute()
const roleLabels = {
  TEACHER: '教师 · 系统管理员',
  CORE_STUDENT: '核心学生 · 系统管理员',
  MEMBER: '正式成员',
  VISITOR: '报名访客',
}
const accountLabel = computed(() => roleLabels[authState.account?.role] || authState.account?.role)
const accountName = computed(() => authState.account?.displayName || authState.account?.username || '')
const adminSectionActive = computed(() => route.path.startsWith('/admin'))
const isMember = computed(() => Boolean(authState.account && authState.account.role !== 'VISITOR'))
const navigation = ref(null)
const navigationMeasure = ref(null)
const visibleCount = ref(0)
const navigationItems = computed(() => [
  ...(isMember.value
    ? [
        { to: '/today', label: '今日', icon: Sun },
        { to: '/tasks', label: '任务', icon: ListChecks },
        { to: '/bounties', label: '悬赏', icon: Gift },
        { to: '/projects', label: '项目', icon: FolderKanban },
      ]
    : []),
  ...(authState.account?.role === 'VISITOR' ? [{ to: '/application', label: '我的报名', icon: ClipboardList }] : []),
  { to: '/discussions', label: '讨论', icon: MessageSquareText },
  ...(isMember.value
    ? [
        { to: '/profile', label: '个人主页', icon: UserRound },
        { to: '/points', label: '积分榜', icon: Trophy },
        { to: '/fund', label: '实验室基金', icon: Wallet },
        { to: '/competitions', label: '比赛管理', icon: Medal },
      ]
    : []),
  { to: '/', label: '公开首页', icon: Home },
])
const visibleItems = computed(() => navigationItems.value.slice(0, visibleCount.value))
const overflowItems = computed(() => navigationItems.value.slice(visibleCount.value))
const moreSectionActive = computed(() =>
  overflowItems.value.some((item) => item.to !== '/' && route.path.startsWith(item.to)),
)
let navigationObserver
let navigationFrame = 0
let disposed = false

function measureNavigation() {
  navigationFrame = 0
  if (!navigation.value || !navigationMeasure.value) return
  const gap = parseFloat(getComputedStyle(navigation.value).columnGap) || 0
  const widths = [...navigationMeasure.value.querySelectorAll('[data-nav-item]')].map(
    (item) => item.getBoundingClientRect().width,
  )
  const adminWidth = adminMenu.value?.querySelector('summary')?.getBoundingClientRect().width || 0
  const available = navigation.value.clientWidth - (adminWidth ? adminWidth + gap : 0)
  const total = widths.reduce((sum, width) => sum + width, 0) + gap * Math.max(0, widths.length - 1)
  let count = widths.length
  if (total > available + 0.5) {
    const moreWidth = navigationMeasure.value.querySelector('[data-nav-more]').getBoundingClientRect().width
    let used = moreWidth
    count = 0
    for (const width of widths) {
      if (used + gap + width > available + 0.5) break
      used += gap + width
      count++
    }
  }
  if (visibleCount.value !== count) closeMoreMenu()
  visibleCount.value = count
}

function scheduleNavigationMeasure() {
  if (disposed || navigationFrame) return
  navigationFrame = requestAnimationFrame(measureNavigation)
}

onMounted(() => {
  if (!navigation.value) return
  navigationObserver = new ResizeObserver(scheduleNavigationMeasure)
  navigationObserver.observe(navigation.value)
  navigationObserver.observe(navigationMeasure.value)
  document.fonts.ready.then(scheduleNavigationMeasure)
  scheduleNavigationMeasure()
})
watch(navigationItems, async () => {
  await nextTick()
  scheduleNavigationMeasure()
})
watch(
  () => authState.account?.systemAdmin,
  async () => {
    await nextTick()
    scheduleNavigationMeasure()
  },
)
onBeforeUnmount(() => {
  disposed = true
  navigationObserver?.disconnect()
  cancelAnimationFrame(navigationFrame)
})
const adminMenu = ref(null)
const moreMenu = ref(null)

function closeAdminMenu() {
  if (adminMenu.value) adminMenu.value.open = false
}

function closeMoreMenu() {
  if (moreMenu.value) moreMenu.value.open = false
}

useDismissibleLayer(adminMenu, {
  isOpen: () => Boolean(adminMenu.value?.open),
  close: closeAdminMenu,
  focusTarget: () => adminMenu.value?.querySelector('summary'),
})

useDismissibleLayer(moreMenu, {
  isOpen: () => Boolean(moreMenu.value?.open),
  close: closeMoreMenu,
  focusTarget: () => moreMenu.value?.querySelector('summary'),
})

watch(
  () => route.fullPath,
  () => {
    closeAdminMenu()
    closeMoreMenu()
  },
)

async function signOut() {
  await logout()
  router.push('/')
}
</script>

<template>
  <main v-if="adminLayout" class="portal-main">
    <header class="portal-heading" :class="{ 'has-actions': $slots.actions }">
      <h1>{{ title }}</h1>
      <span v-if="description">{{ description }}</span>
      <div v-if="$slots.actions" class="portal-heading-actions"><slot name="actions" /></div>
    </header>
    <slot />
  </main>
  <div v-else class="portal-page">
    <header class="portal-topbar">
      <RouterLink class="portal-brand" to="/" :aria-label="`返回 ${brand.name} 公开首页`">
        <img :src="brand.logo" :alt="brand.name" width="900" height="300" />
      </RouterLink>
      <nav ref="navigation" class="portal-navigation" aria-label="成员系统导航">
        <RouterLink v-for="item in visibleItems" :key="item.to" :to="item.to">
          <component :is="item.icon" :size="17" aria-hidden="true" />{{ item.label }}
        </RouterLink>
        <span ref="navigationMeasure" class="portal-nav-measure" aria-hidden="true" inert>
          <a v-for="item in navigationItems" :key="item.to" data-nav-item>
            <component :is="item.icon" :size="17" />{{ item.label }}
          </a>
          <a data-nav-more><MoreHorizontal :size="17" />更多</a>
        </span>
        <details
          v-if="overflowItems.length"
          ref="moreMenu"
          class="portal-admin-menu portal-more-menu"
          @click="(event) => event.target.closest('a') && closeMoreMenu()"
        >
          <summary :class="{ active: moreSectionActive }"><MoreHorizontal :size="17" aria-hidden="true" />更多</summary>
          <div>
            <RouterLink v-for="item in overflowItems" :key="item.to" :to="item.to">
              <component :is="item.icon" :size="17" aria-hidden="true" />{{ item.label }}
            </RouterLink>
          </div>
        </details>
        <details
          v-if="authState.account?.systemAdmin"
          ref="adminMenu"
          class="portal-admin-menu"
          @click="(event) => event.target.closest('a') && closeAdminMenu()"
        >
          <summary :class="{ active: adminSectionActive }">
            <LayoutDashboard :size="17" aria-hidden="true" />后台管理
          </summary>
          <div>
            <RouterLink to="/admin" exact-active-class="router-link-exact-active"
              ><LayoutDashboard :size="17" aria-hidden="true" />待处理总览</RouterLink
            >
            <RouterLink to="/admin/members"><UsersRound :size="17" aria-hidden="true" />成员管理</RouterLink>
            <RouterLink to="/admin/points"><BadgePlus :size="17" aria-hidden="true" />积分管理</RouterLink>
            <RouterLink to="/admin/recruitment"><ShieldCheck :size="17" aria-hidden="true" />招新管理</RouterLink>
            <RouterLink to="/admin/tasks"><ClipboardCheck :size="17" aria-hidden="true" />任务管理</RouterLink>
            <RouterLink to="/admin/bounties"><Gift :size="17" aria-hidden="true" />悬赏管理</RouterLink>
            <RouterLink to="/admin/achievements"><Newspaper :size="17" aria-hidden="true" />成果管理</RouterLink>
            <RouterLink to="/admin/homepage"><LayoutTemplate :size="17" aria-hidden="true" />主页编辑</RouterLink>
          </div>
        </details>
      </nav>
      <div class="portal-account">
        <button
          v-if="authState.account"
          class="portal-search-trigger"
          type="button"
          aria-label="搜索与跳转"
          aria-keyshortcuts="Meta+K Control+K"
          @click="openCommandPalette"
        >
          <Search :size="16" aria-hidden="true" /><span>搜索</span><kbd>{{ commandShortcutLabel }}</kbd>
        </button>
        <ThemeToggle /><NotificationCenter v-if="authState.account" />
        <div v-if="!authState.account" class="portal-guest-actions">
          <RouterLink to="/login">登录</RouterLink><RouterLink class="register" to="/register">注册</RouterLink>
        </div>
        <RouterLink
          v-if="authState.account && authState.account.role !== 'VISITOR'"
          class="portal-account-avatar"
          to="/profile"
          aria-label="打开个人主页"
        >
          <img v-if="authState.account?.avatarUrl" :src="authState.account.avatarUrl" alt="" />
          <b v-else>{{ accountName.slice(0, 1) }}</b>
        </RouterLink>
        <span v-if="authState.account"
          ><strong>{{ accountName }}</strong
          ><small>{{ accountLabel }}</small></span
        >
        <button v-if="authState.account" type="button" aria-label="退出登录" @click="signOut">
          <LogOut :size="18" aria-hidden="true" />
        </button>
      </div>
    </header>

    <main class="portal-main">
      <header class="portal-heading" :class="{ 'has-actions': $slots.actions }">
        <h1>{{ title }}</h1>
        <span v-if="description">{{ description }}</span>
        <div v-if="$slots.actions" class="portal-heading-actions"><slot name="actions" /></div>
      </header>
      <slot />
    </main>
  </div>
</template>

<style scoped>
.portal-topbar .portal-navigation {
  flex-wrap: nowrap;
  overflow: visible;
}
.portal-nav-measure {
  position: fixed;
  top: -1000px;
  left: -10000px;
  display: flex;
  width: max-content;
  visibility: hidden;
  pointer-events: none;
}
.portal-more-menu > div {
  max-height: calc(100dvh - 160px);
  overflow-y: auto;
}
</style>
