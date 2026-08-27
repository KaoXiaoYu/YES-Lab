<script setup>
import { ArrowLeft, FileBadge, ImagePlus, Plus, Save, Trash2 } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import SearchableMemberSelect from '../components/SearchableMemberSelect.vue'
import { createCompetition, deleteCompetitionImage, getAuthenticatedFile, getCompetition, listCompetitionMemberOptions, listCompetitionProjectOptions, replaceCompetitionCertificate, replaceCompetitionImages, updateCompetition } from '../services/authApi'

const route = useRoute(); const router = useRouter(); const editing = computed(() => Boolean(route.params.competitionId))
const members = ref([]); const projects = ref([]); const existing = ref(null); const loading = ref(true); const saving = ref(false); const errorMessage = ref('')
const certificate = ref(null); const imageFiles = ref([]); const imageDescriptions = ref([])
const certificateError = ref(''); const imageError = ref(''); const deletingImageId = ref(null); const existingImageUrls = ref({})
const certificateInput = ref(null); const imagesInput = ref(null); const certificatePreviewUrl = ref(''); const imagePreviewUrls = ref([])
let imageSelectionVersion = 0
const form = reactive({ name: '', track: '', level: 'PROVINCIAL', lifecycle: 'PLANNED', awardName: '', description: '', competitionDate: '', provincialDate: '', nationalDate: '', advisorProfileId: '', advisorName: '', projectId: '', participants: [{ displayName: '', linkedProfileId: '' }] })
const teachers = computed(() => members.value.filter((member) => member.role === 'TEACHER'))
const finished = computed(() => form.lifecycle === 'FINISHED')

watch(() => form.advisorProfileId, (id) => { const teacher = teachers.value.find((item) => item.id === id); if (teacher) form.advisorName = teacher.name })
onMounted(async () => {
  try {
    const requests = [listCompetitionMemberOptions(), listCompetitionProjectOptions()]
    if (editing.value) requests.push(getCompetition(route.params.competitionId))
    const [memberData, projectData, item] = await Promise.all(requests); members.value = memberData; projects.value = projectData
    if (item) { existing.value = item; Object.assign(form, { name: item.name, track: item.track || '', level: item.level, lifecycle: item.lifecycle, awardName: item.awardName || '', description: item.description, competitionDate: item.competitionDate || '', provincialDate: item.provincialDate || '', nationalDate: item.nationalDate || '', advisorProfileId: item.advisor?.id || '', advisorName: item.advisorName || '', projectId: item.project?.id || '', participants: item.participants.filter((p) => !p.captain).map((p) => ({ displayName: p.displayName, linkedProfileId: p.linkedProfileId || '' })) }); if (!form.participants.length) form.participants.push({ displayName: '', linkedProfileId: '' }); await loadExistingImages(item) }
  } catch (error) { errorMessage.value = error.message } finally { loading.value = false }
})

function addParticipant() { if (form.participants.length < 49) form.participants.push({ displayName: '', linkedProfileId: '' }) }
function removeParticipant(index) { form.participants.splice(index, 1); if (!form.participants.length) addParticipant() }
function chooseMember(row) { const member = members.value.find((item) => item.id === row.linkedProfileId); if (member) row.displayName = member.name }
async function chooseCertificate(event) {
  certificateError.value = ''
  const file = event.target.files?.[0] || null
  clearCertificatePreview()
  if (!file) { certificate.value = null; return }
  const extension = file.name.split('.').pop()?.toLocaleLowerCase()
  if (!['pdf', 'jpg', 'jpeg', 'png'].includes(extension || '')) {
    certificateError.value = '证书仅支持 PDF、JPG、JPEG 或 PNG。'
    event.target.value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    certificateError.value = '证书文件不能超过 10MB。'
    event.target.value = ''
    return
  }
  certificate.value = file
  certificatePreviewUrl.value = await createLocalPreviewUrl(file)
}
async function chooseImages(event) {
  const selectionVersion = ++imageSelectionVersion
  imageError.value = ''
  const files = [...(event.target.files || [])]
  if (files.length > 8) imageError.value = '比赛图片最多选择 8 张。'
  const selected = files.slice(0, 8)
  const invalid = selected.find((file) => !['jpg', 'jpeg', 'png', 'webp'].includes(file.name.split('.').pop()?.toLocaleLowerCase() || ''))
  const oversized = selected.find((file) => file.size > 8 * 1024 * 1024)
  if (invalid) imageError.value = `图片 ${invalid.name} 格式不支持，请使用 JPG、PNG 或 WebP。`
  else if (oversized) imageError.value = `图片 ${oversized.name} 超过 8MB。`
  if (imageError.value) {
    clearImageSelection()
    event.target.value = ''
    return
  }
  clearImagePreviews()
  const previews = await Promise.all(selected.map(createLocalPreviewUrl))
  if (selectionVersion !== imageSelectionVersion) {
    previews.forEach((url) => URL.revokeObjectURL(url))
    return
  }
  imageFiles.value = selected
  imageDescriptions.value = selected.map((_, index) => imageDescriptions.value[index] || '')
  imagePreviewUrls.value = previews
}

async function createLocalPreviewUrl(file) {
  if (file.type === 'application/pdf' || file.name.toLocaleLowerCase().endsWith('.pdf')) return URL.createObjectURL(file)
  try {
    const signature = new Uint8Array(await file.slice(0, 2).arrayBuffer())
    if (signature[0] === 0x42 && signature[1] === 0x4d) {
      return URL.createObjectURL(new Blob([file], { type: 'image/bmp' }))
    }
  } catch { /* 无法读取签名时仍尝试浏览器原生预览 */ }
  return URL.createObjectURL(file)
}

function clearCertificatePreview() {
  if (certificatePreviewUrl.value) URL.revokeObjectURL(certificatePreviewUrl.value)
  certificatePreviewUrl.value = ''
}

function clearCertificateSelection() {
  certificate.value = null
  certificateError.value = ''
  clearCertificatePreview()
  if (certificateInput.value) certificateInput.value.value = ''
}

function clearImagePreviews() {
  imagePreviewUrls.value.forEach((url) => URL.revokeObjectURL(url))
  imagePreviewUrls.value = []
}

function clearImageSelection() {
  imageSelectionVersion += 1
  clearImagePreviews()
  imageFiles.value = []
  imageDescriptions.value = []
  if (imagesInput.value) imagesInput.value.value = ''
}

function removeSelectedImage(index) {
  URL.revokeObjectURL(imagePreviewUrls.value[index])
  imageFiles.value.splice(index, 1)
  imageDescriptions.value.splice(index, 1)
  imagePreviewUrls.value.splice(index, 1)
  if (!imageFiles.value.length && imagesInput.value) imagesInput.value.value = ''
}

async function loadExistingImages(item) {
  const loaded = {}
  await Promise.all((item.images || []).map(async (image) => {
    try { loaded[image.id] = await getAuthenticatedFile(image.url) } catch { /* 缩略图失败不影响编辑与删除 */ }
  }))
  existingImageUrls.value = loaded
}

async function removeExistingImage(image) {
  if (!window.confirm(`确定删除这张比赛图片吗？\n${image.description}`)) return
  deletingImageId.value = image.id
  imageError.value = ''
  try {
    const updated = await deleteCompetitionImage(existing.value.id, image.id)
    const url = existingImageUrls.value[image.id]
    if (url) URL.revokeObjectURL(url)
    const nextUrls = { ...existingImageUrls.value }
    delete nextUrls[image.id]
    existingImageUrls.value = nextUrls
    existing.value = updated
  } catch (error) { imageError.value = error.message } finally { deletingImageId.value = null }
}

onBeforeUnmount(() => {
  Object.values(existingImageUrls.value).forEach((url) => URL.revokeObjectURL(url))
  clearCertificatePreview()
  clearImagePreviews()
})
function payload() { return { ...form, competitionDate: form.competitionDate || null, provincialDate: form.provincialDate || null, nationalDate: form.nationalDate || null, advisorProfileId: form.advisorProfileId || null, advisorName: form.advisorName || null, projectId: form.projectId || null, awardName: form.awardName || null, track: form.track || null, participants: form.participants.filter((row) => row.displayName.trim() || row.linkedProfileId).map((row) => ({ displayName: row.displayName || '关联成员', linkedProfileId: row.linkedProfileId || null })), imageDescriptions: imageDescriptions.value } }

async function submit() {
  saving.value = true; errorMessage.value = ''
  try {
    let item
    if (!editing.value) item = await createCompetition(payload(), certificate.value, imageFiles.value)
    else {
      if (certificate.value) item = await replaceCompetitionCertificate(existing.value.id, certificate.value)
      item = await updateCompetition(existing.value.id, payload())
      if (imageFiles.value.length) item = await replaceCompetitionImages(existing.value.id, imageFiles.value, imageDescriptions.value)
    }
    if (certificate.value && !item?.hasCertificate) throw new Error('后端未确认收到证书，请不要重复提交并联系管理员检查服务日志。')
    if (imageFiles.value.length && item?.images?.length !== imageFiles.value.length) throw new Error('后端返回的比赛图片数量与本次上传不一致，请不要重复提交。')
    window.dispatchEvent(new Event('yeslab:competitions-changed'))
    await router.push({ path: '/competitions', query: {
      saved: '1',
      ...(certificate.value ? { certificate: '1' } : {}),
      ...(imageFiles.value.length ? { images: String(imageFiles.value.length) } : {}),
    } })
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
        <SearchableMemberSelect v-model="form.advisorProfileId" :options="teachers" :label="`指导老师账号${finished ? '（可选）' : ''}`" empty-label="不关联账号" :required="!finished && !form.advisorName" /><label>指导老师展示姓名{{ finished ? '（可选）' : '' }}<input v-model.trim="form.advisorName" :required="!finished && !form.advisorProfileId" maxlength="80" /></label>
        <label class="full">关联项目（可选）<select v-model="form.projectId"><option value="">不关联项目</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }} · {{ project.teamName }}</option></select></label>
        <fieldset class="full participant-editor"><legend>比赛成员</legend><p>你会自动作为队长加入，无需重复填写。没有实验室账号的队员只填写展示姓名即可。</p><div v-for="(row, index) in form.participants" :key="index" class="participant-row"><SearchableMemberSelect v-model="row.linkedProfileId" :options="members" label="关联成员" empty-label="不关联账号" @change="chooseMember(row)" /><label>展示姓名<input v-model.trim="row.displayName" maxlength="80" /></label><button type="button" aria-label="移除该成员" @click="removeParticipant(index)"><Trash2 :size="17" aria-hidden="true" /></button></div><button class="achievement-secondary" type="button" @click="addParticipant"><Plus :size="16" aria-hidden="true" />增加成员</button></fieldset>
      </div></section>

      <section class="competition-form-section"><header><span>03</span><div><p>EVIDENCE & GALLERY</p><h2>证书与比赛图片</h2></div></header><div class="competition-form-grid">
        <label class="file-drop full"><FileBadge :size="24" aria-hidden="true" /><span><strong>证书文件{{ finished ? '（必填）' : '（可选）' }}</strong><small>PDF、JPG、JPEG 或 PNG，最大 10MB；管理员审核通过后可在公开详情查看。</small></span><input ref="certificateInput" type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" :required="finished && !existing?.hasCertificate" @change="chooseCertificate" /><b>{{ certificate?.name || existing?.certificateOriginalName || '选择文件' }}</b><small v-if="certificateError" class="file-error" role="alert">{{ certificateError }}</small></label>
        <section v-if="certificate && certificatePreviewUrl" class="upload-preview-panel full" aria-labelledby="certificate-preview-title"><header><div><strong id="certificate-preview-title">证书本地预览</strong><small>当前仅在本机预览，点击保存且后端校验成功后才会上传。</small></div><button type="button" @click="clearCertificateSelection"><Trash2 :size="16" aria-hidden="true" />取消选择</button></header><figure><object v-if="certificate.type === 'application/pdf' || certificate.name.toLocaleLowerCase().endsWith('.pdf')" :data="certificatePreviewUrl" type="application/pdf" aria-label="待上传证书 PDF 预览"></object><img v-else :src="certificatePreviewUrl" :alt="`待上传证书预览：${certificate.name}`" /></figure></section>
        <label class="file-drop full"><ImagePlus :size="24" aria-hidden="true" /><span><strong>比赛相关图片（可选，最多 8 张）</strong><small>JPG、PNG 或 WebP，单张最大 8MB；审核通过后用于公开比赛详情页。</small></span><input ref="imagesInput" type="file" accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" multiple @change="chooseImages" /><b>{{ imageFiles.length ? `已选择 ${imageFiles.length} 张` : editing && existing?.images.length ? `保留现有 ${existing.images.length} 张` : '选择图片' }}</b><small v-if="imageError" class="file-error" role="alert">{{ imageError }}</small></label>
        <section v-if="editing && existing?.images.length" class="existing-image-gallery full" aria-labelledby="existing-gallery-title"><header><div><strong id="existing-gallery-title">现有比赛图集</strong><small>可单独删除；新选择一组图片并保存时，会替换剩余图集。</small></div><span>{{ existing.images.length }} / 8</span></header><div><article v-for="image in existing.images" :key="image.id"><div class="existing-image-preview"><img v-if="existingImageUrls[image.id]" :src="existingImageUrls[image.id]" :alt="image.description" /><ImagePlus v-else :size="24" aria-hidden="true" /></div><p>{{ image.description }}</p><button type="button" :disabled="deletingImageId === image.id" :aria-label="`删除图片：${image.description}`" @click="removeExistingImage(image)"><Trash2 :size="16" aria-hidden="true" />{{ deletingImageId === image.id ? '删除中…' : '删除' }}</button></article></div></section>
        <section v-if="imageFiles.length" class="selected-image-gallery full" aria-labelledby="selected-gallery-title"><header><div><strong id="selected-gallery-title">待上传图片预览</strong><small>这些图片尚未上传；你可以填写说明或移除单张图片。</small></div><span>{{ imageFiles.length }} / 8</span></header><div><article v-for="(file, index) in imageFiles" :key="`${file.name}-${file.lastModified}-${index}`"><img :src="imagePreviewUrls[index]" :alt="`待上传比赛图片 ${index + 1}：${file.name}`" /><label>图片 {{ index + 1 }}：{{ file.name }}<input v-model.trim="imageDescriptions[index]" maxlength="300" placeholder="填写图片说明（可选）" /></label><button type="button" :aria-label="`移除待上传图片：${file.name}`" @click="removeSelectedImage(index)"><Trash2 :size="16" aria-hidden="true" />移除</button></article></div></section>
      </div></section>
      <footer class="competition-submit"><p>已结束比赛提交后进入管理员审核；只有后端返回证书和图片记录后，本页面才会视为保存成功。</p><button class="portal-primary" type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '上传并由后端校验中…' : editing ? '保存比赛记录' : '提交比赛记录' }}</button></footer>
    </form>
  </PortalShell>
</template>
