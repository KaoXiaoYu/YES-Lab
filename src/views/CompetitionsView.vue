<script setup>
import { ArrowRight, CalendarDays, CheckCircle2, Clock3, Medal, Plus, ShieldCheck, UsersRound } from '@lucide/vue'
import { competitionState, competitionOutcome } from '../services/competitionStatus'
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { authState, listCompetitions, getAuthenticatedFile } from '../services/authApi'

const registrationId = ref(null)
const registrationUrl = ref('')
const readingRegistration = ref(false)
const items = ref([])
const loading = ref(true)
const errorMessage = ref('')
const lifecycle = ref('ALL')
const route = useRoute()
const lifecycleLabels = { PLANNED: '未开始', FINISHED: '完赛', ONGOING: '历史：进行中' }
const levelLabels = {
  SCHOOL: '校级',
  PROVINCIAL: '省级',
  REGIONAL: '赛区',
  NATIONAL: '国家级',
  INTERNATIONAL: '国际级',
  OTHER: '其他',
}
const reviewLabels = { NOT_REQUIRED: '无需认证', PENDING: '待审核', APPROVED: '已认证', REJECTED: '已驳回' }
const filtered = computed(() =>
  items.value.filter((item) => lifecycle.value === 'ALL' || item.lifecycle === lifecycle.value),
)
const saveMessage = computed(() => {
  if (route.query.saved !== '1') return ''
  const assets = []
  if (route.query.certificate === '1') assets.push('证书')
  const imageCount = Number(route.query.images || 0)
  if (imageCount > 0) assets.push(`${imageCount} 张比赛图片`)
  return assets.length ? `比赛记录已保存，后端已确认接收并可读取${assets.join('和')}。` : '比赛记录已由后端保存。'
})
async function viewRegistration(item) {
  if (registrationUrl.value) URL.revokeObjectURL(registrationUrl.value)
  registrationUrl.value = ''
  if (registrationId.value === item.id) {
    registrationId.value = null
    return
  }
  registrationId.value = item.id
  readingRegistration.value = true
  try {
    registrationUrl.value = await getAuthenticatedFile(`/api/v1/competitions/${item.id}/registration`)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    readingRegistration.value = false
  }
}
onBeforeUnmount(() => {
  if (registrationUrl.value) URL.revokeObjectURL(registrationUrl.value)
})
onMounted(async () => {
  try {
    items.value = await listCompetitions()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <PortalShell
    eyebrow="ACHIEVEMENTS / COMPETITIONS"
    title="比赛管理"
    description="队长提交参赛记录；已结束比赛经管理员核验证书后，才能进入公开成果与成员主页。"
  >
    <div v-if="saveMessage" class="save-message" role="status">{{ saveMessage }}</div>
    <section class="achievement-toolbar">
      <label
        >比赛状态<select v-model="lifecycle">
          <option value="ALL">全部状态</option>
          <option v-for="(label, value) in lifecycleLabels" :key="value" :value="value">{{ label }}</option>
        </select></label
      >
      <div>
        <RouterLink v-if="authState.account?.systemAdmin" class="achievement-secondary" to="/admin/achievements"
          ><ShieldCheck :size="17" aria-hidden="true" />进入审核管理</RouterLink
        ><RouterLink class="portal-primary" to="/competitions/new"
          ><Plus :size="17" aria-hidden="true" />队长提交比赛</RouterLink
        >
      </div>
    </section>
    <div v-if="loading" class="portal-state">正在读取比赛记录…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <div v-else-if="!filtered.length" class="portal-state achievement-empty">
      <Medal :size="28" aria-hidden="true" /><strong>暂无比赛记录</strong
      ><span>由队长提交已结束成果或正在筹备的比赛队伍。</span>
    </div>
    <section v-else class="competition-record-grid">
      <article v-for="item in filtered" :key="item.id" class="competition-record-card">
        <header>
          <span>{{ levelLabels[item.level] }} · {{ competitionState(item) }}</span
          ><b :data-review="item.verificationStatus">{{ reviewLabels[item.verificationStatus] }}</b>
        </header>
        <p class="competition-result-state">{{ competitionOutcome(item) }}</p>
        <p>{{ item.track || '综合赛道' }}</p>
        <h2>{{ item.name }}</h2>
        <button
          v-if="item.hasRegistration && item.canReadRegistration"
          type="button"
          class="disclosure-button"
          :disabled="readingRegistration"
          :aria-expanded="registrationId === item.id"
          @click="viewRegistration(item)"
        >
          {{ registrationId === item.id ? '收起报名截图' : '查看报名截图' }}
        </button>
        <p v-else-if="!item.hasRegistration" class="ledger-muted">历史记录尚未补齐报名截图。</p>
        <div v-if="registrationId === item.id" class="registration-preview">
          <p v-if="readingRegistration" role="status">正在读取报名截图…</p>
          <img v-if="registrationUrl" :src="registrationUrl" alt="已保存的报名截图" />
        </div>
        <div class="competition-record-summary">{{ item.description }}</div>
        <dl>
          <div>
            <dt><UsersRound :size="15" aria-hidden="true" />队长</dt>
            <dd>{{ item.captain.name }}</dd>
          </div>
          <div>
            <dt><CalendarDays :size="15" aria-hidden="true" />日期</dt>
            <dd>{{ item.competitionDate || item.provincialDate || '待定' }}</dd>
          </div>
          <div>
            <dt><CheckCircle2 :size="15" aria-hidden="true" />结果</dt>
            <dd>{{ item.awardName || '尚未结束' }}</dd>
          </div>
        </dl>
        <footer>
          <span
            ><Clock3 :size="14" aria-hidden="true" />{{ item.participants.length }} 名队员<span v-if="item.advisorName">
              · {{ item.advisorName }}指导</span
            ></span
          ><RouterLink v-if="item.canEdit" :to="`/competitions/${item.id}/edit`"
            >编辑记录 <ArrowRight :size="16" aria-hidden="true" /></RouterLink
          ><RouterLink v-else-if="item.verificationStatus === 'APPROVED'" :to="`/competition-results/${item.id}`"
            >查看公开详情 <ArrowRight :size="16" aria-hidden="true"
          /></RouterLink>
        </footer>
      </article>
    </section>
  </PortalShell>
</template>
