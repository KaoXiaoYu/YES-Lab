<script setup>
import { ArrowLeft, FileBadge, ImagePlus, Plus, Save, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { createCompetition, getCompetition, listCompetitionMemberOptions, listCompetitionProjectOptions, replaceCompetitionCertificate, replaceCompetitionImages, updateCompetition } from '../services/authApi'

const route = useRoute(); const router = useRouter(); const editing = computed(() => Boolean(route.params.competitionId))
const members = ref([]); const projects = ref([]); const existing = ref(null); const loading = ref(true); const saving = ref(false); const errorMessage = ref('')
const certificate = ref(null); const imageFiles = ref([]); const imageDescriptions = ref([])
const form = reactive({ name: '', track: '', level: 'PROVINCIAL', lifecycle: 'PLANNED', awardName: '', description: '', competitionDate: '', provincialDate: '', nationalDate: '', advisorProfileId: '', advisorName: '', projectId: '', participants: [{ displayName: '', linkedProfileId: '' }] })
const teachers = computed(() => members.value.filter((member) => member.role === 'TEACHER'))
const finished = computed(() => form.lifecycle === 'FINISHED')

watch(() => form.advisorProfileId, (id) => { const teacher = teachers.value.find((item) => item.id === id); if (teacher) form.advisorName = teacher.name })
onMounted(async () => {
  try {
    const requests = [listCompetitionMemberOptions(), listCompetitionProjectOptions()]
    if (editing.value) requests.push(getCompetition(route.params.competitionId))
    const [memberData, projectData, item] = await Promise.all(requests); members.value = memberData; projects.value = projectData
    if (item) { existing.value = item; Object.assign(form, { name: item.name, track: item.track || '', level: item.level, lifecycle: item.lifecycle, awardName: item.awardName || '', description: item.description, competitionDate: item.competitionDate || '', provincialDate: item.provincialDate || '', nationalDate: item.nationalDate || '', advisorProfileId: item.advisor?.id || '', advisorName: item.advisorName || '', projectId: item.project?.id || '', participants: item.participants.filter((p) => !p.captain).map((p) => ({ displayName: p.displayName, linkedProfileId: p.linkedProfileId || '' })) }); if (!form.participants.length) form.participants.push({ displayName: '', linkedProfileId: '' }) }
  } catch (error) { errorMessage.value = error.message } finally { loading.value = false }
})

function addParticipant() { if (form.participants.length < 49) form.participants.push({ displayName: '', linkedProfileId: '' }) }
function removeParticipant(index) { form.participants.splice(index, 1); if (!form.participants.length) addParticipant() }
function chooseMember(row) { const member = members.value.find((item) => item.id === row.linkedProfileId); if (member) row.displayName = member.name }
function chooseCertificate(event) { certificate.value = event.target.files?.[0] || null }
function chooseImages(event) { imageFiles.value = [...(event.target.files || [])].slice(0, 8); imageDescriptions.value = imageFiles.value.map((_, index) => imageDescriptions.value[index] || '') }
function payload() { return { ...form, competitionDate: form.competitionDate || null, provincialDate: form.provincialDate || null, nationalDate: form.nationalDate || null, advisorProfileId: form.advisorProfileId || null, advisorName: form.advisorName || null, projectId: form.projectId || null, awardName: form.awardName || null, track: form.track || null, participants: form.participants.filter((row) => row.displayName.trim() || row.linkedProfileId).map((row) => ({ displayName: row.displayName || '关联成员', linkedProfileId: row.linkedProfileId || null })), imageDescriptions: imageDescriptions.value } }

async function submit() {
  saving.value = true; errorMessage.value = ''
  try {
    let item
    if (!editing.value) item = await createCompetition(payload(), certificate.value, imageFiles.value)
    else {
      if (certificate.value) await replaceCompetitionCertificate(existing.value.id, certificate.value)
      item = await updateCompetition(existing.value.id, payload())
      if (imageFiles.value.length) item = await replaceCompetitionImages(existing.value.id, imageFiles.value, imageDescriptions.value)
    }
    await router.push('/competitions')
  } catch (error) { errorMessage.value = error.message } finally { saving.value = false }
}
</script>

<template>
  <PortalShell eyebrow="COMPETITION / CAPTAIN SUBMISSION" :title="editing ? '编辑比赛记录' : '队长提交比赛'" description="提交人将自动成为队长。队员既可关联实验室账号，也可只保留公开展示姓名。">
    <RouterLink class="profile-back-link achievement-back" to="/competitions"><ArrowLeft :size="16" aria-hidden="true" />返回比赛列表</RouterLink>
    <div v-if="loading" class="portal-state">正在准备比赛表单…</div>
    <form v-else class="competition-form" @submit.prevent="submit">
      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
      <section class="competition-form-section"><header><span>01</span><div><p>COMPETITION PROFILE</p><h2>比赛信息</h2></div></header><div class="competition-form-grid">
        <label>比赛名称<input v-model.trim="form.name" required maxlength="180" /></label><label>赛道 / 组别<input v-model.trim="form.track" maxlength="180" /></label>
        <label>比赛级别<select v-model="form.level"><option value="SCHOOL">校级</option><option value="PROVINCIAL">省级</option><option value="REGIONAL">赛区</option><option value="NATIONAL">国家级</option><option value="INTERNATIONAL">国际级</option><option value="OTHER">其他</option></select></label>
        <label>当前状态<select v-model="form.lifecycle"><option value="PLANNED">筹备中</option><option value="ONGOING">进行中</option><option value="FINISHED">已结束</option></select></label>
        <label class="full">比赛与成果描述<textarea v-model.trim="form.description" required maxlength="10000" rows="7" placeholder="介绍比赛背景、技术方案、团队分工、过程与成果。"></textarea></label>
        <template v-if="finished"><label>比赛 / 获奖日期<input v-model="form.competitionDate" type="date" required /></label><label>获奖结果<input v-model.trim="form.awardName" required maxlength="160" placeholder="例如：全国二等奖" /></label></template>
        <template v-else><label>省赛时间<input v-model="form.provincialDate" type="date" required /></label><label>国赛时间<input v-model="form.nationalDate" type="date" required /></label></template>
      </div></section>

      <section class="competition-form-section"><header><span>02</span><div><p>TEAM & RELATIONS</p><h2>队伍与关联</h2></div></header><div class="competition-form-grid">
        <label>指导老师账号{{ finished ? '（可选）' : '' }}<select v-model="form.advisorProfileId" :required="!finished && !form.advisorName"><option value="">不关联账号</option><option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">{{ teacher.name }}</option></select></label><label>指导老师展示姓名{{ finished ? '（可选）' : '' }}<input v-model.trim="form.advisorName" :required="!finished && !form.advisorProfileId" maxlength="80" /></label>
        <label class="full">关联项目（可选）<select v-model="form.projectId"><option value="">不关联项目</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }} · {{ project.teamName }}</option></select></label>
        <fieldset class="full participant-editor"><legend>比赛成员</legend><p>你会自动作为队长加入，无需重复填写。没有实验室账号的队员只填写展示姓名即可。</p><div v-for="(row, index) in form.participants" :key="index" class="participant-row"><label>关联成员<select v-model="row.linkedProfileId" @change="chooseMember(row)"><option value="">不关联账号</option><option v-for="member in members" :key="member.id" :value="member.id">{{ member.name }}</option></select></label><label>展示姓名<input v-model.trim="row.displayName" maxlength="80" /></label><button type="button" aria-label="移除该成员" @click="removeParticipant(index)"><Trash2 :size="17" aria-hidden="true" /></button></div><button class="achievement-secondary" type="button" @click="addParticipant"><Plus :size="16" aria-hidden="true" />增加成员</button></fieldset>
      </div></section>

      <section class="competition-form-section"><header><span>03</span><div><p>EVIDENCE & GALLERY</p><h2>证书与比赛图片</h2></div></header><div class="competition-form-grid">
        <label class="file-drop full"><FileBadge :size="24" aria-hidden="true" /><span><strong>证书文件{{ finished ? '（必填）' : '（可选）' }}</strong><small>PDF、JPG 或 PNG，最大 10MB。证书仅供队长和管理员审核查看，不在公开页直接展示。</small></span><input type="file" accept="application/pdf,image/jpeg,image/png" :required="finished && !existing?.hasCertificate" @change="chooseCertificate" /><b>{{ certificate?.name || existing?.certificateOriginalName || '选择文件' }}</b></label>
        <label class="file-drop full"><ImagePlus :size="24" aria-hidden="true" /><span><strong>比赛相关图片（可选，最多 8 张）</strong><small>JPG、PNG 或 WebP；审核通过后用于公开比赛详情页。</small></span><input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="chooseImages" /><b>{{ imageFiles.length ? `已选择 ${imageFiles.length} 张` : editing && existing?.images.length ? `保留现有 ${existing.images.length} 张` : '选择图片' }}</b></label>
        <div v-if="imageFiles.length" class="image-caption-list full"><label v-for="(file, index) in imageFiles" :key="`${file.name}-${index}`">图片 {{ index + 1 }}：{{ file.name }}<input v-model.trim="imageDescriptions[index]" maxlength="300" placeholder="填写图片说明（可选）" /></label></div>
      </div></section>
      <footer class="competition-submit"><p>已结束比赛提交后进入管理员审核；审核通过后，仅管理员可继续修改并设置首页排序。</p><button class="portal-primary" type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : editing ? '保存比赛记录' : '提交比赛记录' }}</button></footer>
    </form>
  </PortalShell>
</template>
