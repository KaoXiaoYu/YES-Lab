<script setup>
import { Bot, Save, Search } from '@lucide/vue'
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
const mascots = reactive({})
const search = ref('')
const loading = ref(true)
const saving = ref(false)
const message = ref('')
const errorMessage = ref('')

const filteredAccounts = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return accounts.value
  return accounts.value.filter((account) =>
    `${account.displayName} ${account.username} ${roleLabels[account.role] || account.role}`
      .toLowerCase()
      .includes(keyword),
  )
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
  for (const key of Object.keys(mascots)) delete mascots[key]
  for (const account of accounts.value) {
    mascots[account.accountId] = account.mascot === 'NAILONG' ? 'NAILONG' : 'MELINA'
    modes[account.accountId] = account.overrideVisible == null ? 'INHERIT' : account.overrideVisible ? 'SHOW' : 'HIDE'
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
    const mascotOverrides = accounts.value
      .filter((account) => mascots[account.accountId] === 'NAILONG')
      .map((account) => ({ accountId: account.accountId, mascot: 'NAILONG' }))
    applySettings(
      await updateMelinaVisibilitySettings({ visibleRoles: visibleRoles.value, overrides, mascotOverrides }),
    )
    window.dispatchEvent(new CustomEvent('openlims:notification-settings-updated'))
    message.value = '吉祥物与展示设置已保存。对应账号刷新页面即可生效，正在浏览的页面会自动更新。'
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
      <div>
        <p>COMPANION / SETTINGS</p>
        <h2 id="melina-visibility-title">吉祥物与展示设置</h2>
        <small>仅指导老师可修改。按账号选择梅琳娜或奶龙，也可以单独设置显示范围。</small>
      </div>
    </header>

    <div v-if="loading" class="melina-visibility-state">正在读取展示设置…</div>
    <div v-else-if="errorMessage && !accounts.length" class="form-alert" role="alert">{{ errorMessage }}</div>
    <form v-else @submit.prevent="save">
      <fieldset class="melina-role-options" :disabled="saving">
        <legend>默认展示角色</legend>
        <label v-for="role in roles" :key="role.value">
          <input v-model="visibleRoles" type="checkbox" :value="role.value" />
          <span
            ><strong>{{ role.label }}</strong
            ><small>勾选后，该角色默认显示自己的吉祥物与站内消息入口。</small></span
          >
        </label>
      </fieldset>

      <fieldset class="melina-account-overrides" :disabled="saving">
        <legend>指定账号设置</legend>
        <p class="mascot-setting-help">未单独选择时显示梅琳娜。吉祥物只影响对应账号，显示范围仍按原规则生效。</p>
        <label class="melina-account-search"
          ><Search :size="17" aria-hidden="true" /> <span class="sr-only">搜索账号</span
          ><input v-model="search" type="search" placeholder="搜索姓名、账号或角色" />
        </label>
        <div class="melina-account-list">
          <div v-for="account in filteredAccounts" :key="account.accountId" class="mascot-account-row">
            <span class="mascot-account-name"
              ><strong>{{ account.displayName }}</strong
              ><small>@{{ account.username }} · {{ roleLabels[account.role] || account.role }}</small></span
            >
            <label class="mascot-account-control">
              <span>显示范围</span>
              <select v-model="modes[account.accountId]" :aria-label="`${account.username} 的显示范围`">
                <option value="INHERIT">跟随角色</option>
                <option value="SHOW">始终显示</option>
                <option value="HIDE">始终隐藏</option>
              </select>
            </label>
            <label class="mascot-account-control">
              <span>吉祥物</span>
              <select v-model="mascots[account.accountId]" :aria-label="`${account.username} 的吉祥物`">
                <option value="MELINA">梅琳娜</option>
                <option value="NAILONG">奶龙</option>
              </select>
            </label>
          </div>
          <p v-if="!filteredAccounts.length">没有符合条件的账号。</p>
        </div>
      </fieldset>

      <div v-if="message" class="save-message" role="status">{{ message }}</div>
      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
      <footer>
        <p>隐藏后不会删除该账号已收到的站内消息，只是不再展示吉祥物、铃铛和消息弹窗。</p>
        <button type="submit" :disabled="saving">
          <Save :size="17" aria-hidden="true" />{{ saving ? '保存中…' : '保存吉祥物与展示设置' }}
        </button>
      </footer>
    </form>
  </section>
</template>

<style scoped>
.mascot-setting-help {
  margin: 0 0 12px;
  color: var(--admin-muted, var(--color-muted-foreground));
  font-size: 12px;
  line-height: 1.6;
}
.melina-visibility-manager .melina-account-list {
  max-height: 420px;
}
.melina-role-options {
  align-content: start;
  align-self: start;
}
.mascot-account-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid var(--admin-border, var(--color-border));
}
.mascot-account-row:last-child {
  border-bottom: 0;
}
.mascot-account-name {
  display: grid;
  gap: 3px;
  grid-column: 1 / -1;
  overflow-wrap: anywhere;
}
.mascot-account-control {
  display: grid;
  min-width: 0;
  gap: 6px;
  color: var(--admin-muted, var(--color-muted-foreground));
  font-size: 12px;
}
.melina-visibility-manager .melina-account-list .mascot-account-row .mascot-account-control select {
  width: 100%;
  min-height: 44px;
  padding: 8px;
  font-size: 13px;
}
.melina-visibility-manager .melina-account-list .mascot-account-row .mascot-account-control select:focus-visible {
  outline: 2px solid var(--admin-accent, var(--color-secondary));
  outline-offset: 2px;
}
.melina-visibility-manager form footer button {
  min-height: 44px;
}
</style>
