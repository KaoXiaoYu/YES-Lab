<script setup>
import { ArrowLeft, Check, Save, UsersRound } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { createProject, listProjectMemberOptions } from '../services/authApi'

const router = useRouter()
const members = ref([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const form = reactive({
  projectName: '', teamName: '', description: '', type: 'RESEARCH', status: 'PLANNING',
  leaderProfileId: '', advisorProfileId: '', memberProfileIds: [], administratorProfileIds: [],
  requiredSkillTagsText: '', startDate: '', endDate: '', stageGoalsText: '',
  progressDescription: '', outcomes: '', gitRepositoryUrl: '', documentUrl: '', externallyVisible: false,
})

const teachers = computed(() => members.value.filter((member) => member.role === 'TEACHER'))
const selectedMemberIds = computed(() => new Set(form.memberProfileIds))

watch(() => form.leaderProfileId, (leaderId) => {
  if (leaderId && !form.memberProfileIds.includes(leaderId)) form.memberProfileIds.push(leaderId)
})
watch(() => [...form.memberProfileIds], (ids) => {
  const allowed = new Set(ids)
  form.administratorProfileIds = form.administratorProfileIds.filter((id) => allowed.has(id))
}, { deep: true })

onMounted(async () => {
  try {
    members.value = await listProjectMemberOptions()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

function toggle(list, id, checked) {
  if (checked && !list.includes(id)) list.push(id)
  const index = list.indexOf(id)
  if (!checked && index >= 0) list.splice(index, 1)
}

function lines(value) {
  return value.split(/\n/).map((item) => item.trim()).filter(Boolean)
}

function tags(value) {
  return value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean)
}

async function submit() {
  saving.value = true
  errorMessage.value = ''
  try {
    const project = await createProject({
      ...form,
      advisorProfileId: form.advisorProfileId || null,
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      requiredSkillTags: tags(form.requiredSkillTagsText),
      stageGoals: lines(form.stageGoalsText),
      progressDescription: form.progressDescription || null,
      outcomes: form.outcomes || null,
      gitRepositoryUrl: form.gitRepositoryUrl || null,
      documentUrl: form.documentUrl || null,
      requiredSkillTagsText: undefined,
      stageGoalsText: undefined,
    })
    await router.push(`/projects/${project.id}`)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <PortalShell eyebrow="ADMIN / PROJECT CREATE" title="创建项目" description="建立项目团队、指定负责人和指导老师。负责人创建后可继续维护成员与项目管理员。">
    <RouterLink class="profile-back-link project-back-link" to="/projects"><ArrowLeft :size="16" aria-hidden="true" />返回项目列表</RouterLink>
    <div v-if="loading" class="portal-state">正在读取可选成员…</div>
    <div v-else-if="errorMessage && !members.length" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <form v-else class="project-form" @submit.prevent="submit">
      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

      <section class="project-form-section">
        <header><span>01</span><div><p>PROJECT IDENTITY</p><h2>项目与团队</h2></div></header>
        <div class="project-form-grid">
          <label>项目名称<input v-model.trim="form.projectName" required maxlength="160" placeholder="例如：空地协同巡检系统" /></label>
          <label>团队名称<input v-model.trim="form.teamName" required maxlength="120" placeholder="例如：AIR-GROUND / 02" /></label>
          <label>项目类型<select v-model="form.type"><option value="COMPETITION">竞赛</option><option value="RESEARCH">科研</option><option value="INTERNAL">内部项目</option><option value="OPEN_SOURCE">开源</option></select></label>
          <label>项目状态<select v-model="form.status"><option value="PLANNING">筹备中</option><option value="ACTIVE">进行中</option><option value="PAUSED">已暂停</option><option value="COMPLETED">已完成</option><option value="ARCHIVED">已归档</option></select></label>
          <label class="full">项目简介<textarea v-model.trim="form.description" required maxlength="5000" rows="5" placeholder="说明项目目标、场景与价值。"></textarea></label>
        </div>
      </section>

      <section class="project-form-section">
        <header><span>02</span><div><p>OWNERSHIP & TEAM</p><h2>负责人和成员</h2></div></header>
        <div class="project-form-grid">
          <label>项目负责人<select v-model="form.leaderProfileId" required><option value="" disabled>请选择负责人</option><option v-for="member in members" :key="member.id" :value="member.id">{{ member.name }}</option></select><small>负责人自动加入团队，可维护团队名称、成员和项目管理员。</small></label>
          <label>指导老师（可选）<select v-model="form.advisorProfileId"><option value="">暂不关联</option><option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">{{ teacher.name }}</option></select><small>指导老师独立于项目成员和项目管理员。</small></label>
          <fieldset class="full member-picker"><legend>团队成员</legend><div><label v-for="member in members" :key="member.id" class="member-pick"><input type="checkbox" :checked="selectedMemberIds.has(member.id)" :disabled="member.id === form.leaderProfileId" @change="toggle(form.memberProfileIds, member.id, $event.target.checked)" /><span class="mini-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.name.slice(0, 1) }}</b></span><span><strong>{{ member.name }}</strong><small>{{ member.skillTags.join(' · ') || '暂未设置标签' }}</small></span><Check v-if="selectedMemberIds.has(member.id)" :size="16" aria-hidden="true" /></label></div></fieldset>
          <fieldset class="full member-picker"><legend>项目管理员</legend><p>只能从已加入团队的成员中选择；项目管理员可编辑项目资料，但不能更换负责人或管理团队成员。</p><div><label v-for="member in members.filter((item) => selectedMemberIds.has(item.id))" :key="member.id" class="member-pick"><input type="checkbox" :checked="form.administratorProfileIds.includes(member.id)" @change="toggle(form.administratorProfileIds, member.id, $event.target.checked)" /><span class="mini-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.name.slice(0, 1) }}</b></span><span><strong>{{ member.name }}</strong><small>项目管理员</small></span></label></div></fieldset>
        </div>
      </section>

      <section class="project-form-section">
        <header><span>03</span><div><p>PLAN & OUTPUT</p><h2>计划与成果</h2></div></header>
        <div class="project-form-grid">
          <label>开始时间<input v-model="form.startDate" type="date" /></label><label>结束时间<input v-model="form.endDate" type="date" /></label>
          <label class="full">所需能力标签<input v-model.trim="form.requiredSkillTagsText" placeholder="无人机，ROS 2，计算机视觉" /><small>使用逗号分隔，最多 20 个。</small></label>
          <label class="full">阶段目标<textarea v-model="form.stageGoalsText" rows="5" placeholder="每行一个阶段目标"></textarea></label>
          <label class="full">当前进度<textarea v-model="form.progressDescription" rows="5" maxlength="10000" placeholder="记录当前阶段、阻塞点与下一步。"></textarea></label>
          <label class="full">项目成果<textarea v-model="form.outcomes" rows="5" maxlength="10000" placeholder="记录竞赛、论文、软件、设备或其他成果。"></textarea></label>
          <label>Git 仓库<input v-model.trim="form.gitRepositoryUrl" type="url" maxlength="500" placeholder="https://github.com/..." /></label>
          <label>文档链接<input v-model.trim="form.documentUrl" type="url" maxlength="500" placeholder="https://..." /></label>
          <label class="project-switch full"><input v-model="form.externallyVisible" type="checkbox" /><span><strong>在公开展示端展示此项目</strong><small>开启后，访客可看到项目资料、指导老师和团队成员，但看不到项目管理员等内部权限信息。</small></span></label>
        </div>
      </section>

      <footer class="project-form-submit"><span><UsersRound :size="18" aria-hidden="true" />创建后可在团队空间继续更新项目。</span><button class="portal-primary" type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '创建中…' : '创建项目团队' }}</button></footer>
    </form>
  </PortalShell>
</template>
