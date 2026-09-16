<script setup>
import ThemeToggle from './ThemeToggle.vue'
import NotificationCenter from './NotificationCenter.vue'
import {
  BadgePlus,
  ClipboardList,
  FolderKanban,
  Home,
  LayoutDashboard,
  LayoutTemplate,
  LogOut,
  Menu,
  Medal,
  MessageSquareText,
  Newspaper,
  ShieldCheck,
  Trophy,
  UserRound,
  UsersRound,
  X,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, logout } from '../services/authApi'

defineProps({
  eyebrow: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, default: '' },
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
const adminSectionActive = computed(() => route.path.startsWith('/admin/'))
const isAdminPage = computed(() => route.path.startsWith('/admin/'))
const adminSidebarOpen = ref(false)

watch(
  () => route.fullPath,
  () => {
    adminSidebarOpen.value = false
  },
)

async function signOut() {
  await logout()
  router.push('/')
}
</script>

<template>
  <div class="portal-page" :class="{ 'portal-page--admin': isAdminPage }">
    <header v-if="!isAdminPage" class="portal-topbar">
      <RouterLink class="portal-brand" to="/" aria-label="返回 YES Lab 公开首页">
        <img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" />
        <span>MEMBER SYSTEM</span>
      </RouterLink>
      <nav aria-label="成员系统导航">
        <RouterLink to="/"><Home :size="17" aria-hidden="true" />公开首页</RouterLink>
        <RouterLink v-if="authState.account && authState.account.role !== 'VISITOR'" to="/profile"
          ><UserRound :size="17" aria-hidden="true" />个人主页</RouterLink
        >
        <RouterLink v-if="authState.account && authState.account.role !== 'VISITOR'" to="/points"
          ><Trophy :size="17" aria-hidden="true" />积分榜</RouterLink
        >
        <RouterLink v-if="authState.account && authState.account.role !== 'VISITOR'" to="/projects"
          ><FolderKanban :size="17" aria-hidden="true" />项目团队</RouterLink
        >
        <RouterLink v-if="authState.account && authState.account.role !== 'VISITOR'" to="/competitions"
          ><Medal :size="17" aria-hidden="true" />竞赛成果</RouterLink
        >
        <RouterLink to="/discussions"><MessageSquareText :size="17" aria-hidden="true" />讨论板</RouterLink>
        <RouterLink v-if="authState.account?.role === 'VISITOR'" to="/application"
          ><ClipboardList :size="17" aria-hidden="true" />我的报名</RouterLink
        >
        <RouterLink v-if="authState.account?.systemAdmin" class="portal-admin-direct" to="/admin/members"
          ><UsersRound :size="17" aria-hidden="true" />成员管理</RouterLink
        >
        <RouterLink v-if="authState.account?.systemAdmin" class="portal-admin-direct" to="/admin/points"
          ><BadgePlus :size="17" aria-hidden="true" />积分管理</RouterLink
        >
        <RouterLink v-if="authState.account?.systemAdmin" class="portal-admin-direct" to="/admin/recruitment"
          ><ShieldCheck :size="17" aria-hidden="true" />招新管理</RouterLink
        >
        <RouterLink v-if="authState.account?.systemAdmin" class="portal-admin-direct" to="/admin/achievements"
          ><Newspaper :size="17" aria-hidden="true" />成果管理</RouterLink
        >
        <RouterLink v-if="authState.account?.systemAdmin" class="portal-admin-direct" to="/admin/homepage"
          ><LayoutTemplate :size="17" aria-hidden="true" />主页编辑</RouterLink
        >
        <details
          v-if="authState.account?.systemAdmin"
          class="portal-admin-menu"
          @click="(event) => event.target.closest('a') && event.currentTarget.removeAttribute('open')"
        >
          <summary :class="{ active: adminSectionActive }">
            <LayoutDashboard :size="17" aria-hidden="true" />后台管理
          </summary>
          <div>
            <RouterLink to="/admin/members"><UsersRound :size="17" aria-hidden="true" />成员管理</RouterLink>
            <RouterLink to="/admin/points"><BadgePlus :size="17" aria-hidden="true" />积分管理</RouterLink>
            <RouterLink to="/admin/recruitment"><ShieldCheck :size="17" aria-hidden="true" />招新管理</RouterLink>
            <RouterLink to="/admin/achievements"><Newspaper :size="17" aria-hidden="true" />成果管理</RouterLink>
            <RouterLink to="/admin/homepage"><LayoutTemplate :size="17" aria-hidden="true" />主页编辑</RouterLink>
          </div>
        </details>
      </nav>
      <div class="portal-account">
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

    <template v-else>
      <button
        v-if="adminSidebarOpen"
        class="admin-shell-scrim"
        type="button"
        aria-label="关闭后台导航"
        @click="adminSidebarOpen = false"
      ></button>

      <aside
        id="admin-shell-navigation"
        class="admin-shell-sidebar flex flex-col"
        :class="{ open: adminSidebarOpen }"
        aria-label="后台管理导航"
      >
        <div class="admin-shell-sidebar-head">
          <RouterLink class="admin-shell-brand flex items-center" to="/" aria-label="返回 YES Lab 公开首页">
            <img src="/logo-variants/yes-lab-white.png" alt="YES Lab" width="900" height="506" />
            <span><b>YES Lab</b><small>管理工作台</small></span>
          </RouterLink>
          <button type="button" aria-label="关闭后台导航" @click="adminSidebarOpen = false">
            <X :size="18" aria-hidden="true" />
          </button>
        </div>

        <div class="admin-shell-nav-label">后台管理</div>
        <nav class="admin-shell-nav grid" aria-label="后台管理模块">
          <RouterLink to="/admin/members"><UsersRound :size="18" aria-hidden="true" />成员管理</RouterLink>
          <RouterLink to="/admin/points"><BadgePlus :size="18" aria-hidden="true" />积分管理</RouterLink>
          <RouterLink to="/admin/recruitment"><ShieldCheck :size="18" aria-hidden="true" />招新管理</RouterLink>
          <RouterLink to="/admin/achievements"><Newspaper :size="18" aria-hidden="true" />成果管理</RouterLink>
          <RouterLink to="/admin/homepage"><LayoutTemplate :size="18" aria-hidden="true" />主页编辑</RouterLink>
        </nav>

        <div class="admin-shell-nav-label secondary">成员系统</div>
        <nav class="admin-shell-nav admin-shell-nav--secondary grid" aria-label="成员系统快捷入口">
          <RouterLink to="/profile"><UserRound :size="18" aria-hidden="true" />个人主页</RouterLink>
          <RouterLink to="/projects"><FolderKanban :size="18" aria-hidden="true" />项目团队</RouterLink>
          <RouterLink to="/competitions"><Medal :size="18" aria-hidden="true" />竞赛成果</RouterLink>
          <RouterLink to="/discussions"><MessageSquareText :size="18" aria-hidden="true" />讨论板</RouterLink>
        </nav>

        <footer class="admin-shell-user">
          <RouterLink class="admin-shell-user-avatar" to="/profile" aria-label="打开个人主页">
            <img v-if="authState.account?.avatarUrl" :src="authState.account.avatarUrl" alt="" />
            <b v-else>{{ accountName.slice(0, 1) }}</b>
          </RouterLink>
          <span
            ><strong>{{ accountName }}</strong
            ><small>{{ accountLabel }}</small></span
          >
          <button type="button" aria-label="退出登录" @click="signOut">
            <LogOut :size="18" aria-hidden="true" />
          </button>
        </footer>
      </aside>

      <header class="admin-shell-topbar flex items-center justify-between">
        <div class="flex items-center">
          <button
            class="admin-sidebar-trigger"
            type="button"
            :aria-expanded="adminSidebarOpen"
            aria-controls="admin-shell-navigation"
            aria-label="打开后台导航"
            @click="adminSidebarOpen = true"
          >
            <Menu :size="20" aria-hidden="true" />
          </button>
          <div class="admin-shell-context">
            <small>YES LAB / ADMIN</small>
            <strong>{{ title }}</strong>
          </div>
        </div>
        <div class="admin-shell-actions flex items-center">
          <ThemeToggle />
          <NotificationCenter v-if="authState.account" />
          <RouterLink class="admin-shell-top-avatar" to="/profile" aria-label="打开个人主页">
            <img v-if="authState.account?.avatarUrl" :src="authState.account.avatarUrl" alt="" />
            <b v-else>{{ accountName.slice(0, 1) }}</b>
          </RouterLink>
        </div>
      </header>
    </template>

    <main class="portal-main">
      <header class="portal-heading">
        <p>{{ eyebrow }}</p>
        <h1>{{ title }}</h1>
        <span>{{ description }}</span>
      </header>
      <slot />
    </main>
  </div>
</template>
