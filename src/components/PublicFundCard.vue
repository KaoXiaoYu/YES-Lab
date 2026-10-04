<script setup>
import { Wallet } from '@lucide/vue'
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { authState, getPublicFundSummary } from '../services/authApi'
import { labTime } from '../services/fundFormat'
import FundSummary from './FundSummary.vue'
const summary = ref(null)
const error = ref('')
const loading = ref(true)
const hint = ref(false)
const member = computed(() => ['TEACHER', 'CORE_STUDENT', 'MEMBER'].includes(authState.account?.role))
let version = 0
async function load() {
  const ticket = ++version
  loading.value = true
  error.value = ''
  try {
    const result = await getPublicFundSummary()
    if (ticket === version) summary.value = result
  } catch (e) {
    if (ticket === version) error.value = e.message
  } finally {
    if (ticket === version) loading.value = false
  }
}
function resume() {
  if (document.visibilityState === 'visible') load()
}
function storage(event) {
  if (event.key === 'yeslab-fund-changed') load()
}
onMounted(() => {
  load()
  window.addEventListener('yeslab:fund-changed', load)
  window.addEventListener('focus', resume)
  window.addEventListener('storage', storage)
  document.addEventListener('visibilitychange', resume)
})
onBeforeUnmount(() => {
  ++version
  window.removeEventListener('yeslab:fund-changed', load)
  window.removeEventListener('focus', resume)
  window.removeEventListener('storage', storage)
  document.removeEventListener('visibilitychange', resume)
})
</script>
<template>
  <article class="lab-info-card public-fund-card">
    <header>
      <div>
        <p>LAB FUND</p>
        <h2><Wallet :size="22" aria-hidden="true" />实验室基金</h2>
      </div>
      <span>人民币</span>
    </header>
    <p v-if="loading && !summary" role="status">正在读取基金汇总…</p>
    <p v-else-if="error" role="alert">{{ error }} <button type="button" @click="load">重试</button></p>
    <template v-else
      ><FundSummary :summary="summary" />
      <p class="ledger-muted">
        {{ summary?.initialized ? `汇总更新于 ${labTime(summary.updatedAt)}` : '基金尚未登记期初余额。' }}
      </p></template
    ><RouterLink v-if="member" class="disclosure-button" to="/fund">查看基金明细</RouterLink
    ><button v-else type="button" class="disclosure-button" @click="hint = !hint">查看基金明细</button>
    <p v-if="hint && !member" role="status">
      基金明细仅对实验室成员开放。<RouterLink v-if="!authState.account" to="/login?redirect=/fund">成员登录</RouterLink>
    </p>
  </article>
</template>
