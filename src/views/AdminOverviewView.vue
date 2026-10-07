<script setup>
import { animate } from 'motion'
import {
  ArrowRight,
  BadgeCheck,
  ClipboardCheck,
  Gift,
  RefreshCw,
  ShieldCheck,
  Sprout,
  UserRoundSearch,
  UsersRound,
} from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { getAdminOverview } from '../services/authApi'

const icons = {
  RECRUITMENT_SIGNUP: UserRoundSearch,
  RECRUITMENT_SCREENING: ShieldCheck,
  RECRUITMENT_INTERVIEW: UsersRound,
  ONBOARDING_REVIEW: Sprout,
  TASK_REVIEW: ClipboardCheck,
  BOUNTY_PRIZE: Gift,
  ACHIEVEMENT_REVIEW: BadgeCheck,
}

const overview = ref(null)
const loading = ref(true)
const refreshing = ref(false)
const errorMessage = ref('')
const countEls = ref([])

const pending = computed(() => (overview.value?.items || []).filter((item) => item.count > 0))
const clear = computed(() => (overview.value?.items || []).filter((item) => item.count === 0))
const updatedAt = computed(() =>
  overview.value
    ? new Date(overview.value.generatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : '',
)

async function load() {
  refreshing.value = !loading.value
  errorMessage.value = ''
  try {
    overview.value = await getAdminOverview()
    loading.value = false
    await nextTick()
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      countEls.value.forEach((element, index) => {
        const target = pending.value[index]?.count || 0
        if (!element || !target) return
        animate(0, target, {
          duration: 0.8,
          delay: index * 0.05,
          ease: [0.22, 1, 0.36, 1],
          onUpdate: (latest) => (element.textContent = String(Math.round(latest))),
        })
      })
    }
  } catch (error) {
    errorMessage.value = error.message
    loading.value = false
  } finally {
    refreshing.value = false
  }
}

function onVisible() {
  if (document.visibilityState === 'visible' && !loading.value) load()
}

onMounted(() => {
  load()
  document.addEventListener('visibilitychange', onVisible)
})
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisible))
</script>

<template>
  <PortalShell title="待处理总览" description="各模块等待处理的事项，点开直达对应页面。">
    <template #actions>
      <button class="ui-btn ui-btn--secondary" type="button" :disabled="refreshing || loading" @click="load">
        <RefreshCw :size="16" :class="{ 'is-spinning': refreshing }" aria-hidden="true" />{{
          refreshing ? '刷新中…' : '刷新'
        }}
      </button>
    </template>

    <LoadingSkeleton v-if="loading" variant="cards" :rows="4" label="正在汇总待处理事项" />
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <template v-else-if="overview">
      <section class="overview-hero" :data-clear="overview.totalPending === 0" aria-live="polite">
        <strong>{{ overview.totalPending }}</strong>
        <span>{{ overview.totalPending ? '件事等待处理' : '暂时没有待处理事项' }}</span>
        <small>更新于 {{ updatedAt }}</small>
      </section>

      <section v-if="pending.length" class="overview-grid" aria-label="待处理事项">
        <RouterLink v-for="(item, index) in pending" :key="item.key" class="overview-card" :to="item.href">
          <span class="overview-icon"
            ><component :is="icons[item.key] || ClipboardCheck" :size="20" aria-hidden="true"
          /></span>
          <strong :ref="(element) => (countEls[index] = element)">{{ item.count }}</strong>
          <b>{{ item.label }}</b>
          <small>{{ item.hint }}</small>
          <ArrowRight class="overview-go" :size="18" aria-hidden="true" />
        </RouterLink>
      </section>

      <section v-if="clear.length" class="overview-clear" aria-label="已处理完的模块">
        <h2>已清空</h2>
        <ul>
          <li v-for="item in clear" :key="item.key">
            <RouterLink :to="item.href"><BadgeCheck :size="16" aria-hidden="true" />{{ item.label }}</RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </PortalShell>
</template>
