<script setup>
import { Eye, EyeOff, KeyRound } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import MemberProfileDisplay from '../components/MemberProfileDisplay.vue'
import PortalShell from '../components/PortalShell.vue'
import { changeOwnPassword, getOwnProfile, logout } from '../services/authApi'

const router = useRouter()
const profile = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const passwordError = ref('')
const passwordFieldErrors = ref({})
const passwordSubmitting = ref(false)
const passwordVisibility = reactive({ current: false, next: false, confirm: false })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const canChangePassword = computed(() => Boolean(
  passwordForm.currentPassword
  && passwordForm.newPassword.length >= 6
  && passwordForm.newPassword.length <= 18
  && passwordForm.confirmPassword,
))

onMounted(async () => {
  try {
    profile.value = await getOwnProfile()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

async function submitPasswordChange() {
  passwordError.value = ''
  passwordFieldErrors.value = {}
  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 18) {
    passwordFieldErrors.value = { newPassword: '新密码长度需为 6—18 位。' }
    return
  }
  if (passwordForm.currentPassword === passwordForm.newPassword) {
    passwordFieldErrors.value = { newPassword: '新密码不能与当前密码相同。' }
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordFieldErrors.value = { confirmPassword: '两次输入的新密码不一致。' }
    return
  }

  passwordSubmitting.value = true
  try {
    await changeOwnPassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    await logout()
    await router.replace({ path: '/login', query: { passwordChanged: '1' } })
  } catch (error) {
    if (error.message === '当前密码不正确') passwordFieldErrors.value = { currentPassword: error.message }
    else if (Object.keys(error.fields || {}).length) passwordFieldErrors.value = error.fields
    else passwordError.value = error.message
  } finally {
    passwordSubmitting.value = false
  }
}
</script>

<template>
  <PortalShell eyebrow="MEMBER / PROFILE" title="个人主页" description="查看你的成员档案与公开主页；需要修改时再进入独立编辑页面。">
    <div v-if="loading" class="portal-state">正在读取成员资料…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <template v-else-if="profile">
      <MemberProfileDisplay :profile="profile" private-view editable />
      <section class="password-settings-card profile-password-card" aria-labelledby="profile-password-title">
        <header><div><p>ACCOUNT SECURITY</p><h2 id="profile-password-title">修改登录密码</h2></div><span>修改成功后会退出当前账号，并撤销其他设备的续期登录状态。</span></header>
        <div v-if="passwordError" class="form-alert" role="alert">{{ passwordError }}</div>
        <form class="password-settings-form" @submit.prevent="submitPasswordChange">
          <label>当前密码<div class="admin-password-input" :class="{ invalid: passwordFieldErrors.currentPassword }"><input v-model="passwordForm.currentPassword" :type="passwordVisibility.current ? 'text' : 'password'" autocomplete="current-password" required maxlength="72" :aria-invalid="Boolean(passwordFieldErrors.currentPassword)" :aria-describedby="passwordFieldErrors.currentPassword ? 'current-password-error' : undefined" /><button type="button" :aria-label="passwordVisibility.current ? '隐藏当前密码' : '显示当前密码'" @click="passwordVisibility.current = !passwordVisibility.current"><EyeOff v-if="passwordVisibility.current" :size="17" aria-hidden="true" /><Eye v-else :size="17" aria-hidden="true" /></button></div><small v-if="passwordFieldErrors.currentPassword" id="current-password-error" class="field-error">{{ passwordFieldErrors.currentPassword }}</small></label>
          <label>新密码<div class="admin-password-input" :class="{ invalid: passwordFieldErrors.newPassword }"><input v-model="passwordForm.newPassword" :type="passwordVisibility.next ? 'text' : 'password'" autocomplete="new-password" required minlength="6" maxlength="18" :aria-invalid="Boolean(passwordFieldErrors.newPassword)" :aria-describedby="['profile-password-help', passwordFieldErrors.newPassword ? 'new-password-error' : null].filter(Boolean).join(' ')" /><button type="button" :aria-label="passwordVisibility.next ? '隐藏新密码' : '显示新密码'" @click="passwordVisibility.next = !passwordVisibility.next"><EyeOff v-if="passwordVisibility.next" :size="17" aria-hidden="true" /><Eye v-else :size="17" aria-hidden="true" /></button></div><small id="profile-password-help">密码长度为 6—18 位。</small><small v-if="passwordFieldErrors.newPassword" id="new-password-error" class="field-error">{{ passwordFieldErrors.newPassword }}</small></label>
          <label>确认新密码<div class="admin-password-input" :class="{ invalid: passwordFieldErrors.confirmPassword }"><input v-model="passwordForm.confirmPassword" :type="passwordVisibility.confirm ? 'text' : 'password'" autocomplete="new-password" required minlength="6" maxlength="18" :aria-invalid="Boolean(passwordFieldErrors.confirmPassword)" :aria-describedby="passwordFieldErrors.confirmPassword ? 'confirm-password-error' : undefined" /><button type="button" :aria-label="passwordVisibility.confirm ? '隐藏确认密码' : '显示确认密码'" @click="passwordVisibility.confirm = !passwordVisibility.confirm"><EyeOff v-if="passwordVisibility.confirm" :size="17" aria-hidden="true" /><Eye v-else :size="17" aria-hidden="true" /></button></div><small v-if="passwordFieldErrors.confirmPassword" id="confirm-password-error" class="field-error">{{ passwordFieldErrors.confirmPassword }}</small></label>
          <div class="password-settings-actions"><p>请勿与其他网站共用密码。</p><button class="portal-primary" type="submit" :disabled="passwordSubmitting || !canChangePassword"><KeyRound :size="17" aria-hidden="true" />{{ passwordSubmitting ? '修改中…' : '修改密码并退出' }}</button></div>
        </form>
      </section>
    </template>
  </PortalShell>
</template>
