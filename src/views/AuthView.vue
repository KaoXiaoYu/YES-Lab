<script setup>
import ThemeToggle from '../components/ThemeToggle.vue'
import { ArrowLeft, ArrowRight, Eye, EyeOff, LockKeyhole, UserRound } from 'lucide-vue-next'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../services/authApi'

const route = useRoute()
const router = useRouter()
const mode = ref(route.path === '/register' ? 'register' : 'login')
const submitting = ref(false)
const showPassword = ref(false)
const errorMessage = ref('')
const fieldErrors = ref({})
const form = reactive({ username: '', password: '', confirmPassword: '', rememberMe: false })

const isRegister = computed(() => mode.value === 'register')
const normalizedUsername = computed(() => normalizeUsername(form.username))
const accountType = computed(() => identifyAccountType(form.username))

watch(() => route.path, (path) => {
  mode.value = path === '/register' ? 'register' : 'login'
  errorMessage.value = ''
  fieldErrors.value = {}
})

async function submit() {
  errorMessage.value = ''
  fieldErrors.value = {}
  if (isRegister.value && !validateUsername()) return
  if (isRegister.value && (form.password.length < 6 || form.password.length > 18)) {
    fieldErrors.value = { password: '密码长度需为 6—18 位' }
    return
  }
  if (isRegister.value && form.password !== form.confirmPassword) {
    fieldErrors.value = { confirmPassword: '两次输入的密码不一致' }
    return
  }

  submitting.value = true
  try {
    const account = isRegister.value
      ? await register({ username: normalizedUsername.value, password: form.password })
      : await login({ username: form.username.trim(), password: form.password, rememberMe: form.rememberMe })
    const requestedPath = typeof route.query.redirect === 'string' ? route.query.redirect : null
    await router.push(requestedPath || (account.role === 'VISITOR' ? '/application' : '/profile'))
  } catch (error) {
    errorMessage.value = error.message
    fieldErrors.value = error.fields || {}
  } finally {
    submitting.value = false
  }
}

function normalizeUsername(value) {
  const trimmed = value.trim()
  if (trimmed.includes('@')) return trimmed.toLowerCase()
  const compactPhone = trimmed.replace(/[ -]/g, '')
  return /^\+?\d+$/.test(compactPhone) ? compactPhone : trimmed
}

function identifyAccountType(value) {
  const normalized = normalizeUsername(value)
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized) && normalized.length <= 190) return 'email'
  return null
}

function validateUsername() {
  if (!form.username.trim()) {
    fieldErrors.value = { ...fieldErrors.value, username: '请输入邮箱' }
    return false
  }
  if (!accountType.value) {
    fieldErrors.value = { ...fieldErrors.value, username: '请输入有效的邮箱' }
    return false
  }
  const { username, ...remainingErrors } = fieldErrors.value
  fieldErrors.value = remainingErrors
  return true
}
</script>

<template>
  <main class="auth-page"><ThemeToggle class="auth-theme" />
    <section class="auth-story">
      <RouterLink class="auth-back" to="/"><ArrowLeft :size="17" aria-hidden="true" />返回公开首页</RouterLink>
      <div>
        <p>YES LAB / IDENTITY</p>
        <img src="/yes-lab-logo.png" alt="YES Lab" width="900" height="506" />
        <h1>从一次报名，<br />走向真实项目。</h1>
        <span>Yichun Embodied Science</span>
      </div>
      <ol aria-label="新成员成长流程">
        <li><span>01</span>报名与初筛</li><li><span>02</span>面试与技能测试</li><li><span>03</span>试用期与正式成员</li>
      </ol>
    </section>

    <section class="auth-panel" aria-labelledby="auth-title">
      <div class="auth-tabs" role="tablist" aria-label="账号入口">
        <RouterLink to="/login" :aria-selected="!isRegister" role="tab">登录</RouterLink>
        <RouterLink to="/register" :aria-selected="isRegister" role="tab">新用户注册</RouterLink>
      </div>

      <header>
        <p>{{ isRegister ? 'NEW USER REGISTRATION' : 'MEMBER SIGN IN' }}</p>
        <h2 id="auth-title">{{ isRegister ? '创建报名账号' : '欢迎回来' }}</h2>
        <span v-if="!isRegister">使用实验室账号进入你的工作面板。</span>
      </header>

      <form novalidate @submit.prevent="submit">
        <div v-if="route.query.passwordChanged === '1'" class="save-message" role="status">密码已修改，请使用新密码重新登录。</div>
        <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
        <label for="username">{{ isRegister ? '邮箱' : '账号' }}</label>
        <div class="input-shell" :class="{ invalid: fieldErrors.username }"><UserRound :size="18" aria-hidden="true" /><input id="username" v-model="form.username" :type="isRegister ? 'email' : 'text'" name="username" autocomplete="username" required maxlength="190" :placeholder="isRegister ? 'name@example.com' : '邮箱、手机号或实验室账号'" :aria-invalid="Boolean(fieldErrors.username)" :aria-describedby="fieldErrors.username ? 'username-error' : undefined" @blur="isRegister && validateUsername()" /></div>
        <small v-if="fieldErrors.username" id="username-error" class="field-error">{{ fieldErrors.username }}</small>

        <label for="password">密码</label>
        <div class="input-shell" :class="{ invalid: fieldErrors.password }"><LockKeyhole :size="18" aria-hidden="true" /><input id="password" v-model="form.password" :minlength="isRegister ? 6 : undefined" :maxlength="isRegister ? 18 : undefined" :aria-invalid="Boolean(fieldErrors.password)" :aria-describedby="[isRegister ? 'password-help' : null, fieldErrors.password ? 'password-error' : null].filter(Boolean).join(' ') || undefined" name="password" :type="showPassword ? 'text' : 'password'" :autocomplete="isRegister ? 'new-password' : 'current-password'" required placeholder="输入密码" /><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" aria-hidden="true" /><Eye v-else :size="18" aria-hidden="true" /></button></div>
        <small v-if="isRegister" id="password-help" class="field-help">密码长度为 6—18 位。</small>
        <small v-if="fieldErrors.password" id="password-error" class="field-error">{{ fieldErrors.password }}</small>

        <template v-if="isRegister">
          <label for="confirm-password">确认密码</label>
          <div class="input-shell" :class="{ invalid: fieldErrors.confirmPassword }"><LockKeyhole :size="18" aria-hidden="true" /><input id="confirm-password" v-model="form.confirmPassword" name="confirm-password" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" required placeholder="再次输入密码" /></div>
          <small v-if="fieldErrors.confirmPassword" class="field-error">{{ fieldErrors.confirmPassword }}</small>
        </template>

        <label v-else class="remember-login">
          <input v-model="form.rememberMe" type="checkbox" name="remember-me" />
          <span><strong>记住我的登录状态</strong><small>仅在这台可信设备上勾选；关闭浏览器后仍保持登录 30 天。</small></span>
        </label>

        <button class="auth-submit" type="submit" :disabled="submitting"><span>{{ submitting ? '正在提交…' : (isRegister ? '注册并填写报名表' : '登录成员系统') }}</span><ArrowRight :size="19" aria-hidden="true" /></button>
      </form>

      <p v-if="!isRegister" class="auth-note">教师、核心学生和正式成员账号由实验室统一维护；公开注册账号默认身份为游客。</p>
    </section>
  </main>
</template>
