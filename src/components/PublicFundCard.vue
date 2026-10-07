<script setup>
import { Wallet } from '@lucide/vue'
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { authState, getPublicFundSummary } from '../services/authApi'
import { formatMoney, labTime } from '../services/fundFormat'
import FundSummary from './FundSummary.vue'
defineProps({ compact: Boolean })
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
  <article class="lab-info-card public-fund-card" :class="{ 'fund-strip': compact }">
    <header v-if="!compact">
      <div>
        <h2><Wallet :size="22" aria-hidden="true" />实验室基金</h2>
      </div>
      <span>人民币</span>
    </header>
    <div v-if="compact" class="fund-strip-heading">
      <Wallet :size="20" aria-hidden="true" />
      <h2>实验室基金</h2>
      <span>人民币</span>
    </div>
    <p v-if="loading && !summary" role="status">正在读取基金汇总…</p>
    <p v-else-if="error" role="alert">{{ error }} <button type="button" @click="load">重试</button></p>
    <template v-else
      ><dl v-if="compact" class="fund-strip-totals">
        <div>
          <dt>基金余额</dt>
          <dd class="strip-balance">{{ formatMoney(summary?.balance) }}</dd>
        </div>
        <div>
          <dt>累计收入</dt>
          <dd>{{ formatMoney(summary?.income) }}</dd>
        </div>
        <div>
          <dt>累计支出</dt>
          <dd>{{ formatMoney(summary?.expense) }}</dd>
        </div>
      </dl>
      <FundSummary v-else :summary="summary" />
      <p v-if="!compact" class="ledger-muted">
        {{ summary?.initialized ? `汇总更新于 ${labTime(summary.updatedAt)}` : '基金尚未登记期初余额。' }}
      </p></template
    ><RouterLink v-if="member" class="disclosure-button" to="/fund">查看基金明细</RouterLink
    ><button v-else type="button" class="disclosure-button" @click="hint = !hint">查看基金明细</button>
    <p v-if="hint && !member" role="status">
      基金明细仅对实验室成员开放。<RouterLink v-if="!authState.account" to="/login?redirect=/fund">成员登录</RouterLink>
    </p>
  </article>
</template>

<style scoped>
.public-fund-card.fund-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px 24px;
  padding: 16px 24px;
}
.fund-strip-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-primary);
}
.public-fund-card.fund-strip .fund-strip-heading h2 {
  font-size: 16px;
  line-height: 1.5;
}
.fund-strip-heading > span {
  font-size: 12px;
  color: var(--color-muted-foreground);
}
.fund-strip-totals {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 32px;
  margin: 0;
}
.fund-strip-totals > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.fund-strip-totals dt {
  font-size: 13px;
  color: var(--color-muted-foreground);
}
.fund-strip-totals dd {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.fund-strip-totals .strip-balance {
  font-size: 24px;
  color: var(--color-primary);
}
.fund-strip .disclosure-button {
  margin-top: 0;
}
.fund-strip p[role='status'],
.fund-strip p[role='alert'] {
  margin: 0;
}
.fund-strip p[role='status'] {
  flex-basis: 100%;
}
.fund-strip button,
.fund-strip a {
  min-width: 44px;
  min-height: 44px;
}
.fund-strip button:focus-visible,
.fund-strip a:focus-visible {
  outline: 3px solid var(--color-ring);
  outline-offset: 2px;
}
@media (max-width: 767px) {
  .public-fund-card.fund-strip {
    padding: 16px;
    gap: 12px;
  }
  .fund-strip-heading {
    flex: 1;
  }
  .fund-strip-totals {
    flex-basis: 100%;
    order: 2;
    gap: 8px 20px;
  }
  .fund-strip-totals > div:first-child {
    flex-basis: 100%;
  }
  .fund-strip-totals .strip-balance {
    font-size: 22px;
  }
  .fund-strip .disclosure-button {
    padding: 8px 10px;
    font-size: 13px;
  }
  .fund-strip p[role='status'] {
    order: 3;
  }
}
</style>
