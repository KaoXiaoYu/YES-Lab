<script setup>
import { Check, Circle, Clock3, Send } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import { getOwnApplication, saveOwnApplication } from '../services/authApi'

const stages = ['SIGNUP', 'SCREENING', 'INTERVIEW', 'SKILL_TEST', 'PROBATION', 'FORMAL_MEMBER']
const stageLabels = {
  SIGNUP: '报名', SCREENING: '初筛', INTERVIEW: '面试', SKILL_TEST: '技能测试',
  PROBATION: '试用期', FORMAL_MEMBER: '正式成员', REJECTED: '未通过',
}
const directions = ['无人机', '空地协同', '具身智能']
const application = ref(null)
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const form = reactive({
  name: '', major: '', className: '', grade: '', contact: '',
  interestDirections: [], existingSkills: '', experience: '', intendedTags: '',
})

const editable = computed(() => !application.value || application.value.stage === 'SIGNUP')
const currentStageIndex = computed(() => stages.indexOf(application.value?.stage || 'SIGNUP'))

onMounted(async () => {
  try {
    application.value = await getOwnApplication()
    if (application.value) fillForm(application.value)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

async function submit() {
  errorMessage.value = ''
  successMessage.value = ''
  saving.value = true
  try {
    application.value = await saveOwnApplication({
      name: form.name.trim(),
      major: form.major.trim(),
      className: form.className.trim(),
      grade: form.grade.trim() || null,
      contact: form.contact.trim(),
      interestDirections: form.interestDirections,
      existingSkills: splitTags(form.existingSkills),
      experience: form.experience.trim() || null,
      intendedTags: splitTags(form.intendedTags),
    })
    fillForm(application.value)
    successMessage.value = '报名表已保存。进入初筛前，你仍可以继续修改。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function fillForm(value) {
  form.name = value.name
  form.major = value.major
  form.className = value.className
  form.grade = value.grade || ''
  form.contact = value.contact
  form.interestDirections = [...value.interestDirections]
  form.existingSkills = value.existingSkills.join('、')
  form.experience = value.experience || ''
  form.intendedTags = value.intendedTags.join('、')
}

function splitTags(value) {
  return value.split(/[、,，\n]/).map(item => item.trim()).filter(Boolean)
}
</script>

<template>
  <PortalShell eyebrow="RECRUITMENT / APPLICANT" title="我的报名" description="游客账号与成员账号相互独立；通过试用期后，管理员会保留招新记录并将当前账号转换为正式成员。">
    <div v-if="loading" class="portal-state">正在读取报名进度…</div>
    <template v-else>
      <section class="recruitment-progress-card">
        <header><div><p>CURRENT STAGE</p><h2>{{ stageLabels[application?.stage || 'SIGNUP'] }}</h2></div><span v-if="application">最后更新 {{ new Date(application.updatedAt).toLocaleString('zh-CN') }}</span><span v-else>尚未提交报名表</span></header>
        <ol v-if="application?.stage !== 'REJECTED'" class="stage-track">
          <li v-for="(stage, index) in stages" :key="stage" :class="{ done: index < currentStageIndex, active: index === currentStageIndex }"><span><Check v-if="index < currentStageIndex" :size="15" aria-hidden="true" /><Clock3 v-else-if="index === currentStageIndex" :size="15" aria-hidden="true" /><Circle v-else :size="13" aria-hidden="true" /></span><strong>{{ stageLabels[stage] }}</strong></li>
        </ol>
        <div v-else class="rejected-state">本轮招新流程已结束。如需了解评价或重新报名，请联系实验室管理员。</div>
      </section>

      <div class="recruitment-layout">
        <section class="application-card">
          <header><div><p>APPLICATION FORM</p><h2>报名表</h2></div><span>{{ editable ? '初筛前可修改' : '当前阶段已锁定' }}</span></header>
          <div v-if="successMessage" class="save-message" role="status">{{ successMessage }}</div>
          <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
          <form @submit.prevent="submit">
            <div class="application-fields">
              <label>姓名<input v-model.trim="form.name" required :disabled="!editable" autocomplete="name" /></label>
              <label>联系方式<input v-model.trim="form.contact" required :disabled="!editable" autocomplete="tel" /></label>
              <label>专业<input v-model.trim="form.major" required :disabled="!editable" /></label>
              <label>班级<input v-model.trim="form.className" required :disabled="!editable" /></label>
              <label>年级<input v-model.trim="form.grade" :disabled="!editable" placeholder="例如 2025" /></label>
              <fieldset class="full" :disabled="!editable"><legend>兴趣方向（至少一项）</legend><label v-for="direction in directions" :key="direction" class="check-option"><input v-model="form.interestDirections" type="checkbox" :value="direction" />{{ direction }}</label></fieldset>
              <label class="full">已有技能<input v-model="form.existingSkills" :disabled="!editable" placeholder="用逗号或顿号分隔，例如 Python、嵌入式" /></label>
              <label class="full">项目 / 竞赛经历<textarea v-model="form.experience" :disabled="!editable" rows="6" placeholder="可以说明你负责的部分、使用的技术和结果。"></textarea></label>
              <label class="full">意向标签（至少一项）<input v-model="form.intendedTags" required :disabled="!editable" placeholder="例如 无人机系统、计算机视觉" /></label>
            </div>
            <button v-if="editable" class="portal-primary" type="submit" :disabled="saving"><Send :size="18" aria-hidden="true" />{{ saving ? '保存中…' : '保存并提交报名表' }}</button>
          </form>
        </section>

        <aside class="application-history">
          <header><p>STATUS HISTORY</p><h2>流程记录</h2></header>
          <ol v-if="application?.history?.length"><li v-for="item in [...application.history].reverse()" :key="`${item.toStage}-${item.changedAt}`"><span></span><div><strong>{{ stageLabels[item.toStage] }}</strong><p>{{ item.note || '状态已更新' }}</p><small>{{ item.operatorUsername }} · {{ new Date(item.changedAt).toLocaleString('zh-CN') }}</small></div></li></ol>
          <div v-else class="empty-note">提交报名表后，所有阶段变更时间和操作人都会记录在这里。</div>
          <div v-if="application?.interview?.interviewerName" class="interview-summary"><p>INTERVIEW</p><strong>面试官：{{ application.interview.interviewerName }}</strong><span v-if="application.interview.score !== null">评分：{{ application.interview.score }} / 100</span><span v-if="application.interview.evaluation">{{ application.interview.evaluation }}</span></div>
        </aside>
      </div>
    </template>
  </PortalShell>
</template>
