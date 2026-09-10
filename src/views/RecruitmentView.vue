<script setup>
import { CalendarDays, Check, Circle, Clock3, ImagePlus, Link2, MapPin, Plus, Send, TicketCheck, Trash2 } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import AuthenticatedImage from '../components/AuthenticatedImage.vue'
import PortalShell from '../components/PortalShell.vue'
import { authState, bookInterviewSession, cancelInterviewBooking, deleteRecruitmentPortfolioImage, getInterviewSchedule, getOwnApplication, getRecruitmentQuestions, saveOwnApplication, uploadRecruitmentPortfolioImages } from '../services/authApi'
import { showSubmissionFeedback } from '../services/submissionFeedback'

const stages = ['SIGNUP', 'SCREENING', 'INTERVIEW', 'SKILL_TEST', 'PROBATION', 'FORMAL_MEMBER']
const stageLabels = { SIGNUP: '报名', SCREENING: '初筛', INTERVIEW: '面试', SKILL_TEST: '技能测试', PROBATION: '试用期', FORMAL_MEMBER: '正式成员', REJECTED: '未通过' }
const directions = ['无人机', '机器人', '视觉', '嵌入式', '硬件', '新媒体', '算法', '深度学习']
const application = ref(null)
const questions = ref([])
const selectedImages = ref([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const interviewSchedule = ref(null)
const interviewLoading = ref(false)
let interviewPollTimer
const form = reactive({
  name: '', major: '', className: '', grade: '', email: '', phone: '', wechat: '', selfIntroduction: '',
  interestDirections: [], existingSkills: '', experience: '', intendedTags: '', portfolioIntroduction: '',
  mediaLinks: [], technicalAnswers: {},
})
const legacyDirections = computed(() => form.interestDirections.filter(value => !directions.includes(value)))
const editable = computed(() => !application.value || application.value.stage === 'SIGNUP')
const currentStageIndex = computed(() => stages.indexOf(application.value?.stage || 'SIGNUP'))
const rejectedAtScreening = computed(() => {
  if (application.value?.stage !== 'REJECTED') return false
  const rejection = [...(application.value.history || [])].reverse().find(item => item.toStage === 'REJECTED')
  return rejection?.fromStage === 'SCREENING'
})
const answeredCount = computed(() => questions.value.filter(question => form.technicalAnswers[question.id]?.trim()).length)
const remainingImageSlots = computed(() => Math.max(0, 6 - (application.value?.portfolioImages?.length || 0) - selectedImages.value.length))
const groupedInterviewSessions = computed(() => {
  const groups = new Map()
  for (const session of interviewSchedule.value?.availableSessions || []) {
    const date = new Date(session.startAt).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' })
    if (!groups.has(date)) groups.set(date, [])
    groups.get(date).push(session)
  }
  return [...groups.entries()].map(([date, sessions]) => ({ date, sessions }))
})

onMounted(async () => {
  try {
    const [ownApplication, assignedQuestions] = await Promise.all([getOwnApplication(), getRecruitmentQuestions()])
    questions.value = assignedQuestions || []
    application.value = ownApplication
    if (ownApplication) fillForm(ownApplication)
    else if (authState.account?.role === 'VISITOR') {
      const username = authState.account.username || ''
      if (username.includes('@')) form.email = username
      else if (/^\+?\d+$/.test(username)) form.phone = username
    }
    if (ownApplication?.stage === 'INTERVIEW') {
      await refreshInterviewSchedule()
      interviewPollTimer = window.setInterval(refreshInterviewSchedule, 10000)
    }
  } catch (error) { errorMessage.value = error.message }
  finally { loading.value = false }
})
onBeforeUnmount(() => {
  selectedImages.value.forEach(item => URL.revokeObjectURL(item.preview))
  window.clearInterval(interviewPollTimer)
})

async function refreshInterviewSchedule() {
  interviewLoading.value = !interviewSchedule.value
  try {
    interviewSchedule.value = await getInterviewSchedule()
    if (interviewSchedule.value?.eligible === false && application.value?.stage === 'INTERVIEW') {
      const latestApplication = await getOwnApplication()
      if (latestApplication) {
        application.value = latestApplication
        fillForm(latestApplication)
      }
    }
  }
  catch (error) { errorMessage.value = error.message }
  finally { interviewLoading.value = false }
}

async function bookInterview(sessionId) {
  saving.value = true; errorMessage.value = ''; successMessage.value = ''
  try { interviewSchedule.value = await bookInterviewSession(sessionId); successMessage.value = '面试预约成功，你的面试号已经生成。' }
  catch (error) { errorMessage.value = error.message }
  finally { saving.value = false }
}

async function cancelInterview() {
  if (!window.confirm('确认取消当前面试预约吗？取消后需要重新选择场次。')) return
  saving.value = true; errorMessage.value = ''; successMessage.value = ''
  try { interviewSchedule.value = await cancelInterviewBooking(); successMessage.value = '面试预约已取消。' }
  catch (error) { errorMessage.value = error.message }
  finally { saving.value = false }
}

function formatInterviewTime(value) {
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

async function submit() {
  errorMessage.value = ''; successMessage.value = ''
  if (answeredCount.value < 3) { errorMessage.value = '请从随机抽取的 5 道技术认知题中任选至少 3 道回答。'; return }
  const wasSubmitted = Boolean(application.value)
  saving.value = true
  try {
    const technicalAnswers = questions.value.filter(q => form.technicalAnswers[q.id]?.trim()).map(q => ({ questionId: q.id, answer: form.technicalAnswers[q.id].trim() }))
    application.value = await saveOwnApplication({
      name: form.name.trim(), major: form.major.trim(), className: form.className.trim(), grade: form.grade.trim() || null,
      email: form.email.trim(), phone: form.phone.trim(), wechat: form.wechat.trim(), selfIntroduction: form.selfIntroduction.trim() || null,
      interestDirections: form.interestDirections, existingSkills: splitTags(form.existingSkills), experience: form.experience.trim() || null,
      intendedTags: splitTags(form.intendedTags), portfolioIntroduction: form.portfolioIntroduction.trim() || null,
      mediaLinks: form.mediaLinks.filter(link => link.platform.trim() && link.url.trim()).map(link => ({ platform: link.platform.trim(), account: link.account.trim() || null, url: link.url.trim() })),
      technicalQuestionIds: questions.value.map(q => q.id), technicalAnswers,
    })
    if (selectedImages.value.length) {
      application.value = await uploadRecruitmentPortfolioImages(selectedImages.value.map(item => item.file))
      clearSelectedImages()
    }
    fillForm(application.value)
    showSubmissionFeedback({
      eyebrow: wasSubmitted ? 'APPLICATION UPDATED' : 'APPLICATION SUBMITTED',
      title: wasSubmitted ? '报名修改已保存' : '报名表已提交',
      message: wasSubmitted
        ? '报名资料、个人展示和技术认知回答已经更新。进入初筛前仍可继续修改。'
        : '报名资料、个人展示和技术认知回答已经保存。你可以在本页查看进度，进入初筛前仍可修改。',
      confirmLabel: '查看报名进度',
    })
  } catch (error) { errorMessage.value = error.message }
  finally { saving.value = false }
}

function fillForm(value) {
  Object.assign(form, {
    name: value.name, major: value.major, className: value.className, grade: value.grade || '', email: value.email || '',
    phone: value.phone || '', wechat: value.wechat || '', selfIntroduction: value.selfIntroduction || '',
    interestDirections: [...value.interestDirections], existingSkills: value.existingSkills.join('、'), experience: value.experience || '',
    intendedTags: value.intendedTags.join('、'), portfolioIntroduction: value.portfolioIntroduction || '',
    mediaLinks: (value.mediaLinks || []).map(link => ({ ...link, account: link.account || '' })), technicalAnswers: {},
  })
  ;(value.technicalAnswers || []).forEach(item => { form.technicalAnswers[item.questionId] = item.answer })
  if (value.technicalQuestions?.length) questions.value = value.technicalQuestions
}
function splitTags(value) { return value.split(/[、,，\n]/).map(item => item.trim()).filter(Boolean) }
function addMediaLink() { if (form.mediaLinks.length < 8) form.mediaLinks.push({ platform: '', account: '', url: '' }) }
function removeMediaLink(index) { form.mediaLinks.splice(index, 1) }
function chooseImages(event) {
  const files = [...event.target.files]
  event.target.value = ''
  errorMessage.value = ''
  for (const file of files.slice(0, remainingImageSlots.value)) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) { errorMessage.value = '作品图片仅支持 JPG、PNG、WebP，单张最大 5MB。'; continue }
    selectedImages.value.push({ file, preview: URL.createObjectURL(file) })
  }
}
function removeSelectedImage(index) { URL.revokeObjectURL(selectedImages.value[index].preview); selectedImages.value.splice(index, 1) }
function clearSelectedImages() { selectedImages.value.forEach(item => URL.revokeObjectURL(item.preview)); selectedImages.value = [] }
async function deleteExistingImage(imageId) {
  if (!editable.value) return
  errorMessage.value = ''; successMessage.value = ''
  try {
    application.value = await deleteRecruitmentPortfolioImage(imageId)
    successMessage.value = '作品图片已删除。'
  }
  catch (error) { errorMessage.value = error.message }
}
</script>

<template>
  <PortalShell eyebrow="RECRUITMENT / APPLICANT" title="我的报名" description="用简洁的信息、真实作品和技术思考介绍自己。">
    <div v-if="loading" class="portal-state">正在读取报名进度…</div>
    <template v-else>
      <section class="recruitment-progress-card">
        <header><div><p>CURRENT STAGE</p><h2>{{ stageLabels[application?.stage || 'SIGNUP'] }}</h2></div><span v-if="application">最后更新 {{ new Date(application.updatedAt).toLocaleString('zh-CN') }}</span><span v-else>尚未提交报名表</span></header>
        <ol v-if="application?.stage !== 'REJECTED'" class="stage-track"><li v-for="(stage, index) in stages" :key="stage" :class="{ done: index < currentStageIndex, active: index === currentStageIndex }"><span><Check v-if="index < currentStageIndex" :size="15" /><Clock3 v-else-if="index === currentStageIndex" :size="15" /><Circle v-else :size="13" /></span><strong>{{ stageLabels[stage] }}</strong></li></ol>
        <div v-else class="rejected-state" :class="{ 'screening-rejected-state': rejectedAtScreening }">
          <strong>{{ rejectedAtScreening ? '感谢你认真完成本次报名' : '本轮招新流程已结束' }}</strong>
          <p v-if="rejectedAtScreening">本轮暂未通过初筛，建议先围绕感兴趣的方向补充基础知识，并尝试完成一些小项目。欢迎你在下一次招新时再次报名，我们期待看到你的进步。</p>
          <p v-else>如需了解评价或重新报名，请联系实验室管理员。</p>
        </div>
      </section>

      <section v-if="application?.stage === 'INTERVIEW'" class="interview-booking-card">
        <header><div><p>INTERVIEW APPOINTMENT</p><h2>面试预约</h2></div><span>面试开始前 1 小时停止预约</span></header>
        <div v-if="interviewLoading" class="empty-note">正在读取面试场次…</div>
        <template v-else-if="interviewSchedule?.booking">
          <div class="booking-ticket">
            <div class="booking-number"><small>你的面试号</small><strong>{{ interviewSchedule.booking.queueNumber }}</strong><span v-if="interviewSchedule.booking.currentlyCalledNumber">当前叫到 {{ interviewSchedule.booking.currentlyCalledNumber }} 号</span><span v-else>尚未开始叫号</span></div>
            <div class="booking-details"><p><CalendarDays :size="18" /><span>{{ new Date(interviewSchedule.booking.startAt).toLocaleDateString('zh-CN') }} · {{ formatInterviewTime(interviewSchedule.booking.startAt) }}—{{ formatInterviewTime(interviewSchedule.booking.endAt) }}</span></p><p><MapPin :size="18" /><span>{{ interviewSchedule.booking.location }}</span></p><p><TicketCheck :size="18" /><span>每次叫号 1 人；请留意当前叫号状态。</span></p></div>
          </div>
          <button v-if="interviewSchedule.booking.canCancel" class="portal-secondary danger" type="button" :disabled="saving" @click="cancelInterview">取消预约</button>
        </template>
        <template v-else>
          <p class="interview-booking-message">{{ interviewSchedule?.message }}</p>
          <div v-for="group in groupedInterviewSessions" :key="group.date" class="interview-date-group"><h3>{{ group.date }}</h3><div class="interview-session-options"><article v-for="session in group.sessions" :key="session.id"><div><strong>{{ formatInterviewTime(session.startAt) }}—{{ formatInterviewTime(session.endAt) }}</strong><span>剩余 {{ session.remainingPlaces }} 个名额</span></div><button type="button" :disabled="saving" @click="bookInterview(session.id)">选择此场次</button></article></div></div>
          <div v-if="!groupedInterviewSessions.length" class="empty-note">梅琳娜已经提醒指导老师和核心成员发布新的面试场次。</div>
        </template>
      </section>

      <div class="recruitment-layout">
        <section class="application-card">
          <header><div><p>APPLICATION FORM</p><h2>报名表</h2></div><span>{{ editable ? '初筛前可修改' : '当前阶段已锁定' }}</span></header>
          <div v-if="successMessage" class="save-message" role="status">{{ successMessage }}</div>
          <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
          <form @submit.prevent="submit">
            <div class="application-fields">
              <h3 class="form-section-title full"><span>01</span>基本信息</h3>
              <label>姓名<input v-model.trim="form.name" required :disabled="!editable" autocomplete="name" /></label>
              <label>邮箱<input v-model.trim="form.email" type="email" required maxlength="190" :disabled="!editable" autocomplete="email" /></label>
              <label>手机号码<input v-model.trim="form.phone" type="tel" maxlength="30" :disabled="!editable" autocomplete="tel" placeholder="例如：13800138000" /></label>
              <label>微信号<input v-model.trim="form.wechat" maxlength="80" :disabled="!editable" /></label>
              <label>专业<input v-model.trim="form.major" placeholder="软件工程" required :disabled="!editable" /></label>
              <label>班级<input v-model.trim="form.className" placeholder="24软件工程1班" required :disabled="!editable" /></label>
              <label>年级<input v-model.trim="form.grade" :disabled="!editable" placeholder="24级" /></label>
              <fieldset class="full" :disabled="!editable"><legend>兴趣方向（至少一项）</legend><label v-for="direction in [...directions, ...legacyDirections]" :key="direction" class="check-option"><input v-model="form.interestDirections" type="checkbox" :value="direction" />{{ direction }}</label></fieldset>
              <label class="full">自我介绍<textarea v-model="form.selfIntroduction" :disabled="!editable" rows="4" maxlength="5000" placeholder="简单介绍自己的兴趣、特点和加入实验室的期待。" /></label>
              <label class="full">已有技能<input v-model="form.existingSkills" :disabled="!editable" placeholder="Python、嵌入式、摄影等" /></label>
              <label class="full">项目 / 竞赛经历<textarea v-model="form.experience" :disabled="!editable" rows="5" placeholder="说明你负责的部分、使用的技术和结果。" /></label>
              <label class="full">意向标签（至少一项）<input v-model="form.intendedTags" required :disabled="!editable" placeholder="例如 无人机系统、计算机视觉" /></label>

              <h3 class="form-section-title full"><span>02</span>个人展示</h3>
              <label class="full">作品与个人介绍<textarea v-model="form.portfolioIntroduction" :disabled="!editable" rows="4" maxlength="3000" placeholder="介绍你的代表作品、创作方向或希望展示的内容。" /></label>
              <section class="portfolio-uploader full" aria-labelledby="portfolio-title">
                <header><div><strong id="portfolio-title">作品照片</strong><small>最多 6 张，支持 JPG、PNG、WebP，单张最大 5MB</small></div><label v-if="editable && remainingImageSlots"><ImagePlus :size="17" />选择图片<input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="chooseImages" /></label></header>
                <div v-if="application?.portfolioImages?.length || selectedImages.length" class="portfolio-preview-grid">
                  <figure v-for="image in application?.portfolioImages || []" :key="image.id"><AuthenticatedImage :src="image.url" :alt="image.originalName" /><figcaption>{{ image.originalName }}</figcaption><button v-if="editable" type="button" aria-label="删除已上传图片" @click="deleteExistingImage(image.id)"><Trash2 :size="15" /></button></figure>
                  <figure v-for="(image, index) in selectedImages" :key="image.preview"><img :src="image.preview" :alt="image.file.name" /><figcaption>{{ image.file.name }} · 待上传</figcaption><button type="button" aria-label="移除待上传图片" @click="removeSelectedImage(index)"><Trash2 :size="15" /></button></figure>
                </div>
                <p v-else>可以上传项目实物、程序界面、设计作品或活动成果。</p>
              </section>
              <section class="media-links-editor full" aria-labelledby="media-links-title">
                <header><div><strong id="media-links-title"><Link2 :size="17" />个人主页与自媒体</strong><small>GitHub、Gitee、B站、抖音、小红书、博客等</small></div><button v-if="editable && form.mediaLinks.length < 8" type="button" @click="addMediaLink"><Plus :size="16" />添加链接</button></header>
                <div v-for="(link, index) in form.mediaLinks" :key="index" class="media-link-row"><input v-model.trim="link.platform" :disabled="!editable" required placeholder="平台" aria-label="平台名称" /><input v-model.trim="link.account" :disabled="!editable" placeholder="账号名称（可选）" aria-label="账号名称" /><input v-model.trim="link.url" :disabled="!editable" required type="url" placeholder="https://" aria-label="主页链接" /><button v-if="editable" type="button" aria-label="删除链接" @click="removeMediaLink(index)"><Trash2 :size="16" /></button></div>
                <p v-if="!form.mediaLinks.length">还没有添加个人主页或自媒体链接。</p>
              </section>

              <h3 class="form-section-title full"><span>03</span>技术认知 <small>随机 5 题，任选 3 题回答，也可以全部完成</small></h3>
              <section class="technical-question-list full">
                <label v-for="(question, index) in questions" :key="question.id"><span><b>{{ String(index + 1).padStart(2, '0') }}</b>{{ question.prompt }}</span><textarea v-model="form.technicalAnswers[question.id]" :disabled="!editable" rows="4" maxlength="2000" placeholder="写下你的理解或实际经历；不会也可以如实说明。" /></label>
                <div class="answer-progress" :class="{ complete: answeredCount >= 3 }"><strong>已回答 {{ answeredCount }} / 5</strong><span>{{ answeredCount >= 3 ? '已达到提交要求' : `还需回答 ${3 - answeredCount} 题` }}</span></div>
              </section>
            </div>
            <button v-if="editable" class="portal-primary" type="submit" :disabled="saving"><Send :size="18" aria-hidden="true" />{{ saving ? '保存中…' : application ? '修改报名表' : '提交报名表' }}</button>
          </form>
        </section>

        <aside class="application-history"><header><p>STATUS HISTORY</p><h2>流程记录</h2></header><ol v-if="application?.history?.length"><li v-for="item in [...application.history].reverse()" :key="`${item.toStage}-${item.changedAt}`"><span /><div><strong>{{ stageLabels[item.toStage] }}</strong><p>{{ item.note || '状态已更新' }}</p><small>{{ item.operatorUsername }} · {{ new Date(item.changedAt).toLocaleString('zh-CN') }}</small></div></li></ol><div v-else class="empty-note">提交报名表后，阶段变化会记录在这里。</div></aside>
      </div>
    </template>
  </PortalShell>
</template>
