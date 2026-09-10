<script setup>
import ThemeToggle from '../components/ThemeToggle.vue'
import { ArrowLeft, ArrowRight, MessageSquareText, Menu, Reply, X } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DiscussionCollapsibleContent from '../components/DiscussionCollapsibleContent.vue'
import MemberProfileDisplay from '../components/MemberProfileDisplay.vue'
import { authState } from '../services/authApi'
import { fetchPublicMemberDiscussions, fetchPublicMemberProfile } from '../services/publicApi'

const route = useRoute()
const profile = ref(null)
const discussionActivity = ref([])
const loading = ref(true)
const errorMessage = ref('')
const menuOpen = ref(false)
const accountDestination = computed(() => authState.account?.role === 'VISITOR' ? '/application' : '/profile')
const accountName = computed(() => authState.account?.displayName || authState.account?.username || '')

onMounted(async () => {
  try {
    profile.value = await fetchPublicMemberProfile(route.params.profileId)
    if (!profile.value) errorMessage.value = '该成员主页不存在、尚未公开，或后端服务未启动。'
    else discussionActivity.value = await fetchPublicMemberDiscussions(route.params.profileId).catch(() => [])
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

function contentNumber(value) { return `#D${String(value).padStart(6, '0')}` }
function formatTime(value) { return new Date(value).toLocaleString('zh-CN') }
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
      <div class="header-actions"><ThemeToggle /><button class="menu-button" :aria-expanded="menuOpen" :aria-label="menuOpen ? '关闭菜单' : '打开菜单'" @click="menuOpen = !menuOpen">
        <X v-if="menuOpen" :size="22" aria-hidden="true" /><Menu v-else :size="22" aria-hidden="true" />
      </button></div>
    </header>

    <section id="member-profile" class="public-member-main">
      <header class="public-member-heading"><p>YES LAB / PEOPLE</p><h1>{{ profile?.name || '成员主页' }}</h1><span>成员公开档案 · 项目、能力与成长记录</span></header>
      <div v-if="loading" class="portal-state">正在读取公开成员资料…</div>
      <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}<RouterLink to="/">返回首页</RouterLink></div>
      <template v-else-if="profile">
        <MemberProfileDisplay :profile="profile" />
        <section class="profile-discussion-card" aria-labelledby="profile-discussion-title">
          <header><div><p>DISCUSSION ACTIVITY</p><h2 id="profile-discussion-title">讨论板内容</h2></div><span>{{ discussionActivity.length }} 条公开内容</span></header>
          <div v-if="discussionActivity.length" class="profile-discussion-list">
            <article v-for="item in discussionActivity" :key="`${item.type}-${item.contentNumber}`">
              <div class="profile-discussion-meta"><span><MessageSquareText v-if="item.type === 'POST'" :size="16" aria-hidden="true" /><Reply v-else :size="16" aria-hidden="true" />{{ item.type === 'POST' ? '发布讨论' : '参与回复' }}</span><b>{{ contentNumber(item.contentNumber) }}</b><time :datetime="item.createdAt">{{ formatTime(item.createdAt) }}</time></div>
              <h3>{{ item.type === 'POST' ? item.postTitle : `回复《${item.postTitle}》` }}</h3>
              <DiscussionCollapsibleContent :html="item.content" :max-height="220" label="讨论内容" compact />
              <RouterLink :to="{ path: '/discussions', hash: `#post-${item.postId}` }">查看所在讨论<ArrowRight :size="15" aria-hidden="true" /></RouterLink>
            </article>
          </div>
          <p v-else class="profile-discussion-empty">这位成员还没有发布讨论或回复。</p>
        </section>
      </template>
    </section>
  </main>
</template>
