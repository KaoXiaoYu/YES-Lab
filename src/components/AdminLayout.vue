<script setup>
import { brand } from '../config/site'
import ThemeToggle from './ThemeToggle.vue'
import NotificationCenter from './NotificationCenter.vue'
import {
  BadgePlus,
  ChevronRight,
  ClipboardCheck,
  FolderKanban,
  Gift,
  LayoutDashboard,
  LayoutTemplate,
  ListChecks,
  LogOut,
  Medal,
  Menu,
  MessageSquareText,
  Newspaper,
  PanelLeftClose,
  PanelLeftOpen,
  Search,
  ShieldCheck,
  UserRound,
  UsersRound,
  X,
} from '@lucide/vue'
import { computed, provide, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, logout } from '../services/authApi'
import { notifyRouteLeft } from '../services/routeTransition'
import { commandShortcutLabel, openCommandPalette } from '../services/commandPalette'

const router = useRouter()
const route = useRoute()

const modules = [
  { to: '/admin', label: '待处理总览', icon: LayoutDashboard, exact: true },
  { to: '/admin/members', label: '成员管理', icon: UsersRound },
  { to: '/admin/points', label: '积分管理', icon: BadgePlus },
  { to: '/admin/recruitment', label: '招新管理', icon: ShieldCheck },
  { to: '/admin/tasks', label: '任务管理', icon: ClipboardCheck },
  { to: '/admin/bounties', label: '悬赏管理', icon: Gift },
  { to: '/admin/achievements', label: '成果管理', icon: Newspaper },
  { to: '/admin/homepage', label: '主页编辑', icon: LayoutTemplate },
]
const shortcuts = [
  { to: '/profile', label: '个人主页', icon: UserRound },
  { to: '/projects', label: '项目团队', icon: FolderKanban },
  { to: '/tasks', label: '我的任务', icon: ListChecks },
  { to: '/bounties', label: '悬赏榜', icon: Gift },
  { to: '/competitions', label: '比赛管理', icon: Medal },
  { to: '/discussions', label: '讨论板', icon: MessageSquareText },
]
const roleLabels = {
  TEACHER: '教师 · 系统管理员',
  CORE_STUDENT: '核心学生 · 系统管理员',
}

const page = reactive({ title: '' })
provide('adminLayout', page)

const currentModule = computed(() =>
  modules.find((item) => route.path === item.to || (!item.exact && route.path.startsWith(`${item.to}/`))),
)
const pageTitle = computed(() => page.title || currentModule.value?.label || '管理工作台')
const accountName = computed(() => authState.account?.displayName || authState.account?.username || '')
const accountLabel = computed(() => roleLabels[authState.account?.role] || authState.account?.role)

const sidebarOpen = ref(false)
const rail = ref(readRail())

function readRail() {
  try {
    return localStorage.getItem('yeslab-admin-rail') === '1'
  } catch {
    return false
  }
}

function toggleRail() {
  rail.value = !rail.value
  try {
    localStorage.setItem('yeslab-admin-rail', rail.value ? '1' : '0')
  } catch {
    /* The preference is optional. */
  }
}

watch(
  () => route.fullPath,
  () => {
    sidebarOpen.value = false
  },
)

async function signOut() {
  await logout()
  router.push('/')
}
</script>

<template>
  <div class="portal-page portal-page--admin" :class="{ 'is-rail': rail }">
    <Transition name="fade">
      <button
        v-if="sidebarOpen"
        class="admin-shell-scrim"
        type="button"
        aria-label="关闭后台导航"
        @click="sidebarOpen = false"
      ></button>
    </Transition>

    <aside
      id="admin-shell-navigation"
      class="admin-shell-sidebar flex flex-col"
      :class="{ open: sidebarOpen }"
      aria-label="后台管理导航"
    >
      <div class="admin-shell-sidebar-head">
        <RouterLink class="admin-shell-brand flex items-center" to="/" :aria-label="`返回 ${brand.name} 公开首页`">
          <img :src="brand.logoOnDark" :alt="brand.name" width="900" height="300" />
          <span
            ><b>{{ brand.name }}</b
            ><small>管理工作台</small></span
          >
        </RouterLink>
        <button type="button" aria-label="关闭后台导航" @click="sidebarOpen = false">
          <X :size="18" aria-hidden="true" />
        </button>
      </div>

      <div class="admin-shell-scroll">
        <div class="admin-shell-nav-label">后台管理</div>
        <nav class="admin-shell-nav grid" aria-label="后台管理模块">
          <RouterLink
            v-for="item in modules"
            :key="item.to"
            :to="item.to"
            :class="{ 'is-current': currentModule === item }"
            :aria-current="currentModule === item ? 'page' : undefined"
            :title="rail ? item.label : undefined"
            ><component :is="item.icon" :size="18" aria-hidden="true" /><span>{{ item.label }}</span></RouterLink
          >
        </nav>

        <div class="admin-shell-nav-label secondary">成员系统</div>
        <nav class="admin-shell-nav admin-shell-nav--secondary grid" aria-label="成员系统快捷入口">
          <RouterLink v-for="item in shortcuts" :key="item.to" :to="item.to" :title="rail ? item.label : undefined"
            ><component :is="item.icon" :size="18" aria-hidden="true" /><span>{{ item.label }}</span></RouterLink
          >
        </nav>
      </div>

      <footer class="admin-shell-user">
        <RouterLink class="admin-shell-user-avatar" to="/profile" aria-label="打开个人主页">
          <img v-if="authState.account?.avatarUrl" :src="authState.account.avatarUrl" alt="" />
          <b v-else>{{ accountName.slice(0, 1) }}</b>
        </RouterLink>
        <span
          ><strong>{{ accountName }}</strong
          ><small>{{ accountLabel }}</small></span
        >
        <button type="button" aria-label="退出登录" title="退出登录" @click="signOut">
          <LogOut :size="18" aria-hidden="true" />
        </button>
      </footer>
    </aside>

    <header class="admin-shell-topbar flex items-center justify-between">
      <div class="flex items-center">
        <button
          class="admin-sidebar-trigger"
          type="button"
          :aria-expanded="sidebarOpen"
          aria-controls="admin-shell-navigation"
          aria-label="打开后台导航"
          @click="sidebarOpen = true"
        >
          <Menu :size="20" aria-hidden="true" />
        </button>
        <button
          class="admin-rail-toggle"
          type="button"
          :aria-pressed="rail"
          :aria-label="rail ? '展开侧边导航' : '收起侧边导航'"
          :title="rail ? '展开侧边导航' : '收起侧边导航'"
          @click="toggleRail"
        >
          <PanelLeftOpen v-if="rail" :size="18" aria-hidden="true" />
          <PanelLeftClose v-else :size="18" aria-hidden="true" />
        </button>
        <nav class="admin-shell-context" aria-label="当前位置">
          <RouterLink v-if="currentModule && pageTitle !== currentModule.label" :to="currentModule.to">{{
            currentModule.label
          }}</RouterLink>
          <ChevronRight v-if="currentModule && pageTitle !== currentModule.label" :size="14" aria-hidden="true" />
          <strong aria-current="page">{{ pageTitle }}</strong>
        </nav>
      </div>
      <div class="admin-shell-actions flex items-center">
        <button
          class="portal-search-trigger"
          type="button"
          aria-label="搜索与跳转"
          aria-keyshortcuts="Meta+K Control+K"
          @click="openCommandPalette"
        >
          <Search :size="16" aria-hidden="true" /><span>搜索</span><kbd>{{ commandShortcutLabel }}</kbd>
        </button>
        <ThemeToggle />
        <NotificationCenter v-if="authState.account" />
        <RouterLink class="admin-shell-top-avatar" to="/profile" aria-label="打开个人主页">
          <img v-if="authState.account?.avatarUrl" :src="authState.account.avatarUrl" alt="" />
          <b v-else>{{ accountName.slice(0, 1) }}</b>
        </RouterLink>
      </div>
    </header>

    <RouterView v-slot="{ Component }">
      <Transition name="route" mode="out-in" @after-leave="notifyRouteLeft">
        <component :is="Component" />
      </Transition>
    </RouterView>
    <!-- Drawers render here so they keep the admin-scoped form styles. -->
    <div id="admin-layer"></div>
  </div>
</template>
