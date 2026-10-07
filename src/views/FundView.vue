<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Wallet, Plus, RotateCcw } from '@lucide/vue'
import PortalShell from '../components/PortalShell.vue'
import FundSummary from '../components/FundSummary.vue'
import { authState, getFundSummary, getFundEntries, createFundEntry, reverseFundEntry } from '../services/authApi'
import { formatMoney, labDate, labTime } from '../services/fundFormat'
const summary = ref(null)
const rows = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(true)
const error = ref('')
const success = ref('')
const busy = ref(false)
const formError = ref('')
const errorElement = ref(null)
const formOpen = ref(false)
const reverseId = ref(null)
const reason = ref('')
const filters = reactive({ type: '', from: '', to: '' })
const form = reactive({
  type: 'INCOME',
  amount: '',
  occurredOn: labDate(),
  title: '',
  description: '',
  confirmed: false,
})
const canManage = computed(() => ['TEACHER', 'CORE_STUDENT'].includes(authState.account?.role))
const initialize = computed(() => summary.value && !summary.value.initialized)
const labels = { OPENING: '期初登记', INCOME: '收入', EXPENSE: '支出', REVERSAL: '撤销' }
let savedPayload = ''
let requestKey = ''
let requestVersion = 0
function keyFor(payload) {
  const text = JSON.stringify(payload)
  if (text !== savedPayload) {
    savedPayload = text
    requestKey = crypto.randomUUID()
  }
  return requestKey
}
let hasLoaded = false
async function load() {
  const ticket = ++requestVersion
  // Keep current content on screen while refreshing after an action.
  if (!hasLoaded) loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, pageSize: 20 }
    for (const [key, value] of Object.entries(filters)) if (value) params[key] = value
    const [account, entries] = await Promise.all([getFundSummary(), getFundEntries(params)])
    if (ticket !== requestVersion) return
    summary.value = account
    rows.value = entries.entries
    total.value = entries.totalCount
  } catch (e) {
    if (ticket === requestVersion) error.value = e.message
  } finally {
    hasLoaded = true
    if (ticket === requestVersion) loading.value = false
  }
}
async function fail(e) {
  formError.value = e.message
  await nextTick()
  errorElement.value?.focus()
}
async function save() {
  busy.value = true
  formError.value = ''
  success.value = ''
  const payload = {
    type: initialize.value ? 'OPENING' : form.type,
    amount: form.amount,
    occurredOn: form.occurredOn,
    title: form.title.trim(),
    description: form.description.trim(),
  }
  try {
    if (initialize.value && !form.confirmed) throw new Error('请确认期初余额。')
    await createFundEntry({ ...payload, requestKey: keyFor(payload) }, initialize.value)
    success.value = initialize.value ? '期初余额已登记。' : '收支已记入基金账本。'
    form.amount = ''
    form.title = ''
    form.description = ''
    form.confirmed = false
    formOpen.value = false
    savedPayload = ''
    page.value = 0
    await load()
  } catch (e) {
    await fail(e)
  } finally {
    busy.value = false
  }
}
async function reverse(row) {
  busy.value = true
  formError.value = ''
  success.value = ''
  const payload = { id: row.id, reason: reason.value.trim() }
  try {
    if (!payload.reason) throw new Error('请填写撤销原因。')
    await reverseFundEntry(row.id, { reason: payload.reason, requestKey: keyFor(payload) })
    reverseId.value = null
    reason.value = ''
    savedPayload = ''
    success.value = '已撤销，原流水及原因均已保留。'
    await load()
  } catch (e) {
    await fail(e)
  } finally {
    busy.value = false
  }
}
function changePage(delta) {
  page.value += delta
  load()
}
function toggleForm() {
  formOpen.value = !formOpen.value
  reverseId.value = null
}
function applyFilters() {
  page.value = 0
  load()
}
function toggleReverse(row) {
  reverseId.value = reverseId.value === row.id ? null : row.id
  reason.value = ''
  formOpen.value = false
}
onMounted(load)
</script>
<template>
  <PortalShell title="实验室基金" description="查看基金余额与实际收支，所有记账和撤销均保留记录。">
    <div v-if="error" class="form-alert" role="alert">
      {{ error }} <button type="button" @click="load">重试</button>
    </div>
    <p v-if="loading && !summary" class="portal-state" role="status">正在读取基金账本…</p>
    <template v-if="summary"
      ><section class="lab-info-card">
        <header>
          <div>
            <h2><Wallet :size="22" aria-hidden="true" />基金概况</h2>
          </div>
          <span>人民币</span>
        </header>
        <FundSummary :summary="summary" opening />
        <p class="ledger-muted">
          {{
            summary.initialized
              ? `期初日期 ${summary.openedOn} · 汇总更新于 ${labTime(summary.updatedAt)}`
              : '尚未登记期初余额，登记后才能记账。'
          }}
        </p>
        <p class="ledger-muted">余额 = 期初余额 + 有效收入 − 有效支出</p>
        <button
          v-if="canManage"
          type="button"
          class="disclosure-button"
          :aria-expanded="formOpen"
          aria-controls="fund-entry-form"
          :disabled="busy"
          @click="toggleForm"
        >
          <Plus :size="18" aria-hidden="true" />{{ initialize ? '登记期初余额' : '新增收支' }}
        </button>
      </section>
      <p v-if="success" class="ledger-success" role="status">{{ success }}</p>
      <div v-if="formError" ref="errorElement" class="form-alert" role="alert" tabindex="-1">{{ formError }}</div>
      <form v-if="canManage && formOpen" id="fund-entry-form" class="lab-info-card ledger-form" @submit.prevent="save">
        <h2>{{ initialize ? '登记期初余额' : '新增收支' }}</h2>
        <fieldset :disabled="busy">
          <div class="ledger-fields">
            <label v-if="!initialize"
              >收支类型<select v-model="form.type">
                <option value="INCOME">收入</option>
                <option value="EXPENSE">支出</option>
              </select></label
            ><label
              >金额（元）<input
                v-model="form.amount"
                type="number"
                :min="initialize ? '0' : '0.01'"
                step="0.01"
                required
                inputmode="decimal" /></label
            ><label>发生日期<input v-model="form.occurredOn" type="date" required /></label
            ><label>事项名称<input v-model.trim="form.title" maxlength="160" required /></label
            ><label class="ledger-wide"
              >事项说明<textarea v-model.trim="form.description" rows="3" maxlength="1000"></textarea>
            </label>
          </div>
          <label v-if="initialize" class="ledger-checkbox"
            ><input v-model="form.confirmed" type="checkbox" required />我已核对期初余额（可以为零），确认登记。</label
          ><button class="disclosure-button ledger-primary" type="submit">{{ busy ? '正在保存…' : '确认记账' }}</button>
        </fieldset>
      </form>
      <section class="lab-info-card">
        <header>
          <div>
            <h2>收支记录</h2>
          </div>
          <span>共 {{ total }} 条</span>
        </header>
        <form class="ledger-filters" @submit.prevent="applyFilters">
          <label
            >类型<select v-model="filters.type">
              <option value="">全部</option>
              <option v-for="(label, type) in labels" :key="type" :value="type">{{ label }}</option>
            </select></label
          ><label>开始日期<input v-model="filters.from" type="date" /></label
          ><label>结束日期<input v-model="filters.to" type="date" /></label
          ><button type="submit" :disabled="loading">筛选</button>
        </form>
        <p v-if="loading" role="status">正在读取流水…</p>
        <p v-else-if="!rows.length" class="ledger-muted">当前筛选下暂无记录。</p>
        <ol class="fund-entry-list">
          <li v-for="row in rows" :key="row.id">
            <div class="fund-entry-heading">
              <div>
                <span class="ledger-type">{{ labels[row.type] }}{{ row.reversalEntryId ? ' · 已撤销' : '' }}</span>
                <h3>{{ row.title }}</h3>
              </div>
              <strong
                >{{ row.type === 'EXPENSE' ? '−' : row.type === 'INCOME' ? '+' : ''
                }}{{ formatMoney(row.amount) }}</strong
              >
            </div>
            <p v-if="row.description">{{ row.description }}</p>
            <p class="ledger-muted">
              发生日期 {{ row.occurredOn }} · 记账人 {{ row.operatorName }} · {{ labTime(row.recordedAt) }}
            </p>
            <p class="ledger-muted">流水编号 {{ row.id }}</p>
            <p v-if="row.originalEntryId">撤销原因：{{ row.reversalReason }}<br />原流水：{{ row.originalEntryId }}</p>
            <p v-if="row.reversalEntryId">
              由 {{ row.reversedBy }} 于 {{ labTime(row.reversedAt) }} 撤销：{{ row.reason }}
            </p>
            <button
              v-if="canManage && ['INCOME', 'EXPENSE'].includes(row.type) && !row.reversalEntryId"
              type="button"
              :disabled="busy"
              class="disclosure-button"
              :aria-expanded="reverseId === row.id"
              @click="toggleReverse(row)"
            >
              <RotateCcw :size="16" aria-hidden="true" />撤销本笔记录
            </button>
            <form v-if="reverseId === row.id" class="fund-reversal-form" @submit.prevent="reverse(row)">
              <label
                >撤销原因<textarea
                  v-model.trim="reason"
                  required
                  maxlength="1000"
                  :disabled="busy"
                  rows="2"
                ></textarea></label
              ><button type="submit" :disabled="busy">{{ busy ? '正在撤销…' : '确认撤销并留痕' }}</button
              ><button type="button" :disabled="busy" @click="reverseId = null">取消</button>
            </form>
          </li>
        </ol>
        <div v-if="total > 20" class="ledger-pagination">
          <button type="button" :disabled="loading || page === 0" @click="changePage(-1)">上一页</button
          ><span>{{ page + 1 }} / {{ Math.ceil(total / 20) }}</span
          ><button type="button" :disabled="loading || (page + 1) * 20 >= total" @click="changePage(1)">下一页</button>
        </div>
      </section>
    </template>
  </PortalShell>
</template>
