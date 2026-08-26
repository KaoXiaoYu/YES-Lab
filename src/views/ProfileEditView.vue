<script setup>
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import {
  ArrowLeft, Bold, Braces, Camera, Italic, List, ListOrdered, Quote, Redo2, Save,
  Strikethrough, Trash2, Undo2, Upload,
} from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PortalShell from '../components/PortalShell.vue'
import { deleteOwnAvatar, getOwnProfile, replaceOwnAvatar, updateOwnProfile } from '../services/authApi'

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
let redirectTimer

const editor = useEditor({
  extensions: [StarterKit],
  content: '<p>正在加载个人主页…</p>',
  editorProps: { attributes: { 'aria-label': '个人主页富文本内容', class: 'profile-editor-content' } },
})

onMounted(async () => {
  try {
    profile.value = await getOwnProfile()
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

async function saveProfile() {
  if (!editor.value) return
  saving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    profile.value = await updateOwnProfile({
      internalContact: form.internalContact || null,
      headline: form.headline || null,
      profileHtml: editor.value.getHTML(),
    })
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
