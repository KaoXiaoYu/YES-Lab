<script setup>
import { brand } from '../config/site'
import { LogOut, Save, ShieldCheck } from '@lucide/vue'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { completeOwnQualification, getOwnProfile, logout } from '../services/authApi'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const fieldErrors = ref({})
const profile = ref(null)
const form = reactive({ memberCode: '', skillTagsText: '' })

onMounted(loadProfile)

async function loadProfile() {
  loading.value = true
  errorMessage.value = ''
  try {
    profile.value = await getOwnProfile()
    form.memberCode = profile.value.memberCode || ''
    form.skillTagsText = (profile.value.skillTags || []).join('、')
    if (profile.value.qualificationComplete) await leaveCompletionPage()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function splitTags(value) {
  return [
    ...new Set(
      value
        .split(/[、,，;；\n]/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  ]
}

async function submit() {
  fieldErrors.value = {}
  errorMessage.value = ''
  const memberCode = form.memberCode.trim()
  const skillTags = splitTags(form.skillTagsText)
  if (!/^[A-Za-z0-9._-]{2,64}$/.test(memberCode)) {
    fieldErrors.value.memberCode = '请输入 2—64 位字母、数字、点、下划线或短横线。'
    return
  }
  if (skillTags.length < 1 || skillTags.length > 12) {
    fieldErrors.value.skillTagsText = '请填写 1—12 个能力标签，可用顿号或逗号分隔。'
    return
  }
  if (skillTags.some((tag) => tag.length > 80)) {
    fieldErrors.value.skillTagsText = '每个能力标签不能超过 80 个字符。'
    return
  }

  saving.value = true
  try {
    await completeOwnQualification({ memberCode, skillTags })
    await leaveCompletionPage()
  } catch (error) {
    fieldErrors.value = error.fields || {}
    errorMessage.value = Object.keys(fieldErrors.value).length ? '' : error.message
  } finally {
    saving.value = false
  }
}

async function leaveCompletionPage() {
  const destination = safeRedirect(route.query.redirect) || '/profile'
  await router.replace(destination)
}

function safeRedirect(candidate) {
  return typeof candidate === 'string' &&
    candidate.startsWith('/') &&
    !candidate.startsWith('//') &&
    !candidate.startsWith('/complete-profile')
    ? candidate
    : null
}

async function signOut() {
  try {
    await logout()
  } finally {
    await router.replace('/login')
  }
}
</script>

<template>
  <main class="profile-completion-page">
    <section class="profile-completion-card" aria-labelledby="profile-completion-title">
      <header class="profile-completion-heading">
        <span class="profile-completion-icon"><ShieldCheck :size="23" aria-hidden="true" /></span>
        <div>
          <p>MEMBER PROFILE</p>
          <h1 id="profile-completion-title">完善成员资料</h1>
        </div>
      </header>

      <p class="profile-completion-intro">
        欢迎加入 {{ brand.name }}。继续使用成员系统前，请补全学号 /
        内部编号和能力标签；编号需全站唯一，提交成功后即可进入你原本要访问的页面。
      </p>

      <div v-if="loading" class="portal-state" role="status">正在读取成员资料…</div>
      <div v-else-if="!profile && errorMessage" class="profile-completion-error" role="alert">
        <p>{{ errorMessage }}</p>
        <button type="button" class="portal-secondary" @click="loadProfile">重试</button>
      </div>
      <form v-else class="profile-completion-form" @submit.prevent="submit" novalidate>
        <label for="completion-member-code">学号 / 内部编号 <span aria-hidden="true">*</span></label>
        <input
          id="completion-member-code"
          v-model="form.memberCode"
          type="text"
          autocomplete="off"
          minlength="2"
          maxlength="64"
          required
          :aria-invalid="Boolean(fieldErrors.memberCode)"
          :aria-describedby="fieldErrors.memberCode ? 'completion-member-code-error' : 'completion-member-code-help'"
        />
        <small id="completion-member-code-help">支持 2—64 位字母、数字、点、下划线和短横线。</small>
        <small v-if="fieldErrors.memberCode" id="completion-member-code-error" class="field-error" role="alert">{{
          fieldErrors.memberCode
        }}</small>

        <label for="completion-skill-tags">能力标签 <span aria-hidden="true">*</span></label>
        <textarea
          id="completion-skill-tags"
          v-model="form.skillTagsText"
          rows="3"
          maxlength="1000"
          required
          placeholder="例如：嵌入式、ROS、计算机视觉"
          :aria-invalid="Boolean(fieldErrors.skillTagsText)"
          :aria-describedby="fieldErrors.skillTagsText ? 'completion-skill-tags-error' : 'completion-skill-tags-help'"
        />
        <small id="completion-skill-tags-help">至少填写 1 项，最多 12 项；多个标签用顿号或逗号分隔。</small>
        <small v-if="fieldErrors.skillTagsText" id="completion-skill-tags-error" class="field-error" role="alert">{{
          fieldErrors.skillTagsText
        }}</small>

        <p v-if="errorMessage" class="profile-completion-error" role="alert">{{ errorMessage }}</p>
        <div class="profile-completion-actions">
          <button class="portal-primary" type="submit" :disabled="saving">
            <Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存并继续' }}
          </button>
          <button class="portal-secondary" type="button" :disabled="saving" @click="signOut">
            <LogOut :size="17" aria-hidden="true" />退出登录
          </button>
        </div>
      </form>
    </section>
  </main>
</template>
