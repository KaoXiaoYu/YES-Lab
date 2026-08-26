<script setup>
import { CalendarDays, ExternalLink, FileText, GitBranch, ImageUp, Pencil, Save, Settings2, ShieldCheck, Target, UserRound, UsersRound, X } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import ProjectCoverImage from '../components/ProjectCoverImage.vue'
import { authState, getProject, listProjectMemberOptions, replaceProjectCover, updateProject, updateProjectTeam } from '../services/authApi'

const route = useRoute()
const project = ref(null)
const memberOptions = ref([])
const loading = ref(true)
const saving = ref(false)
const coverSaving = ref(false)
const coverFile = ref(null)
const coverPreviewUrl = ref('')
const coverInput = ref(null)
const coverError = ref('')
const editMode = ref(null)
const editorClose = ref(null)
const errorMessage = ref('')
const message = ref('')
const detailForm = reactive({})
const teamForm = reactive({})

const typeLabels = { COMPETITION: '竞赛', RESEARCH: '科研', INTERNAL: '内部项目', OPEN_SOURCE: '开源' }
const statusLabels = { PLANNING: '筹备中', ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', ARCHIVED: '已归档' }
const roleLabels = { TEACHER: '指导教师', CORE_STUDENT: '核心成员', MEMBER: '普通成员' }
const teachers = computed(() => memberOptions.value.filter((member) => member.role === 'TEACHER'))
const selectedMemberIds = computed(() => new Set(teamForm.memberProfileIds || []))
const administratorIds = computed(() => new Set(project.value?.administrators.map((member) => member.id) || []))

onMounted(load)

watch(editMode, async (mode) => {
  document.body.classList.toggle('modal-open', Boolean(mode))
  if (mode) {
    await nextTick()
    editorClose.value?.focus()
  } else {
    resetCoverSelection()
  }
})

window.addEventListener('keydown', closeOnEscape)
onBeforeUnmount(() => {
  window.removeEventListener('keydown', closeOnEscape)
  document.body.classList.remove('modal-open')
  resetCoverSelection()
})

async function load() {
  try {
    const [projectData, options] = await Promise.all([getProject(route.params.projectId), listProjectMemberOptions()])
    project.value = projectData
    memberOptions.value = options
    fillForms()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function fillForms() {
  Object.assign(detailForm, {
    projectName: project.value.projectName, description: project.value.description,
    type: project.value.type, status: project.value.status, advisorProfileId: project.value.advisor?.id || '',
    requiredSkillTagsText: project.value.requiredSkillTags.join('，'), startDate: project.value.startDate || '', endDate: project.value.endDate || '',
    stageGoalsText: project.value.stageGoals.join('\n'), progressDescription: project.value.progressDescription || '', outcomes: project.value.outcomes || '',
    gitRepositoryUrl: project.value.gitRepositoryUrl || '', documentUrl: project.value.documentUrl || '', externallyVisible: project.value.externallyVisible,
  })
  Object.assign(teamForm, {
    teamName: project.value.teamName, leaderProfileId: project.value.leader.id,
    memberProfileIds: project.value.members.map((member) => member.id), administratorProfileIds: project.value.administrators.map((member) => member.id),
  })
}

function openEditor(mode) {
  fillForms()
  message.value = ''
  errorMessage.value = ''
  coverError.value = ''
  editMode.value = mode
}

function toggle(list, id, checked) {
  if (checked && !list.includes(id)) list.push(id)
  const index = list.indexOf(id)
  if (!checked && index >= 0) list.splice(index, 1)
  teamForm.administratorProfileIds = teamForm.administratorProfileIds.filter((item) => teamForm.memberProfileIds.includes(item))
}

function closeOnEscape(event) {
  if (event.key === 'Escape' && editMode.value) editMode.value = null
}

function splitTags(value) { return value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean) }
function splitLines(value) { return value.split(/\n/).map((item) => item.trim()).filter(Boolean) }

function selectCover(event) {
  const file = event.target.files?.[0]
  resetCoverSelection()
  coverError.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    coverError.value = '项目主图仅支持 JPG、PNG 或 WebP。'
    event.target.value = ''
    return
  }
  if (file.size > 8 * 1024 * 1024) {
    coverError.value = '项目主图不能超过 8MB。'
    event.target.value = ''
    return
  }
  coverFile.value = file
  coverPreviewUrl.value = URL.createObjectURL(file)
}

function resetCoverSelection() {
  if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
  coverPreviewUrl.value = ''
  coverFile.value = null
  if (coverInput.value) coverInput.value.value = ''
}

async function uploadCover() {
  if (!coverFile.value) {
    coverError.value = '请先选择一张项目主图。'
    return
  }
  coverSaving.value = true
  coverError.value = ''
  try {
    project.value = await replaceProjectCover(project.value.id, coverFile.value)
    message.value = '项目主图已更新。'
    resetCoverSelection()
  } catch (error) {
    coverError.value = error.message
  } finally {
    coverSaving.value = false
  }
}

async function saveDetails() {
  saving.value = true
  errorMessage.value = ''
  try {
    project.value = await updateProject(project.value.id, {
      projectName: detailForm.projectName, description: detailForm.description, type: detailForm.type, status: detailForm.status,
      advisorProfileId: detailForm.advisorProfileId || null, requiredSkillTags: splitTags(detailForm.requiredSkillTagsText),
      startDate: detailForm.startDate || null, endDate: detailForm.endDate || null, stageGoals: splitLines(detailForm.stageGoalsText),
      progressDescription: detailForm.progressDescription || null, outcomes: detailForm.outcomes || null,
      gitRepositoryUrl: detailForm.gitRepositoryUrl || null, documentUrl: detailForm.documentUrl || null, externallyVisible: detailForm.externallyVisible,
    })
    editMode.value = null
    message.value = '项目资料已更新。'
    fillForms()
  } catch (error) { errorMessage.value = error.message } finally { saving.value = false }
}

async function saveTeam() {
  saving.value = true
  errorMessage.value = ''
  try {
    project.value = await updateProjectTeam(project.value.id, { ...teamForm })
    editMode.value = null
    message.value = '团队成员与角色已更新。'
    fillForms()
  } catch (error) { errorMessage.value = error.message } finally { saving.value = false }
}
</script>

<template>
  <PortalShell eyebrow="PROJECT / TEAM SPACE" :title="project?.teamName || '项目团队空间'" description="项目资料、角色与成员的统一协作入口。当前版本不包含即时聊天。">
    <div v-if="loading" class="portal-state">正在进入团队空间…</div>
    <div v-else-if="!project" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <template v-else>
      <div v-if="message" class="save-message" role="status">{{ message }}</div>
      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

      <header class="project-workspace-head">
        <div class="project-workspace-cover"><ProjectCoverImage :key="project.updatedAt" :cover-url="project.coverImageUrl" :alt="`${project.projectName}项目主图`" /></div>
        <div class="project-workspace-copy"><span>{{ typeLabels[project.type] }}</span><b :data-status="project.status">{{ statusLabels[project.status] }}</b><h2>{{ project.projectName }}</h2><p>{{ project.description }}</p></div>
        <div class="project-workspace-actions"><button v-if="project.canEditProject" type="button" @click="openEditor('details')"><Pencil :size="16" aria-hidden="true" />编辑项目资料</button><button v-if="project.canManageTeam" type="button" @click="openEditor('team')"><Settings2 :size="16" aria-hidden="true" />管理团队</button></div>
      </header>

      <div class="project-workspace-layout">
        <aside class="project-roster">
          <header><p>TEAM ROSTER</p><h2>团队成员</h2><span>{{ project.members.length }}</span></header>
          <section><p>负责人</p><div class="project-person"><span class="mini-avatar"><img v-if="project.leader.avatarUrl" :src="project.leader.avatarUrl" alt="" /><b v-else>{{ project.leader.name.slice(0, 1) }}</b></span><div><strong>{{ project.leader.name }}</strong><small>{{ roleLabels[project.leader.role] }}</small></div><UserRound :size="17" aria-hidden="true" /></div></section>
          <section><p>指导老师</p><div v-if="project.advisor" class="project-person"><span class="mini-avatar"><img v-if="project.advisor.avatarUrl" :src="project.advisor.avatarUrl" alt="" /><b v-else>{{ project.advisor.name.slice(0, 1) }}</b></span><div><strong>{{ project.advisor.name }}</strong><small>项目指导老师</small></div><ShieldCheck :size="17" aria-hidden="true" /></div><span v-else class="project-roster-empty">暂未关联指导老师</span></section>
          <section><p>项目管理员</p><div v-for="member in project.administrators" :key="member.id" class="project-person"><span class="mini-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.name.slice(0, 1) }}</b></span><div><strong>{{ member.name }}</strong><small>{{ roleLabels[member.role] }}</small></div><Settings2 :size="16" aria-hidden="true" /></div><span v-if="!project.administrators.length" class="project-roster-empty">暂无项目管理员</span></section>
          <section><p>全部成员</p><div v-for="member in project.members" :key="member.id" class="project-person"><span class="mini-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.name.slice(0, 1) }}</b></span><div><strong>{{ member.name }}</strong><small>{{ administratorIds.has(member.id) ? '项目管理员' : roleLabels[member.role] }}</small></div></div></section>
        </aside>

        <section class="project-workspace-main">
          <section class="project-metrics"><article><CalendarDays :size="19" aria-hidden="true" /><span>项目周期</span><strong>{{ project.startDate || '待定' }} — {{ project.endDate || '长期' }}</strong></article><article><UsersRound :size="19" aria-hidden="true" /><span>团队规模</span><strong>{{ project.members.length }} 名成员</strong></article><article><Target :size="19" aria-hidden="true" /><span>公开展示</span><strong>{{ project.externallyVisible ? '已开启' : '仅内部' }}</strong></article></section>
          <section class="project-content-card"><header><p>REQUIRED CAPABILITIES</p><h2>所需能力</h2></header><ul class="portal-project-tag-list"><li v-for="tag in project.requiredSkillTags" :key="tag">{{ tag }}</li><li v-if="!project.requiredSkillTags.length">暂未设置</li></ul></section>
          <section class="project-content-card"><header><p>MILESTONES</p><h2>阶段目标</h2></header><ol class="project-goals"><li v-for="(goal, index) in project.stageGoals" :key="goal"><span>{{ String(index + 1).padStart(2, '0') }}</span><p>{{ goal }}</p></li></ol><p v-if="!project.stageGoals.length" class="project-content-empty">暂未设置阶段目标。</p></section>
          <section class="project-content-split"><article class="project-content-card"><header><p>PROGRESS</p><h2>进度说明</h2></header><div>{{ project.progressDescription || '暂未更新进度。' }}</div></article><article class="project-content-card"><header><p>OUTCOMES</p><h2>项目成果</h2></header><div>{{ project.outcomes || '暂未登记成果。' }}</div></article></section>
          <section class="project-links"><a v-if="project.gitRepositoryUrl" :href="project.gitRepositoryUrl" target="_blank" rel="noopener noreferrer"><GitBranch :size="20" aria-hidden="true" /><span><small>SOURCE</small><strong>Git 仓库</strong></span><ExternalLink :size="16" aria-hidden="true" /></a><a v-if="project.documentUrl" :href="project.documentUrl" target="_blank" rel="noopener noreferrer"><FileText :size="20" aria-hidden="true" /><span><small>DOCUMENTATION</small><strong>项目文档</strong></span><ExternalLink :size="16" aria-hidden="true" /></a></section>
        </section>
      </div>

      <div v-if="editMode" class="project-editor-overlay" role="dialog" aria-modal="true" :aria-labelledby="`${editMode}-editor-title`" @click.self="editMode = null">
        <section class="project-editor-panel">
          <header><div><p>{{ editMode === 'details' ? 'PROJECT DETAILS' : 'TEAM MANAGEMENT' }}</p><h2 :id="`${editMode}-editor-title`">{{ editMode === 'details' ? '编辑项目资料' : '管理项目团队' }}</h2></div><button ref="editorClose" type="button" aria-label="关闭编辑窗口" @click="editMode = null"><X :size="20" aria-hidden="true" /></button></header>
          <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
          <form v-if="editMode === 'details'" class="project-form-grid" @submit.prevent="saveDetails">
            <section class="project-cover-editor full" aria-labelledby="project-cover-editor-title">
              <div class="project-cover-editor-preview"><img v-if="coverPreviewUrl" :src="coverPreviewUrl" alt="待上传的项目主图预览" /><ProjectCoverImage v-else :key="project.updatedAt" :cover-url="project.coverImageUrl" :alt="`${project.projectName}当前项目主图`" /></div>
              <div><p id="project-cover-editor-title">PROJECT COVER</p><h3>项目主图</h3><span>推荐 16:10 横图，支持 JPG、PNG、WebP，文件不超过 8MB。未上传时显示 YES Lab 默认图。</span><label class="project-cover-file">选择图片<input ref="coverInput" type="file" accept="image/jpeg,image/png,image/webp" @change="selectCover" /></label><small v-if="coverFile">已选择：{{ coverFile.name }}</small><small v-if="coverError" class="project-cover-error" role="alert">{{ coverError }}</small><button type="button" :disabled="!coverFile || coverSaving" @click="uploadCover"><ImageUp :size="16" aria-hidden="true" />{{ coverSaving ? '上传中…' : '上传项目主图' }}</button></div>
            </section>
            <label class="full">项目名称<input v-model.trim="detailForm.projectName" required maxlength="160" /></label><label>类型<select v-model="detailForm.type"><option v-for="(label, value) in typeLabels" :key="value" :value="value">{{ label }}</option></select></label><label>状态<select v-model="detailForm.status"><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label>
            <label class="full">项目简介<textarea v-model.trim="detailForm.description" required rows="5" maxlength="5000"></textarea></label>
            <label>指导老师（可选）<select v-model="detailForm.advisorProfileId"><option value="">暂不关联</option><option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">{{ teacher.name }}</option></select></label><label>所需能力标签<input v-model.trim="detailForm.requiredSkillTagsText" placeholder="使用逗号分隔" /></label>
            <label>开始时间<input v-model="detailForm.startDate" type="date" /></label><label>结束时间<input v-model="detailForm.endDate" type="date" /></label><label class="full">阶段目标<textarea v-model="detailForm.stageGoalsText" rows="5" placeholder="每行一个目标"></textarea></label><label class="full">进度说明<textarea v-model="detailForm.progressDescription" rows="5"></textarea></label><label class="full">项目成果<textarea v-model="detailForm.outcomes" rows="5"></textarea></label>
            <label>Git 仓库<input v-model.trim="detailForm.gitRepositoryUrl" type="url" /></label><label>文档链接<input v-model.trim="detailForm.documentUrl" type="url" /></label><label class="project-switch full"><input v-model="detailForm.externallyVisible" type="checkbox" /><span><strong>允许公开展示</strong><small>访客将能看到项目和团队公开资料。</small></span></label>
            <footer class="project-editor-submit full"><button class="portal-primary" type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存项目资料' }}</button></footer>
          </form>
          <form v-else class="project-form-grid" @submit.prevent="saveTeam">
            <label class="full">团队名称<input v-model.trim="teamForm.teamName" required maxlength="120" /></label><label class="full">项目负责人<select v-model="teamForm.leaderProfileId" :disabled="!authState.account?.systemAdmin"><option v-for="member in memberOptions" :key="member.id" :value="member.id">{{ member.name }}</option></select><small>{{ authState.account?.systemAdmin ? '系统管理员可更换负责人。' : '只有系统管理员可以更换负责人。' }}</small></label>
            <fieldset class="full member-picker"><legend>团队成员</legend><div><label v-for="member in memberOptions" :key="member.id" class="member-pick"><input type="checkbox" :checked="selectedMemberIds.has(member.id)" :disabled="member.id === teamForm.leaderProfileId" @change="toggle(teamForm.memberProfileIds, member.id, $event.target.checked)" /><span class="mini-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.name.slice(0, 1) }}</b></span><span><strong>{{ member.name }}</strong><small>{{ member.skillTags.join(' · ') || '暂未设置标签' }}</small></span></label></div></fieldset>
            <fieldset class="full member-picker"><legend>项目管理员</legend><p>项目管理员可以编辑项目资料；团队成员变更仍由负责人或系统管理员操作。</p><div><label v-for="member in memberOptions.filter((item) => selectedMemberIds.has(item.id))" :key="member.id" class="member-pick"><input type="checkbox" :checked="teamForm.administratorProfileIds.includes(member.id)" @change="toggle(teamForm.administratorProfileIds, member.id, $event.target.checked)" /><span class="mini-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" /><b v-else>{{ member.name.slice(0, 1) }}</b></span><span><strong>{{ member.name }}</strong><small>项目管理员</small></span></label></div></fieldset>
            <footer class="project-editor-submit full"><button class="portal-primary" type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存团队设置' }}</button></footer>
          </form>
        </section>
      </div>
    </template>
  </PortalShell>
</template>
