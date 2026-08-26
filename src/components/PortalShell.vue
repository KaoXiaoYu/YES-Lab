<script setup>
import { ClipboardList, FolderKanban, Home, LayoutTemplate, LogOut, Medal, Newspaper, ShieldCheck, UserRound, UsersRound } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { authState, logout } from '../services/authApi'

defineProps({
  eyebrow: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, default: '' },
})

const router = useRouter()
const roleLabels = {
  TEACHER: '教师 · 系统管理员',
  CORE_STUDENT: '核心学生 · 系统管理员',
  MEMBER: '正式成员',
  VISITOR: '报名访客',
}
const accountLabel = computed(() => roleLabels[authState.account?.role] || authState.account?.role)
const accountName = computed(() => authState.account?.displayName || authState.account?.username || '')

async function signOut() {
  await logout()
  router.push('/')
}
</script>

<template>
  <div class="portal-page">
    <header class="portal-topbar">
      <RouterLink class="portal-brand" to="/" aria-label="返回 YES Lab 公开首页">
        <img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" />
        <span>MEMBER SYSTEM</span>
      </RouterLink>
      <nav aria-label="成员系统导航">
        <RouterLink to="/"><Home :size="17" aria-hidden="true" />公开首页</RouterLink>
        <RouterLink v-if="authState.account?.role !== 'VISITOR'" to="/profile"><UserRound :size="17" aria-hidden="true" />个人主页</RouterLink>
        <RouterLink v-if="authState.account?.role !== 'VISITOR'" to="/projects"><FolderKanban :size="17" aria-hidden="true" />项目团队</RouterLink>
        <RouterLink v-if="authState.account?.role !== 'VISITOR'" to="/competitions"><Medal :size="17" aria-hidden="true" />竞赛成果</RouterLink>
        <RouterLink v-if="authState.account?.role === 'VISITOR'" to="/application"><ClipboardList :size="17" aria-hidden="true" />我的报名</RouterLink>
        <RouterLink v-if="authState.account?.systemAdmin" to="/admin/members"><UsersRound :size="17" aria-hidden="true" />成员管理</RouterLink>
        <RouterLink v-if="authState.account?.systemAdmin" to="/admin/recruitment"><ShieldCheck :size="17" aria-hidden="true" />招新管理</RouterLink>
        <RouterLink v-if="authState.account?.systemAdmin" to="/admin/achievements"><Newspaper :size="17" aria-hidden="true" />成果管理</RouterLink>
        <RouterLink v-if="authState.account?.systemAdmin" to="/admin/homepage"><LayoutTemplate :size="17" aria-hidden="true" />主页编辑</RouterLink>
      </nav>
      <div class="portal-account">
        <RouterLink v-if="authState.account?.role !== 'VISITOR'" class="portal-account-avatar" to="/profile" aria-label="打开个人主页">
          <img v-if="authState.account?.avatarUrl" :src="authState.account.avatarUrl" alt="" />
          <b v-else>{{ accountName.slice(0, 1) }}</b>
        </RouterLink>
        <span><strong>{{ accountName }}</strong><small>{{ accountLabel }}</small></span>
        <button type="button" aria-label="退出登录" @click="signOut"><LogOut :size="18" aria-hidden="true" /></button>
      </div>
    </header>

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
