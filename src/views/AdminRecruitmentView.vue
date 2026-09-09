<script setup>
import { ArrowRight, Search, UserPlus, XCircle } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import AuthenticatedImage from '../components/AuthenticatedImage.vue'
import InterviewSessionManager from '../components/InterviewSessionManager.vue'
import PortalShell from '../components/PortalShell.vue'
import {
  changeRecruitmentStage, convertRecruitmentToMember, listInterviewers,
  listRecruitmentApplications,
} from '../services/authApi'

const stageLabels = {
  SIGNUP: '报名', SCREENING: '初筛', INTERVIEW: '面试', SKILL_TEST: '技能测试',
  PROBATION: '试用期', FORMAL_MEMBER: '正式成员', REJECTED: '未通过',
}
const nextStages = { SIGNUP: 'SCREENING', SCREENING: 'INTERVIEW', SKILL_TEST: 'PROBATION' }
const applications = ref([])
const interviewers = ref([])
const selected = ref(null)
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const query = ref('')
const stageFilter = ref('ALL')
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
    <InterviewSessionManager :interviewers="interviewers" @completed="refresh" />
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
          <article><p>邮箱</p><strong>{{ selected.email || '未填写' }}</strong></article><article><p>手机号码</p><strong>{{ selected.phone || '未填写' }}</strong></article><article><p>微信号</p><strong>{{ selected.wechat || '未填写' }}</strong></article><article v-if="!selected.email && !selected.phone && selected.contact"><p>原联系方式</p><strong>{{ selected.contact }}</strong></article><article class="full"><p>自我介绍</p><span class="application-introduction">{{ selected.selfIntroduction || '未填写自我介绍。' }}</span></article><article><p>INTEREST</p><div class="detail-tags"><span v-for="item in selected.interestDirections" :key="item">{{ item }}</span></div></article><article><p>EXISTING SKILLS</p><div class="detail-tags"><span v-for="item in selected.existingSkills" :key="item">{{ item }}</span><small v-if="!selected.existingSkills.length">暂无</small></div></article><article><p>INTENDED TAGS</p><div class="detail-tags"><span v-for="item in selected.intendedTags" :key="item">{{ item }}</span></div></article><article class="full"><p>PROJECT / COMPETITION EXPERIENCE</p><span>{{ selected.experience || '未填写项目或竞赛经历。' }}</span></article>
        </section>

        <section class="admin-showcase-review">
          <header><p>PERSONAL SHOWCASE</p><h3>个人展示</h3></header>
          <p class="application-introduction">{{ selected.portfolioIntroduction || '未填写作品介绍。' }}</p>
          <div v-if="selected.portfolioImages?.length" class="admin-portfolio-grid">
            <figure v-for="image in selected.portfolioImages" :key="image.id"><AuthenticatedImage :src="image.url" :alt="image.originalName" /><figcaption>{{ image.originalName }}</figcaption></figure>
          </div>
          <div v-if="selected.mediaLinks?.length" class="admin-media-links"><a v-for="link in selected.mediaLinks" :key="`${link.platform}-${link.url}`" :href="link.url" target="_blank" rel="noopener noreferrer"><strong>{{ link.platform }}</strong><span>{{ link.account || '打开个人主页' }}</span></a></div>
          <div v-if="!selected.portfolioImages?.length && !selected.mediaLinks?.length" class="empty-note">未上传作品图片或个人链接。</div>
        </section>

        <section class="admin-showcase-review">
          <header><p>TECHNICAL AWARENESS</p><h3>技术认知</h3><span>已回答 {{ selected.technicalAnswers?.length || 0 }} / 5</span></header>
          <article v-for="question in selected.technicalQuestions" :key="question.id" class="admin-technical-answer"><strong>{{ question.prompt }}</strong><p>{{ selected.technicalAnswers?.find(answer => answer.questionId === question.id)?.answer || '该题未作答' }}</p></article>
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
