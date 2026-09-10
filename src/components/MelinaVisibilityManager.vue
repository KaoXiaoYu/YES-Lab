<script setup>
import { Bot, Save, Search } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { getMelinaVisibilitySettings, updateMelinaVisibilitySettings } from '../services/authApi'

const roles = [
  { value: 'TEACHER', label: '指导教师' },
  { value: 'CORE_STUDENT', label: '核心成员' },
  { value: 'MEMBER', label: '普通成员' },
  { value: 'VISITOR', label: '访客 / 招新成员' },
]
const roleLabels = Object.fromEntries(roles.map((role) => [role.value, role.label]))
const visibleRoles = ref([])
const accounts = ref([])
const modes = reactive({})
const search = ref('')
const loading = ref(true)
const saving = ref(false)
const message = ref('')
const errorMessage = ref('')

const filteredAccounts = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return accounts.value
  return accounts.value.filter((account) =>
    `${account.displayName} ${account.username} ${roleLabels[account.role] || account.role}`.toLowerCase().includes(keyword))
})

onMounted(load)

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    applySettings(await getMelinaVisibilitySettings())
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function applySettings(settings) {
  visibleRoles.value = [...(settings?.visibleRoles || [])]
  accounts.value = settings?.accounts || []
  for (const key of Object.keys(modes)) delete modes[key]
  for (const account of accounts.value) {
    modes[account.accountId] = account.overrideVisible == null
      ? 'INHERIT' : account.overrideVisible ? 'SHOW' : 'HIDE'
  }
}

async function save() {
  saving.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    const overrides = accounts.value
      .filter((account) => modes[account.accountId] !== 'INHERIT')
      .map((account) => ({ accountId: account.accountId, visible: modes[account.accountId] === 'SHOW' }))
    applySettings(await updateMelinaVisibilitySettings({ visibleRoles: visibleRoles.value, overrides }))
    message.value = '梅琳娜的展示范围已保存。用户下次打开页面时生效。'
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="melina-visibility-manager" aria-labelledby="melina-visibility-title">
    <header>
      <span aria-hidden="true"><Bot :size="22" /></span>
      <div><p>MELINA / VISIBILITY</p><h2 id="melina-visibility-title">梅琳娜展示范围</h2>
        <small>按角色设置默认范围，也可以为指定账号单独显示或隐藏。账号设置优先于角色设置。</small></div>
    </header>

    <div v-if="loading" class="melina-visibility-state">正在读取展示设置…</div>
    <div v-else-if="errorMessage && !accounts.length" class="form-alert" role="alert">{{ errorMessage }}</div>
    <form v-else @submit.prevent="save">
      <fieldset class="melina-role-options">
        <legend>默认展示角色</legend>
        <label v-for="role in roles" :key="role.value">
          <input v-model="visibleRoles" type="checkbox" :value="role.value" />
          <span><strong>{{ role.label }}</strong><small>勾选后，该角色默认显示梅琳娜与站内消息入口。</small></span>
        </label>
      </fieldset>

      <fieldset class="melina-account-overrides">
        <legend>指定账号例外</legend>
        <label class="melina-account-search"><Search :size="17" aria-hidden="true" />
          <span class="sr-only">搜索账号</span><input v-model="search" type="search" placeholder="搜索姓名、账号或角色" />
        </label>
        <div class="melina-account-list">
          <label v-for="account in filteredAccounts" :key="account.accountId">
            <span><strong>{{ account.displayName }}</strong><small>@{{ account.username }} · {{ roleLabels[account.role] || account.role }}</small></span>
            <select v-model="modes[account.accountId]" :aria-label="`${account.displayName} 的梅琳娜展示设置`">
              <option value="INHERIT">跟随角色</option>
              <option value="SHOW">始终显示</option>
              <option value="HIDE">始终隐藏</option>
            </select>
          </label>
          <p v-if="!filteredAccounts.length">没有符合条件的账号。</p>
        </div>
      </fieldset>

      <div v-if="message" class="save-message" role="status">{{ message }}</div>
      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
      <footer><p>隐藏后不会删除该账号已收到的站内消息，只是不再展示梅琳娜、铃铛和消息弹窗。</p>
        <button type="submit" :disabled="saving"><Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存展示范围' }}</button></footer>
    </form>
  </section>
</template>
