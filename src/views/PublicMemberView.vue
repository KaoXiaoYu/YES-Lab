<script setup>
import { ArrowLeft, Menu, X } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import MemberProfileDisplay from '../components/MemberProfileDisplay.vue'
import { authState } from '../services/authApi'
import { fetchPublicMemberProfile } from '../services/publicApi'

const route = useRoute()
const profile = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const menuOpen = ref(false)
const accountDestination = computed(() => authState.account?.role === 'VISITOR' ? '/application' : '/profile')
const accountName = computed(() => authState.account?.displayName || authState.account?.username || '')

onMounted(async () => {
  try {
    profile.value = await fetchPublicMemberProfile(route.params.profileId)
    if (!profile.value) errorMessage.value = '该成员主页不存在、尚未公开，或后端服务未启动。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="site-shell public-member-page">
    <a class="skip-link" href="#member-profile">跳到成员资料</a>
    <header class="site-header">
      <RouterLink class="brand" to="/" aria-label="返回 YES Lab 首页">
        <img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" />
        <span>MEMBER PROFILE</span>
      </RouterLink>
      <nav :class="['top-nav', { open: menuOpen }]" aria-label="成员主页导航">
        <RouterLink class="public-back-link" to="/"><ArrowLeft :size="17" aria-hidden="true" />返回公开首页</RouterLink>
        <RouterLink v-if="authState.account" class="public-account-chip" :to="accountDestination">
          <span class="public-account-avatar"><img v-if="authState.account.avatarUrl" :src="authState.account.avatarUrl" alt="" /><b v-else>{{ accountName.slice(0, 1) }}</b></span>
          <strong>{{ accountName }}</strong>
        </RouterLink>
        <span v-else class="auth-entry"><RouterLink to="/login">登录</RouterLink><i>/</i><RouterLink to="/register">注册</RouterLink></span>
      </nav>
      <button class="menu-button" :aria-expanded="menuOpen" :aria-label="menuOpen ? '关闭菜单' : '打开菜单'" @click="menuOpen = !menuOpen">
        <X v-if="menuOpen" :size="22" aria-hidden="true" /><Menu v-else :size="22" aria-hidden="true" />
      </button>
    </header>

    <section id="member-profile" class="public-member-main">
      <header class="public-member-heading"><p>YES LAB / PEOPLE</p><h1>{{ profile?.name || '成员主页' }}</h1><span>成员公开档案 · 项目、能力与成长记录</span></header>
      <div v-if="loading" class="portal-state">正在读取公开成员资料…</div>
      <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}<RouterLink to="/">返回首页</RouterLink></div>
      <MemberProfileDisplay v-else-if="profile" :profile="profile" />
    </section>
  </main>
</template>
