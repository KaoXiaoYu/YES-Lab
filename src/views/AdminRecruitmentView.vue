<script setup>
import { ArrowRight, CheckCircle2, Search, UserCheck, UserPlus, XCircle } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import {
  changeRecruitmentStage, convertRecruitmentToMember, listInterviewers,
  listRecruitmentApplications, saveInterview,
} from '../services/authApi'

const stageLabels = {
  SIGNUP: '报名', SCREENING: '初筛', INTERVIEW: '面试', SKILL_TEST: '技能测试',
  PROBATION: '试用期', FORMAL_MEMBER: '正式成员', REJECTED: '未通过',
}
const nextStages = { SIGNUP: 'SCREENING', INTERVIEW: 'SKILL_TEST', SKILL_TEST: 'PROBATION' }
const applications = ref([])
const interviewers = ref([])
const selected = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const query = ref('')
const stageFilter = ref('ALL')
const interviewForm = reactive({ interviewerUsername: '', score: '', evaluation: '', suggestedTags: '', passed: null })
const convertForm = reactive({ memberCode: '', skillTags: '' })

const filteredApplications = computed(() => applications.value.filter((application) => {
  const matchesStage = stageFilter.value === 'ALL' || application.stage === stageFilter.value
  const keyword = query.value.trim().toLowerCase()
  const matchesKeyword = !keyword || [application.name, application.applicantUsername, application.major, application.className].some(value => value?.toLowerCase().includes(keyword))
  return matchesStage && matchesKeyword
}))

onMounted(refresh)

async function refresh() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [applicationData, interviewerData] = await Promise.all([listRecruitmentApplications(), listInterviewers()])
    applications.value = applicationData
    interviewers.value = interviewerData
    if (selected.value) selectApplication(applicationData.find(item => item.id === selected.value.id) || applicationData[0])
    else selectApplication(applicationData[0])
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function selectApplication(application) {
  selected.value = application || null
  successMessage.value = ''
  errorMessage.value = ''
  interviewForm.interviewerUsername = application?.interview?.interviewerName || interviewers.value[0]?.username || ''
  interviewForm.score = application?.interview?.score ?? ''
  interviewForm.evaluation = application?.interview?.evaluation || ''
  interviewForm.suggestedTags = application?.interview?.suggestedTags?.join('、') || application?.intendedTags?.join('、') || ''
  interviewForm.passed = application?.interview?.passed ?? null
  convertForm.memberCode = ''
  convertForm.skillTags = application?.interview?.suggestedTags?.join('、') || application?.intendedTags?.join('、') || ''
}

async function advance() {
  const target = nextStages[selected.value?.stage]
  if (!target) return
  await runAction(() => changeRecruitmentStage(selected.value.id, { stage: target, note: `进入${stageLabels[target]}`, linkedQuizId: null }), `已进入${stageLabels[target]}阶段。`)
}

async function rejectApplication() {
  if (!window.confirm(`确认结束 ${selected.value.name} 的本轮招新流程吗？`)) return
  await runAction(() => changeRecruitmentStage(selected.value.id, { stage: 'REJECTED', note: '本轮招新未通过', linkedQuizId: null }), '报名流程已结束。')
}

async function submitInterview() {
  await runAction(() => saveInterview(selected.value.id, {
    interviewerUsername: interviewForm.interviewerUsername,
    score: interviewForm.score === '' ? null : Number(interviewForm.score),
    evaluation: interviewForm.evaluation || null,
    suggestedTags: splitTags(interviewForm.suggestedTags),
    passed: interviewForm.passed,
  }), '面试分配与评价已保存。')
}

async function convertMember() {
  await runAction(() => convertRecruitmentToMember(selected.value.id, {
    memberCode: convertForm.memberCode.trim(),
    skillTags: splitTags(convertForm.skillTags),
  }), '已转换为正式成员；重新登录后会获得成员权限。')
}

async function runAction(action, success) {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const updated = await action()
    applications.value = applications.value.map(item => item.id === updated.id ? updated : item)
    selectApplication(updated)
    successMessage.value = success
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

function splitTags(value) {
  return value.split(/[、,，\n]/).map(item => item.trim()).filter(Boolean)
}
</script>

<template>
  <PortalShell eyebrow="ADMIN / RECRUITMENT" title="招新管理" description="教师与核心学生拥有相同的系统管理员权限。所有阶段变化都会记录时间和操作账号。">
    <div v-if="errorMessage && !selected" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <section v-else class="admin-recruitment-layout">
      <aside class="applicant-list">
        <header><div><p>APPLICATIONS</p><h2>报名记录</h2></div><span>{{ applications.length }}</span></header>
        <div class="applicant-tools"><label><Search :size="16" aria-hidden="true" /><input v-model.trim="query" aria-label="搜索报名者" placeholder="姓名 / 账号 / 专业" /></label><select v-model="stageFilter" aria-label="按阶段筛选"><option value="ALL">全部阶段</option><option v-for="(label, key) in stageLabels" :key="key" :value="key">{{ label }}</option></select></div>
        <div v-if="loading" class="empty-note">正在读取报名记录…</div>
        <button v-for="application in filteredApplications" :key="application.id" type="button" :class="{ active: selected?.id === application.id }" @click="selectApplication(application)"><span>{{ application.name.slice(0, 1) }}</span><div><strong>{{ application.name }}</strong><small>{{ application.major }} · {{ application.className }}</small></div><b>{{ stageLabels[application.stage] }}</b></button>
        <div v-if="!loading && !filteredApplications.length" class="empty-note">没有符合条件的报名记录。</div>
      </aside>

      <div v-if="selected" class="application-detail">
        <header class="detail-head"><div><p>{{ selected.applicantUsername }} / {{ stageLabels[selected.stage] }}</p><h2>{{ selected.name }}</h2><span>{{ selected.major }} · {{ selected.className }} · {{ selected.grade || '年级未填' }}</span></div><div class="detail-actions"><button v-if="nextStages[selected.stage]" type="button" :disabled="working" @click="advance">进入{{ stageLabels[nextStages[selected.stage]] }}<ArrowRight :size="17" aria-hidden="true" /></button><button v-if="!['FORMAL_MEMBER', 'REJECTED'].includes(selected.stage)" class="danger" type="button" :disabled="working" @click="rejectApplication"><XCircle :size="17" aria-hidden="true" />结束流程</button></div></header>
        <div v-if="successMessage" class="save-message" role="status">{{ successMessage }}</div>
        <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

        <section class="detail-grid">
          <article><p>CONTACT</p><strong>{{ selected.contact }}</strong></article><article><p>INTEREST</p><div class="detail-tags"><span v-for="item in selected.interestDirections" :key="item">{{ item }}</span></div></article><article><p>EXISTING SKILLS</p><div class="detail-tags"><span v-for="item in selected.existingSkills" :key="item">{{ item }}</span><small v-if="!selected.existingSkills.length">暂无</small></div></article><article><p>INTENDED TAGS</p><div class="detail-tags"><span v-for="item in selected.intendedTags" :key="item">{{ item }}</span></div></article><article class="full"><p>PROJECT / COMPETITION EXPERIENCE</p><span>{{ selected.experience || '未填写项目或竞赛经历。' }}</span></article>
        </section>

        <section v-if="['SCREENING', 'INTERVIEW'].includes(selected.stage)" class="admin-form-card">
          <header><UserCheck :size="22" aria-hidden="true" /><div><p>INTERVIEW</p><h3>面试分配与评价</h3></div></header>
          <div class="admin-form-grid"><label>面试官<select v-model="interviewForm.interviewerUsername"><option v-for="item in interviewers" :key="item.accountId" :value="item.username">{{ item.username }} · {{ item.role === 'TEACHER' ? '教师' : '核心学生' }}</option></select></label><label>评分（0—100）<input v-model="interviewForm.score" type="number" min="0" max="100" /></label><label class="full">面试评价<textarea v-model="interviewForm.evaluation" rows="4"></textarea></label><label class="full">建议标签<input v-model="interviewForm.suggestedTags" placeholder="用逗号或顿号分隔" /></label><fieldset class="full"><legend>面试结论</legend><label class="check-option"><input v-model="interviewForm.passed" type="radio" :value="true" />建议通过</label><label class="check-option"><input v-model="interviewForm.passed" type="radio" :value="false" />建议不通过</label></fieldset></div>
          <button class="portal-primary" type="button" :disabled="working || !interviewForm.interviewerUsername" @click="submitInterview"><CheckCircle2 :size="18" aria-hidden="true" />保存面试记录</button>
        </section>

        <section v-if="selected.stage === 'PROBATION'" class="admin-form-card conversion-card">
          <header><UserPlus :size="22" aria-hidden="true" /><div><p>MEMBER CONVERSION</p><h3>一键转为正式成员</h3></div></header>
          <p>转换后保留当前报名与面试历史，并为账号创建规范成员资料。</p>
          <div class="admin-form-grid"><label>学号 / 内部编号<input v-model.trim="convertForm.memberCode" required /></label><label>能力标签（至少一项）<input v-model="convertForm.skillTags" required /></label></div>
          <button class="portal-primary" type="button" :disabled="working || !convertForm.memberCode || !splitTags(convertForm.skillTags).length" @click="convertMember"><UserPlus :size="18" aria-hidden="true" />确认转为正式成员</button>
        </section>

        <section class="admin-history"><header><p>AUDIT TRAIL</p><h3>状态变更记录</h3></header><ol><li v-for="item in [...selected.history].reverse()" :key="item.changedAt"><span></span><div><strong>{{ stageLabels[item.toStage] }}</strong><p>{{ item.note || '状态已更新' }}</p><small>{{ item.operatorUsername }} · {{ new Date(item.changedAt).toLocaleString('zh-CN') }}</small></div></li></ol></section>
      </div>
      <div v-else class="portal-state">选择一条报名记录查看详情。</div>
    </section>
  </PortalShell>
</template>
