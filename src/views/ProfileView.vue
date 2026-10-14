<script setup>
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import { Eye, EyeOff, KeyRound } from '@lucide/vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import MemberProfileDisplay from '../components/MemberProfileDisplay.vue'
import PointsHeatmap from '../components/PointsHeatmap.vue'
import ProfileTodayPanel from '../components/ProfileTodayPanel.vue'
import { authState } from '../services/authApi'
import PortalShell from '../components/PortalShell.vue'
import { changeOwnPassword, getOwnPoints, getOwnProfile, logout } from '../services/authApi'
import { showSubmissionFeedback } from '../services/submissionFeedback'

const router = useRouter()
const profile = ref(null)
const pointsSummary = ref(null)
const pointsError = ref('')
const pointsLoading = ref(true)
const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 5 ? '夜深了' : hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'
})
const name = computed(() => authState.account?.displayName || authState.account?.username || '')
const loading = ref(true)
const errorMessage = ref('')
const passwordError = ref('')
const passwordFieldErrors = ref({})
const passwordSubmitting = ref(false)
const passwordVisibility = reactive({ current: false, next: false, confirm: false })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const canChangePassword = computed(() =>
  Boolean(
    passwordForm.currentPassword &&
    passwordForm.newPassword.length >= 6 &&
    passwordForm.newPassword.length <= 18 &&
    passwordForm.confirmPassword,
  ),
)

onMounted(loadPoints)

async function loadPoints() {
  pointsLoading.value = true
  pointsError.value = ''
  try {
    pointsSummary.value = await getOwnPoints()
  } catch (error) {
    pointsError.value = error.message
  } finally {
    pointsLoading.value = false
  }
}

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
    await router.replace('/login')
    showSubmissionFeedback({
      title: '密码已修改',
      message: '当前账号已经安全退出，其他设备也无法继续续期。请使用新密码重新登录。',
      confirmLabel: '返回登录',
    })
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
  <PortalShell title="个人主页" :description="`${greeting}，${name}。查看今日待办、成员档案与积分记录。`">
    <ProfileTodayPanel :points-summary="pointsSummary">
      <LoadingSkeleton v-if="loading" variant="detail" :rows="3" label="正在读取成员资料" />
      <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
      <template v-else-if="profile">
        <MemberProfileDisplay :profile="{ ...profile, currentRank: pointsSummary?.totalRank }" private-view editable />
      </template>
      <LoadingSkeleton v-if="pointsLoading" variant="detail" :rows="1" label="正在读取积分" />
      <div v-else-if="pointsError" class="portal-state error" role="alert">
        {{ pointsError }} <button class="portal-secondary" type="button" @click="loadPoints">重试</button>
      </div>
      <PointsHeatmap
        v-else-if="pointsSummary"
        :daily-points="pointsSummary.dailyPoints"
        :total-points="pointsSummary.totalPoints"
      />
    </ProfileTodayPanel>
    <template v-if="profile">
      <section class="password-settings-card profile-password-card" aria-labelledby="profile-password-title">
        <header>
          <div>
            <h2 id="profile-password-title">修改登录密码</h2>
          </div>
          <span>修改成功后会退出当前账号，并撤销其他设备的续期登录状态。</span>
        </header>
        <div v-if="passwordError" class="form-alert" role="alert">{{ passwordError }}</div>
        <form class="password-settings-form" @submit.prevent="submitPasswordChange">
          <label
            >当前密码
            <div class="admin-password-input" :class="{ invalid: passwordFieldErrors.currentPassword }">
              <input
                v-model="passwordForm.currentPassword"
                :type="passwordVisibility.current ? 'text' : 'password'"
                autocomplete="current-password"
                required
                maxlength="72"
                :aria-invalid="Boolean(passwordFieldErrors.currentPassword)"
                :aria-describedby="passwordFieldErrors.currentPassword ? 'current-password-error' : undefined"
              /><button
                type="button"
                :aria-label="passwordVisibility.current ? '隐藏当前密码' : '显示当前密码'"
                @click="passwordVisibility.current = !passwordVisibility.current"
              >
                <EyeOff v-if="passwordVisibility.current" :size="17" aria-hidden="true" /><Eye
                  v-else
                  :size="17"
                  aria-hidden="true"
                />
              </button>
            </div>
            <small v-if="passwordFieldErrors.currentPassword" id="current-password-error" class="field-error">{{
              passwordFieldErrors.currentPassword
            }}</small></label
          >
          <label
            >新密码
            <div class="admin-password-input" :class="{ invalid: passwordFieldErrors.newPassword }">
              <input
                v-model="passwordForm.newPassword"
                :type="passwordVisibility.next ? 'text' : 'password'"
                autocomplete="new-password"
                required
                minlength="6"
                maxlength="18"
                :aria-invalid="Boolean(passwordFieldErrors.newPassword)"
                :aria-describedby="
                  ['profile-password-help', passwordFieldErrors.newPassword ? 'new-password-error' : null]
                    .filter(Boolean)
                    .join(' ')
                "
              /><button
                type="button"
                :aria-label="passwordVisibility.next ? '隐藏新密码' : '显示新密码'"
                @click="passwordVisibility.next = !passwordVisibility.next"
              >
                <EyeOff v-if="passwordVisibility.next" :size="17" aria-hidden="true" /><Eye
                  v-else
                  :size="17"
                  aria-hidden="true"
                />
              </button>
            </div>
            <small id="profile-password-help">密码长度为 6—18 位。</small
            ><small v-if="passwordFieldErrors.newPassword" id="new-password-error" class="field-error">{{
              passwordFieldErrors.newPassword
            }}</small></label
          >
          <label
            >确认新密码
            <div class="admin-password-input" :class="{ invalid: passwordFieldErrors.confirmPassword }">
              <input
                v-model="passwordForm.confirmPassword"
                :type="passwordVisibility.confirm ? 'text' : 'password'"
                autocomplete="new-password"
                required
                minlength="6"
                maxlength="18"
                :aria-invalid="Boolean(passwordFieldErrors.confirmPassword)"
                :aria-describedby="passwordFieldErrors.confirmPassword ? 'confirm-password-error' : undefined"
              /><button
                type="button"
                :aria-label="passwordVisibility.confirm ? '隐藏确认密码' : '显示确认密码'"
                @click="passwordVisibility.confirm = !passwordVisibility.confirm"
              >
                <EyeOff v-if="passwordVisibility.confirm" :size="17" aria-hidden="true" /><Eye
                  v-else
                  :size="17"
                  aria-hidden="true"
                />
              </button>
            </div>
            <small v-if="passwordFieldErrors.confirmPassword" id="confirm-password-error" class="field-error">{{
              passwordFieldErrors.confirmPassword
            }}</small></label
          >
          <div class="password-settings-actions">
            <p>请勿与其他网站共用密码。</p>
            <button class="portal-primary" type="submit" :disabled="passwordSubmitting || !canChangePassword">
              <KeyRound :size="17" aria-hidden="true" />{{ passwordSubmitting ? '修改中…' : '修改密码并退出' }}
            </button>
          </div>
        </form>
      </section>
    </template>
  </PortalShell>
</template>
