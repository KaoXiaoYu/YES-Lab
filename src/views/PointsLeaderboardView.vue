<script setup>
import { CalendarRange, Medal, Trophy, UsersRound } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { getPointLeaderboard } from '../services/authApi'

const periods = [
  { value: 'TOTAL', label: '总榜', description: '历史净积分' },
  { value: 'DAY', label: '日榜', description: '今天' },
  { value: 'WEEK', label: '周榜', description: '本周一至周日' },
  { value: 'MONTH', label: '月榜', description: '本自然月' },
  { value: 'YEAR', label: '年榜', description: '本自然年' },
]
const route = useRoute()
const router = useRouter()
const leaderboard = ref(null)
const loading = ref(true)
const errorMessage = ref('')
let requestVersion = 0

const period = computed(() => {
  const candidate = String(route.query.period || 'TOTAL').toUpperCase()
  return periods.some((item) => item.value === candidate) ? candidate : 'TOTAL'
})
const selectedPeriod = computed(() => periods.find((item) => item.value === period.value))
const entries = computed(() => leaderboard.value?.entries || [])
const leader = computed(() => entries.value[0] || null)
const rangeLabel = computed(() => {
  if (!leaderboard.value?.startsOn) return '全部历史记录'
  if (leaderboard.value.startsOn === leaderboard.value.endsOn) return formatDate(leaderboard.value.startsOn)
  return `${formatDate(leaderboard.value.startsOn)} — ${formatDate(leaderboard.value.endsOn)}`
})

watch(period, loadLeaderboard, { immediate: true })

async function loadLeaderboard(value) {
  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getPointLeaderboard(value)
    if (version === requestVersion) leaderboard.value = data
  } catch (error) {
    if (version === requestVersion) errorMessage.value = error.message
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function selectPeriod(value) {
  router.replace({ query: value === 'TOTAL' ? {} : { period: value } })
}

function formatDate(value) {
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' }).format(
    new Date(`${value}T00:00:00`),
  )
}

function formatDateTime(value) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '—'
}
</script>

<template>
  <PortalShell
    title="成员积分榜"
    description="查看所有正式学生成员的总榜与当前自然周期榜单；同分成员并列，教师不参与排名。"
  >
    <nav class="points-period-tabs" aria-label="积分榜统计周期">
      <button
        v-for="item in periods"
        :key="item.value"
        type="button"
        :class="{ active: period === item.value }"
        :aria-current="period === item.value ? 'page' : undefined"
        @click="selectPeriod(item.value)"
      >
        <strong>{{ item.label }}</strong
        ><span>{{ item.description }}</span>
      </button>
    </nav>

    <div v-if="loading" class="portal-state">正在统计{{ selectedPeriod.label }}…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <template v-else-if="leaderboard">
      <section class="points-leaderboard-overview" aria-label="榜单概览">
        <article>
          <Trophy :size="21" aria-hidden="true" />
          <span>当前第一</span>
          <strong>{{ leader?.memberName || '暂无成员' }}</strong>
          <small>{{ leader ? `${leader.points} 分` : '—' }}</small>
        </article>
        <article>
          <UsersRound :size="21" aria-hidden="true" />
          <span>参榜成员</span>
          <strong>{{ entries.length }}</strong>
          <small>含本周期零分成员</small>
        </article>
        <article>
          <CalendarRange :size="21" aria-hidden="true" />
          <span>统计范围</span>
          <strong>{{ selectedPeriod.label }}</strong>
          <small>{{ rangeLabel }}</small>
        </article>
      </section>

      <section class="points-ranking-card" aria-labelledby="points-ranking-title">
        <header>
          <div>
            <h2 id="points-ranking-title">{{ selectedPeriod.label }}完整榜单</h2>
          </div>
          <span>更新于 {{ formatDateTime(leaderboard.generatedAt) }}</span>
        </header>

        <div v-if="entries.length" class="points-ranking-table-wrap">
          <table class="points-ranking-table">
            <thead>
              <tr>
                <th scope="col">排名</th>
                <th scope="col">成员</th>
                <th scope="col">成员编号</th>
                <th scope="col">身份</th>
                <th scope="col">{{ selectedPeriod.label }}积分</th>
                <th v-if="period !== 'TOTAL'" scope="col">历史总分</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in entries" :key="entry.memberProfileId" :class="{ current: entry.currentMember }">
                <td>
                  <span class="points-rank-number" :data-rank="entry.rank"
                    ><Medal v-if="entry.rank <= 3" :size="16" aria-hidden="true" />{{ entry.rank }}</span
                  >
                </td>
                <td>
                  <RouterLink class="points-ranked-member" :to="`/members/${entry.memberProfileId}`">
                    <img v-if="entry.avatarUrl" :src="entry.avatarUrl" alt="" loading="lazy" />
                    <b v-else aria-hidden="true">{{ entry.memberName.slice(0, 1) }}</b>
                    <span
                      ><strong>{{ entry.memberName }}</strong
                      ><small v-if="entry.currentMember">当前账号</small></span
                    >
                  </RouterLink>
                </td>
                <td class="member-code">{{ entry.memberCode }}</td>
                <td>{{ entry.role === 'CORE_STUDENT' ? '核心学生' : '正式成员' }}</td>
                <td class="points-score">
                  <strong>{{ entry.points }}</strong
                  ><span>分</span>
                </td>
                <td v-if="period !== 'TOTAL'" class="points-total-score">{{ entry.totalPoints }} 分</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="points-ranking-empty">暂无符合参榜条件的正式学生成员。</div>
      </section>
    </template>
  </PortalShell>
</template>
