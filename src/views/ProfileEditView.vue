<script setup>
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import {
  ArrowDown, ArrowLeft, ArrowUp, Bold, Braces, Camera, Eye, Italic, List, ListOrdered, Quote, Redo2, Save,
  Strikethrough, Trash2, Undo2, Upload,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { deleteOwnAvatar, getOwnProfile, getOwnShowcase, replaceOwnAvatar, updateOwnProfile, updateOwnShowcase } from '../services/authApi'

const router = useRouter()
const profile = ref(null)
const loading = ref(true)
const saving = ref(false)
const avatarSaving = ref(false)
const avatarFile = ref(null)
const avatarPreview = ref('')
const avatarInput = ref(null)
const message = ref('')
const errorMessage = ref('')
const form = reactive({ avatarUrl: '', internalContact: '', headline: '' })
const showcase = ref({ projectOptions: [], achievementOptions: [], featuredProjectIds: [], featuredCompetitionIds: [] })
const selectedProjects = computed(() => orderedOptions(showcase.value.projectOptions, showcase.value.featuredProjectIds))
const selectedAchievements = computed(() => orderedOptions(showcase.value.achievementOptions, showcase.value.featuredCompetitionIds))
let redirectTimer

const editor = useEditor({
  extensions: [StarterKit],
  content: '<p>正在加载个人主页…</p>',
  editorProps: { attributes: { 'aria-label': '个人主页富文本内容', class: 'profile-editor-content' } },
})

onMounted(async () => {
  try {
    const [profileData, showcaseData] = await Promise.all([getOwnProfile(), getOwnShowcase()])
    profile.value = profileData
    showcase.value = showcaseData
    form.avatarUrl = profile.value.avatarUrl || ''
    form.internalContact = profile.value.internalContact || ''
    form.headline = profile.value.headline || ''
    editor.value?.commands.setContent(profile.value.profileHtml || '<p></p>')
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  window.clearTimeout(redirectTimer)
  releaseAvatarPreview()
  editor.value?.destroy()
})

function selectAvatar(event) {
  const file = event.target.files?.[0]
  errorMessage.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    errorMessage.value = '头像仅支持 JPG、PNG 或 WebP。'
    event.target.value = ''
    return
  }
  if (file.size > 4 * 1024 * 1024) {
    errorMessage.value = '头像图片不能超过 4MB。'
    event.target.value = ''
    return
  }
  releaseAvatarPreview()
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
}

async function uploadAvatar() {
  if (!avatarFile.value) return
  avatarSaving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    profile.value = await replaceOwnAvatar(avatarFile.value)
    form.avatarUrl = profile.value.avatarUrl || ''
    releaseAvatarPreview()
    avatarFile.value = null
    if (avatarInput.value) avatarInput.value.value = ''
    message.value = '头像已更新。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    avatarSaving.value = false
  }
}

async function removeAvatar() {
  if (!window.confirm('确定移除当前头像吗？')) return
  avatarSaving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    profile.value = await deleteOwnAvatar()
    form.avatarUrl = ''
    releaseAvatarPreview()
    avatarFile.value = null
    if (avatarInput.value) avatarInput.value.value = ''
    message.value = '头像已移除。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    avatarSaving.value = false
  }
}

function releaseAvatarPreview() {
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
}

function toggleShowcase(list, id, checked) {
  if (checked && !list.includes(id)) list.push(id)
  const index = list.indexOf(id)
  if (!checked && index >= 0) list.splice(index, 1)
}

function moveShowcase(list, index, direction) {
  const target = index + direction
  if (target < 0 || target >= list.length) return
  ;[list[index], list[target]] = [list[target], list[index]]
}

function orderedOptions(options, ids) {
  const byId = new Map(options.map((item) => [item.id, item]))
  return ids.map((id) => byId.get(id)).filter(Boolean)
}

async function saveProfile() {
  if (!editor.value) return
  saving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    const [profileData, showcaseData] = await Promise.all([
      updateOwnProfile({
        internalContact: form.internalContact || null,
        headline: form.headline || null,
        profileHtml: editor.value.getHTML(),
      }),
      updateOwnShowcase({
        featuredProjectIds: showcase.value.featuredProjectIds,
        featuredCompetitionIds: showcase.value.featuredCompetitionIds,
      }),
    ])
    profile.value = profileData
    showcase.value = showcaseData
    message.value = '个人主页已保存，即将返回主页。'
    redirectTimer = window.setTimeout(() => router.push('/profile'), 450)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function toggle(command) {
  const chain = editor.value?.chain().focus()
  if (chain) command(chain).run()
}
</script>

<template>
  <PortalShell eyebrow="MEMBER / PROFILE EDIT" title="编辑个人主页" description="修改头像、内部联系方式、主页标语和公开介绍；成员固定字段由管理员维护。">
    <div v-if="loading" class="portal-state">正在读取成员资料…</div>
    <div v-else-if="errorMessage && !profile" class="portal-state error" role="alert">{{ errorMessage }}</div>

    <section v-else-if="profile" class="profile-edit-card standalone-editor">
      <header>
        <div><p>PUBLIC PROFILE EDITOR</p><h2>主页内容</h2></div>
        <RouterLink class="profile-back-link" to="/profile"><ArrowLeft :size="17" aria-hidden="true" />取消并返回</RouterLink>
      </header>
      <div v-if="message" class="save-message" role="status">{{ message }}</div>
      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

      <section class="avatar-editor" aria-labelledby="avatar-editor-title">
        <span class="avatar-editor-preview"><img v-if="avatarPreview || form.avatarUrl" :src="avatarPreview || form.avatarUrl" alt="当前头像预览" /><Camera v-else :size="28" aria-hidden="true" /></span>
        <div><h3 id="avatar-editor-title">个人头像</h3><p>支持 JPG、PNG、WebP，文件不超过 4MB。建议使用清晰的正方形图片。</p>
          <div class="avatar-editor-actions">
            <label class="avatar-file-button"><Camera :size="17" aria-hidden="true" />选择图片<input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp" @change="selectAvatar" /></label>
            <button type="button" :disabled="!avatarFile || avatarSaving" @click="uploadAvatar"><Upload :size="17" aria-hidden="true" />{{ avatarSaving && avatarFile ? '上传中…' : '上传头像' }}</button>
            <button v-if="form.avatarUrl" class="danger" type="button" :disabled="avatarSaving" @click="removeAvatar"><Trash2 :size="17" aria-hidden="true" />移除头像</button>
          </div>
        </div>
      </section>

      <div class="profile-fields">
        <label>内部联系方式<input v-model.trim="form.internalContact" type="text" autocomplete="tel" placeholder="仅成员系统内部展示" /></label>
        <label class="full">主页标语<input v-model.trim="form.headline" type="text" maxlength="160" placeholder="用一句话描述你的研究兴趣" /></label>
      </div>

      <section class="profile-showcase-editor" aria-labelledby="profile-showcase-title">
        <header><div><p>PUBLIC RECORDS</p><h3 id="profile-showcase-title">主页展示内容</h3></div><span><Eye :size="16" aria-hidden="true" />只显示已公开项目和审核通过的奖项</span></header>
        <div class="profile-showcase-grid">
          <article><h4>展示项目</h4><p>勾选本人参与或指导的公开项目，再调整顺序。</p><div class="profile-showcase-options"><label v-for="item in showcase.projectOptions" :key="item.id"><input type="checkbox" :checked="showcase.featuredProjectIds.includes(item.id)" @change="toggleShowcase(showcase.featuredProjectIds, item.id, $event.target.checked)" /><span><strong>{{ item.title }}</strong><small>{{ item.subtitle }}</small></span></label><p v-if="!showcase.projectOptions.length" class="empty-note">暂无可公开展示的关联项目。</p></div><ol class="profile-showcase-order"><li v-for="(item, index) in selectedProjects" :key="item.id"><span>{{ index + 1 }}</span><strong>{{ item.title }}</strong><button type="button" :disabled="index === 0" :aria-label="`上移项目：${item.title}`" @click="moveShowcase(showcase.featuredProjectIds, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === selectedProjects.length - 1" :aria-label="`下移项目：${item.title}`" @click="moveShowcase(showcase.featuredProjectIds, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button></li></ol></article>
          <article><h4>展示奖项</h4><p>仅列出本人关联且已经管理员认证的比赛成果。</p><div class="profile-showcase-options"><label v-for="item in showcase.achievementOptions" :key="item.id"><input type="checkbox" :checked="showcase.featuredCompetitionIds.includes(item.id)" @change="toggleShowcase(showcase.featuredCompetitionIds, item.id, $event.target.checked)" /><span><strong>{{ item.title }}</strong><small>{{ item.subtitle }}</small></span></label><p v-if="!showcase.achievementOptions.length" class="empty-note">暂无审核通过的关联奖项。</p></div><ol class="profile-showcase-order"><li v-for="(item, index) in selectedAchievements" :key="item.id"><span>{{ index + 1 }}</span><strong>{{ item.title }}</strong><button type="button" :disabled="index === 0" :aria-label="`上移奖项：${item.title}`" @click="moveShowcase(showcase.featuredCompetitionIds, index, -1)"><ArrowUp :size="15" aria-hidden="true" /></button><button type="button" :disabled="index === selectedAchievements.length - 1" :aria-label="`下移奖项：${item.title}`" @click="moveShowcase(showcase.featuredCompetitionIds, index, 1)"><ArrowDown :size="15" aria-hidden="true" /></button></li></ol></article>
        </div>
      </section>

      <div class="editor-shell">
        <div v-if="editor" class="editor-toolbar" role="toolbar" aria-label="富文本格式工具">
          <button type="button" :aria-pressed="editor.isActive('bold')" aria-label="粗体" @click="toggle(chain => chain.toggleBold())"><Bold :size="17" aria-hidden="true" /></button>
          <button type="button" :aria-pressed="editor.isActive('italic')" aria-label="斜体" @click="toggle(chain => chain.toggleItalic())"><Italic :size="17" aria-hidden="true" /></button>
          <button type="button" :aria-pressed="editor.isActive('strike')" aria-label="删除线" @click="toggle(chain => chain.toggleStrike())"><Strikethrough :size="17" aria-hidden="true" /></button>
          <span aria-hidden="true"></span>
          <button type="button" :aria-pressed="editor.isActive('heading', { level: 2 })" aria-label="二级标题" @click="toggle(chain => chain.toggleHeading({ level: 2 }))">H2</button>
          <button type="button" :aria-pressed="editor.isActive('heading', { level: 3 })" aria-label="三级标题" @click="toggle(chain => chain.toggleHeading({ level: 3 }))">H3</button>
          <button type="button" :aria-pressed="editor.isActive('bulletList')" aria-label="无序列表" @click="toggle(chain => chain.toggleBulletList())"><List :size="17" aria-hidden="true" /></button>
          <button type="button" :aria-pressed="editor.isActive('orderedList')" aria-label="有序列表" @click="toggle(chain => chain.toggleOrderedList())"><ListOrdered :size="17" aria-hidden="true" /></button>
          <button type="button" :aria-pressed="editor.isActive('blockquote')" aria-label="引用" @click="toggle(chain => chain.toggleBlockquote())"><Quote :size="17" aria-hidden="true" /></button>
          <button type="button" :aria-pressed="editor.isActive('codeBlock')" aria-label="代码块" @click="toggle(chain => chain.toggleCodeBlock())"><Braces :size="17" aria-hidden="true" /></button>
          <span aria-hidden="true"></span>
          <button type="button" aria-label="撤销" :disabled="!editor.can().chain().focus().undo().run()" @click="editor.chain().focus().undo().run()"><Undo2 :size="17" aria-hidden="true" /></button>
          <button type="button" aria-label="重做" :disabled="!editor.can().chain().focus().redo().run()" @click="editor.chain().focus().redo().run()"><Redo2 :size="17" aria-hidden="true" /></button>
        </div>
        <EditorContent :editor="editor" />
      </div>

      <div class="profile-save-row"><p>公开内容会在服务端再次进行 HTML 白名单清洗。</p><button type="button" :disabled="saving || avatarSaving" @click="saveProfile"><Save :size="18" aria-hidden="true" />{{ saving ? '保存中…' : '保存并返回主页' }}</button></div>
    </section>
  </PortalShell>
</template>
