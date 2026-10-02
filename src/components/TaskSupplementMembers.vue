<script setup>
import { ChevronLeft, ChevronRight, Search, UserPlus } from '@lucide/vue'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { listTaskMemberOptions, supplementTaskAssignments } from '../services/authApi'

const props = defineProps({
  taskId: { type: String, required: true },
  assignments: { type: Array, required: true },
  busy: Boolean,
})
const emit = defineEmits(['completed'])
const roleLabels = { TEACHER: '教师', CORE_STUDENT: '核心学生', MEMBER: '普通成员' }
const members = ref([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const notice = ref('')
const errorBox = ref(null)
const roles = ref([])
const grades = ref([])
const keyword = ref('')
const selected = ref([])
const onlySelected = ref(false)
const showSelected = ref(false)
const page = ref(1)
const limit = 100
const pageSize = 20
const disabled = computed(() => props.busy || submitting.value || loading.value)
const assignedIds = computed(() => new Set(props.assignments.map((row) => row.memberProfileId)))
const selectedIds = computed(() => new Set(selected.value))
const gradeOptions = computed(() => [...new Set(members.value.map((m) => m.grade || ''))].sort())
const matches = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  return members.value.filter(
    (m) =>
      (!roles.value.length || roles.value.includes(m.role)) &&
      (!grades.value.length || grades.value.includes(m.grade || '')) &&
      (!query || [m.name, m.memberCode].some((text) => text?.toLocaleLowerCase().includes(query))),
  )
})
const available = computed(() => matches.value.filter((m) => !assignedIds.value.has(m.profileId)))
const results = computed(() => matches.value.filter((m) => !onlySelected.value || selectedIds.value.has(m.profileId)))
const pages = computed(() => Math.max(1, Math.ceil(results.value.length / pageSize)))
const visibleMembers = computed(() => results.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const selectedMembers = computed(() => members.value.filter((m) => selectedIds.value.has(m.profileId)))
const hiddenCount = computed(() => selected.value.filter((id) => !matches.value.some((m) => m.profileId === id)).length)
const bulkCount = computed(() => new Set([...selected.value, ...available.value.map((m) => m.profileId)]).size)
const canSelectMore = computed(() => selected.value.length < limit)

watch([roles, grades, keyword, onlySelected], () => (page.value = 1), { deep: true })
watch(pages, (count) => (page.value = Math.min(page.value, count)))
watch(assignedIds, (ids) => {
  const remaining = selected.value.filter((id) => !ids.has(id))
  const removed = selected.value.length - remaining.length
  if (removed) notice.value = `${removed} 位已选成员已获得任务，已从本次名单移除。`
  selected.value = remaining
})

async function report(message) {
  error.value = message
  await nextTick()
  errorBox.value?.focus()
}
async function loadMembers() {
  loading.value = true
  error.value = ''
  try {
    members.value = await listTaskMemberOptions()
  } catch (failure) {
    await report(failure.message)
  } finally {
    loading.value = false
  }
}
onMounted(loadMembers)
function toggleMember(id, checked) {
  if (assignedIds.value.has(id)) return
  if (checked && !selectedIds.value.has(id)) {
    if (!canSelectMore.value) return
    selected.value = [...selected.value, id]
  } else if (!checked) selected.value = selected.value.filter((value) => value !== id)
}
function selectMatches() {
  if (bulkCount.value > limit) return
  selected.value = [...new Set([...selected.value, ...available.value.map((m) => m.profileId)])]
}
function clearSelection() {
  selected.value = []
  notice.value = ''
}
async function submit() {
  if (disabled.value || !selected.value.length) return
  submitting.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await supplementTaskAssignments(props.taskId, { rules: [], memberProfileIds: [...selected.value] })
    clearSelection()
    emit('completed', result)
  } catch (failure) {
    await report(failure.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="admin-form-card supplement-members" aria-labelledby="supplement-title">
    <header>
      <UserPlus :size="22" aria-hidden="true" />
      <div>
        <p>SUPPLEMENT</p>
        <h3 id="supplement-title">选择补发成员</h3>
      </div>
    </header>
    <p class="supplement-help">按身份和年级筛选，再勾选本次要补发的人。已发放成员不会重复创建。</p>
    <p v-if="error" ref="errorBox" class="supplement-error" role="alert" tabindex="-1">{{ error }}</p>
    <div v-if="loading" class="supplement-help" role="status">正在读取成员名单…</div>
    <button v-else-if="!members.length && error" class="portal-secondary" type="button" @click="loadMembers">
      重试读取成员
    </button>
    <form v-else @submit.prevent="submit">
      <fieldset class="supplement-fields" :disabled="disabled">
        <legend class="sr-only">筛选和选择补发成员</legend>
        <div class="supplement-filters">
          <div class="supplement-filter" role="group" aria-labelledby="supplement-roles">
            <span id="supplement-roles" class="supplement-label">成员身份</span>
            <div class="supplement-options">
              <label v-for="(label, value) in roleLabels" :key="value" class="supplement-check">
                <input v-model="roles" type="checkbox" :value="value" />{{ label }}
              </label>
            </div>
          </div>
          <div class="supplement-filter" role="group" aria-labelledby="supplement-grades">
            <span id="supplement-grades" class="supplement-label">成员年级</span>
            <div class="supplement-options">
              <label v-for="grade in gradeOptions" :key="grade" class="supplement-check">
                <input v-model="grades" type="checkbox" :value="grade" />{{ grade || '年级未填' }}
              </label>
              <span v-if="!gradeOptions.length" class="supplement-help">暂无年级选项</span>
            </div>
          </div>
        </div>
        <label class="supplement-search" for="supplement-keyword">
          <span class="supplement-label">搜索成员</span>
          <span class="supplement-input"
            ><Search :size="18" aria-hidden="true" /><input
              id="supplement-keyword"
              v-model="keyword"
              type="search"
              placeholder="姓名或学号 / 内部编号"
          /></span>
        </label>
        <div class="supplement-toolbar">
          <p class="supplement-help" role="status" aria-atomic="true">
            匹配 {{ matches.length }} 人 · 可补发 {{ available.length }} 人 · 已发放
            {{ matches.length - available.length }} 人
          </p>
          <div class="supplement-tools">
            <button
              class="portal-secondary"
              type="button"
              :disabled="!available.length || bulkCount > limit"
              @click="selectMatches"
            >
              选中当前筛选结果（{{ available.length }} 人）
            </button>
            <label class="supplement-check"><input v-model="onlySelected" type="checkbox" />仅看已选</label>
            <button class="portal-secondary" type="button" :disabled="!selected.length" @click="clearSelection">
              清空选择
            </button>
          </div>
        </div>
        <p v-if="bulkCount > limit" class="supplement-help" role="status">
          合并已选名单将超过 100 人，请缩小筛选范围或先清空选择。
        </p>
        <ul class="supplement-list" aria-label="候选成员">
          <li v-for="member in visibleMembers" :key="member.profileId">
            <label class="supplement-member" :class="{ 'is-assigned': assignedIds.has(member.profileId) }">
              <input
                type="checkbox"
                :checked="selectedIds.has(member.profileId)"
                :disabled="assignedIds.has(member.profileId) || (!selectedIds.has(member.profileId) && !canSelectMore)"
                @change="toggleMember(member.profileId, $event.target.checked)"
              />
              <span class="supplement-person"
                ><strong>{{ member.name }}</strong
                ><span
                  >{{ member.memberCode || '编号未填' }} · {{ roleLabels[member.role] }} ·
                  {{ member.grade || '年级未填' }}</span
                ></span
              >
              <span class="supplement-badge">{{ assignedIds.has(member.profileId) ? '已发放' : '可补发' }}</span>
            </label>
          </li>
          <li v-if="!results.length" class="supplement-empty">
            {{
              onlySelected
                ? '当前筛选结果中没有已选成员，可取消「仅看已选」或查看全部已选名单。'
                : '没有匹配成员，请调整身份、年级或搜索内容。'
            }}
          </li>
        </ul>
        <div v-if="pages > 1" class="supplement-pagination">
          <button class="portal-secondary" type="button" :disabled="page === 1" aria-label="上一页成员" @click="page--">
            <ChevronLeft :size="16" aria-hidden="true" />上一页
          </button>
          <span>{{ page }} / {{ pages }} 页 · {{ results.length }} 人</span>
          <button
            class="portal-secondary"
            type="button"
            :disabled="page === pages"
            aria-label="下一页成员"
            @click="page++"
          >
            下一页<ChevronRight :size="16" aria-hidden="true" />
          </button>
        </div>
        <div class="supplement-selection">
          <p role="status" aria-atomic="true">
            <strong>已选 {{ selected.length }} 人</strong
            ><span v-if="hiddenCount">（含不在当前结果中的 {{ hiddenCount }} 人）</span>
          </p>
          <button
            class="portal-secondary"
            type="button"
            :disabled="!selected.length"
            :aria-expanded="showSelected"
            aria-controls="supplement-selected"
            @click="showSelected = !showSelected"
          >
            {{ showSelected ? '收起已选名单' : '查看已选名单' }}
          </button>
        </div>
        <ul
          v-if="showSelected && selected.length"
          id="supplement-selected"
          class="supplement-selected"
          aria-label="本次全部补发成员"
        >
          <li v-for="member in selectedMembers" :key="member.profileId">
            <span>{{ member.name }} · {{ member.memberCode || '编号未填' }}</span
            ><button
              type="button"
              :aria-label="`取消选择 ${member.name} ${member.memberCode || ''}`"
              @click="toggleMember(member.profileId, false)"
            >
              移除
            </button>
          </li>
        </ul>
        <p v-if="notice" class="supplement-help" role="status">{{ notice }}</p>
        <p class="supplement-help">
          {{
            selected.length === limit
              ? '已达单次 100 人上限，可取消成员后重新选择。'
              : selected.length
                ? '只向上述已选成员补发，原有任务状态和进度保留。'
                : '请先勾选成员；单次最多补发 100 人。'
          }}
        </p>
        <button class="portal-primary" type="submit" :disabled="!selected.length">
          <UserPlus :size="16" aria-hidden="true" />{{ submitting ? '正在补发…' : `补发给 ${selected.length} 人` }}
        </button>
      </fieldset>
    </form>
  </section>
</template>

<style scoped>
.supplement-members {
  color: var(--admin-foreground);
}
.supplement-fields {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
  display: grid;
  gap: 16px;
}
.supplement-help {
  margin: 0;
  color: var(--admin-muted);
  font-size: 14px;
  line-height: 1.65;
}
.supplement-members > .supplement-help {
  margin-bottom: 20px;
}
.supplement-label {
  font-size: 14px;
  font-weight: 650;
}
.supplement-filters {
  display: grid;
  gap: 8px;
}
.supplement-filter {
  display: flex;
  align-items: baseline;
  gap: 20px;
}
.supplement-filter > .supplement-label {
  flex: 0 0 64px;
  padding-top: 12px;
}
.supplement-options {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  min-width: 0;
}
.supplement-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  cursor: pointer;
  font-size: 14px;
}
.supplement-members input[type='checkbox'] {
  width: 18px;
  height: 18px;
  margin: 0;
  flex-shrink: 0;
  accent-color: var(--admin-accent);
}
.supplement-search {
  display: grid;
  gap: 8px;
}
.supplement-input {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid var(--admin-border-strong);
  border-radius: 8px;
  background: var(--admin-card);
  color: var(--admin-muted);
}
.supplement-members #supplement-keyword {
  width: 100%;
  min-width: 0;
  height: 44px;
  padding: 8px 0;
  background: transparent;
  border: 0;
  color: var(--admin-foreground);
  font: inherit;
  font-size: 16px;
  box-shadow: none;
}
.supplement-input:focus-within {
  outline: 2px solid var(--admin-accent);
  outline-offset: 2px;
}
.supplement-members #supplement-keyword:focus {
  outline: none;
  box-shadow: none;
}
.supplement-toolbar {
  display: grid;
  gap: 10px;
}
.supplement-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
}
.supplement-list,
.supplement-selected {
  padding: 0;
  margin: 0;
  list-style: none;
  border: 1px solid var(--admin-border);
  border-radius: 10px;
  overflow: hidden;
}
.supplement-list li + li,
.supplement-selected li + li {
  border-top: 1px solid var(--admin-border);
}
.supplement-member {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  min-height: 64px;
  cursor: pointer;
  background: var(--admin-card);
}
.supplement-member:has(input:checked) {
  background: var(--admin-accent-soft);
}
.supplement-member:focus-within {
  outline: 2px solid var(--admin-accent);
  outline-offset: -2px;
}
.supplement-member.is-assigned {
  cursor: default;
  background: var(--admin-card-muted);
}
.supplement-person {
  display: grid;
  gap: 4px;
  min-width: 0;
  flex: 1;
  overflow-wrap: anywhere;
}
.supplement-person strong {
  font-size: 15px;
}
.supplement-person > span {
  color: var(--admin-muted);
  font-size: 13px;
  line-height: 1.5;
}
.supplement-badge {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--admin-muted);
}
.supplement-empty {
  padding: 24px 16px;
  color: var(--admin-muted);
  line-height: 1.6;
  font-size: 14px;
}
.supplement-pagination,
.supplement-selection {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
}
.supplement-selection p {
  margin: 0;
  overflow-wrap: anywhere;
}
.supplement-selection p > span {
  color: var(--admin-muted);
}
.supplement-selected li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 16px;
  font-size: 14px;
}
.supplement-selected li > span {
  min-width: 0;
  overflow-wrap: anywhere;
}
.supplement-selected button {
  flex-shrink: 0;
  min-height: 44px;
  padding: 8px;
  border: 0;
  color: var(--admin-accent);
  background: transparent;
  cursor: pointer;
}
.supplement-error {
  padding: 12px 16px;
  border: 1px solid var(--color-danger-border);
  border-radius: 8px;
  color: var(--admin-danger);
  background: var(--admin-danger-soft);
}
.supplement-members button {
  min-height: 44px;
}
.supplement-fields > .portal-primary {
  justify-self: start;
  margin-top: 0;
}
.supplement-members :is(button, input):focus-visible {
  outline: 2px solid var(--admin-accent);
  outline-offset: 2px;
}
@media (max-width: 600px) {
  .supplement-filter {
    display: grid;
    gap: 0;
  }
  .supplement-filter > .supplement-label {
    padding: 0;
  }
  .supplement-options {
    gap: 0 12px;
  }
  .supplement-member {
    padding: 12px;
    gap: 10px;
  }
  .supplement-badge {
    font-size: 12px;
  }
  .supplement-fields > .portal-primary {
    width: 100%;
    justify-content: center;
  }
}
</style>
