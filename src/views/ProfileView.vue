<script setup>
import { onMounted, ref } from 'vue'
import MemberProfileDisplay from '../components/MemberProfileDisplay.vue'
import PortalShell from '../components/PortalShell.vue'
import { getOwnProfile } from '../services/authApi'

const profile = ref(null)
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    profile.value = await getOwnProfile()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <PortalShell eyebrow="MEMBER / PROFILE" title="个人主页" description="查看你的成员档案与公开主页；需要修改时再进入独立编辑页面。">
    <div v-if="loading" class="portal-state">正在读取成员资料…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <MemberProfileDisplay v-else-if="profile" :profile="profile" private-view editable />
  </PortalShell>
</template>
