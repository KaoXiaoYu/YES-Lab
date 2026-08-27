<script setup>
import { ArrowRight, CalendarDays, CheckCircle2, Clock3, Medal, Plus, ShieldCheck, UsersRound } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { authState, listCompetitions } from '../services/authApi'

const items = ref([]); const loading = ref(true); const errorMessage = ref(''); const lifecycle = ref('ALL')
const route = useRoute()
const lifecycleLabels = { PLANNED: '筹备中', ONGOING: '进行中', FINISHED: '已结束' }
const levelLabels = { SCHOOL: '校级', PROVINCIAL: '省级', REGIONAL: '赛区', NATIONAL: '国家级', INTERNATIONAL: '国际级', OTHER: '其他' }
const reviewLabels = { NOT_REQUIRED: '无需认证', PENDING: '待审核', APPROVED: '已认证', REJECTED: '已驳回' }
const filtered = computed(() => items.value.filter((item) => lifecycle.value === 'ALL' || item.lifecycle === lifecycle.value))
const saveMessage = computed(() => {
  if (route.query.saved !== '1') return ''
  const assets = []
  if (route.query.certificate === '1') assets.push('证书')
  const imageCount = Number(route.query.images || 0)
  if (imageCount > 0) assets.push(`${imageCount} 张比赛图片`)
  return assets.length
    ? `比赛记录已保存，后端已确认接收并可读取${assets.join('和')}。`
    : '比赛记录已由后端保存。'
})
onMounted(async () => { try { items.value = await listCompetitions() } catch (error) { errorMessage.value = error.message } finally { loading.value = false } })
</script>

<template>
  <PortalShell eyebrow="ACHIEVEMENTS / COMPETITIONS" title="竞赛成果" description="队长提交参赛记录；已结束比赛经管理员核验证书后，才能进入公开成果与成员主页。">
    <div v-if="saveMessage" class="save-message" role="status">{{ saveMessage }}</div>
    <section class="achievement-toolbar"><label>比赛状态<select v-model="lifecycle"><option value="ALL">全部状态</option><option v-for="(label, value) in lifecycleLabels" :key="value" :value="value">{{ label }}</option></select></label><div><RouterLink v-if="authState.account?.systemAdmin" class="achievement-secondary" to="/admin/achievements"><ShieldCheck :size="17" aria-hidden="true" />进入审核管理</RouterLink><RouterLink class="portal-primary" to="/competitions/new"><Plus :size="17" aria-hidden="true" />队长提交比赛</RouterLink></div></section>
    <div v-if="loading" class="portal-state">正在读取比赛记录…</div><div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <div v-else-if="!filtered.length" class="portal-state achievement-empty"><Medal :size="28" aria-hidden="true" /><strong>暂无比赛记录</strong><span>由队长提交已结束成果或正在筹备的比赛队伍。</span></div>
    <section v-else class="competition-record-grid">
      <article v-for="item in filtered" :key="item.id" class="competition-record-card">
        <header><span>{{ levelLabels[item.level] }} · {{ lifecycleLabels[item.lifecycle] }}</span><b :data-review="item.verificationStatus">{{ reviewLabels[item.verificationStatus] }}</b></header>
        <p>{{ item.track || '综合赛道' }}</p><h2>{{ item.name }}</h2><div class="competition-record-summary">{{ item.description }}</div>
        <dl><div><dt><UsersRound :size="15" aria-hidden="true" />队长</dt><dd>{{ item.captain.name }}</dd></div><div><dt><CalendarDays :size="15" aria-hidden="true" />日期</dt><dd>{{ item.competitionDate || item.provincialDate || '待定' }}</dd></div><div><dt><CheckCircle2 :size="15" aria-hidden="true" />结果</dt><dd>{{ item.awardName || '尚未结束' }}</dd></div></dl>
        <footer><span><Clock3 :size="14" aria-hidden="true" />{{ item.participants.length }} 名队员<span v-if="item.advisorName"> · {{ item.advisorName }}指导</span></span><RouterLink v-if="item.canEdit" :to="`/competitions/${item.id}/edit`">编辑记录 <ArrowRight :size="16" aria-hidden="true" /></RouterLink><RouterLink v-else-if="item.verificationStatus === 'APPROVED'" :to="`/competition-results/${item.id}`">查看公开详情 <ArrowRight :size="16" aria-hidden="true" /></RouterLink></footer>
      </article>
    </section>
  </PortalShell>
</template>
