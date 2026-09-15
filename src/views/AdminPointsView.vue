<script setup>
import { BadgePlus, CircleAlert, Plus, RotateCcw, Trash2 } from '@lucide/vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import {
  authState,
  grantPoints,
  listMembers,
  listPointGrants,
  getPointRules,
  reversePointGrant,
} from '../services/authApi'
import { showSubmissionFeedback } from '../services/submissionFeedback'

const rules = ref([])
const members = ref([])
const grants = ref([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const message = ref('')
const memberToAdd = ref('')
const reversalTargetId = ref(null)
const reversalSaving = ref(false)
const reversalForm = reactive({ reason: '', evidenceUrl: '' })
const today = localDateString()
const form = reactive({
  title: '',
  subcategory: '',
  occurredOn: today,
  itemTotalPoints: 1,
  sourceReference: '',
  evidenceUrl: '',
  description: '',
  allocations: [],
})

const selectedRule = computed(() => rules.value.find((rule) => rule.subcategory === form.subcategory) || null)
const eligibleMembers = computed(() =>
  members.value.filter(
    (member) =>
      member.status === 'OFFICIAL' && member.role !== 'TEACHER' && member.username !== authState.account?.username,
  ),
)
const availableMembers = computed(() => {
  const selectedIds = new Set(form.allocations.map((allocation) => allocation.memberProfileId))
  return eligibleMembers.value.filter((member) => !selectedIds.has(member.id))
})
const allocatedPoints = computed(() =>
  form.allocations.reduce((total, allocation) => total + Number(allocation.points || 0), 0),
)
const allocationValid = computed(() => {
  if (!form.allocations.length || form.allocations.some((item) => !item.contribution.trim())) return false
  if (selectedRule.value?.allocationPolicy === 'PER_MEMBER') {
    return form.allocations.every((item) => Number(item.points) === Number(form.itemTotalPoints))
  }
  return allocatedPoints.value === Number(form.itemTotalPoints)
})
const canSubmit = computed(
  () =>
    Boolean(
      form.title.trim() &&
      form.subcategory &&
      form.occurredOn &&
      Number(form.itemTotalPoints) > 0 &&
      form.sourceReference.trim() &&
      form.evidenceUrl.trim(),
    ) && allocationValid.value,
)

watch(
  () => [form.subcategory, form.itemTotalPoints],
  () => {
    if (selectedRule.value?.allocationPolicy !== 'PER_MEMBER') return
    form.allocations.forEach((allocation) => {
      allocation.points = Number(form.itemTotalPoints) || 1
    })
  },
)

onMounted(async () => {
  try {
    ;[rules.value, members.value, grants.value] = await Promise.all([getPointRules(), listMembers(), listPointGrants()])
    form.subcategory = rules.value[0]?.subcategory || ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})

function addMember() {
  const member = availableMembers.value.find((item) => item.id === memberToAdd.value)
  if (!member) return
  form.allocations.push({
    memberProfileId: member.id,
    memberName: member.name,
    memberCode: member.memberCode,
    points: selectedRule.value?.allocationPolicy === 'PER_MEMBER' ? Number(form.itemTotalPoints) || 1 : 1,
    contribution: '',
  })
  memberToAdd.value = ''
}

function removeMember(memberProfileId) {
  const index = form.allocations.findIndex((item) => item.memberProfileId === memberProfileId)
  if (index >= 0) form.allocations.splice(index, 1)
}

async function submitGrant() {
  if (!canSubmit.value) return
  saving.value = true
  errorMessage.value = ''
  message.value = ''
  try {
    const saved = await grantPoints({
      title: form.title.trim(),
      subcategory: form.subcategory,
      occurredOn: form.occurredOn,
      itemTotalPoints: Number(form.itemTotalPoints),
      sourceReference: form.sourceReference.trim(),
      evidenceUrl: form.evidenceUrl.trim(),
      description: form.description.trim() || null,
      allocations: form.allocations.map((allocation) => ({
        memberProfileId: allocation.memberProfileId,
        points: Number(allocation.points),
        contribution: allocation.contribution.trim(),
      })),
    })
    grants.value.unshift(saved)
    saved.allocations.forEach((allocation) => {
      const member = members.value.find((item) => item.id === allocation.memberProfileId)
      if (member) member.totalPoints = allocation.currentTotalPoints
    })
    resetGrantForm()
    showSubmissionFeedback({
      eyebrow: 'POINTS GRANTED',
      title: '积分已发放',
      message: `${saved.title}已向 ${saved.allocations.length} 名成员完成记分，流水和凭证已保留。`,
      confirmLabel: '查看积分流水',
    })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function resetGrantForm() {
  Object.assign(form, {
    title: '',
    occurredOn: today,
    itemTotalPoints: 1,
    sourceReference: '',
    evidenceUrl: '',
    description: '',
  })
  form.allocations.splice(0)
}

function openReversal(grant) {
  reversalTargetId.value = grant.id
  reversalForm.reason = ''
  reversalForm.evidenceUrl = ''
  message.value = ''
  errorMessage.value = ''
}

function cancelReversal() {
  reversalTargetId.value = null
  reversalForm.reason = ''
  reversalForm.evidenceUrl = ''
}

async function submitReversal(grant) {
  if (!reversalForm.reason.trim()) return
  reversalSaving.value = true
  errorMessage.value = ''
  try {
    const saved = await reversePointGrant(grant.id, {
      reason: reversalForm.reason.trim(),
      evidenceUrl: reversalForm.evidenceUrl.trim() || null,
    })
    grants.value.unshift(saved)
    cancelReversal()
    message.value = `${grant.title}已撤销，原始记录和反向流水均已保留。`
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    reversalSaving.value = false
  }
}

function isReversed(grant) {
  return grants.value.some((item) => item.reversalOfGrantId === grant.id)
}

function formatDateTime(value) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '—'
}

function localDateString() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10)
}
</script>

<template>
  <PortalShell
    eyebrow="ADMIN / POINTS"
    title="积分管理"
    description="按事项发放、按成员分配并保留凭证；错误记录通过整批撤销更正，不直接覆盖历史。"
  >
    <div v-if="loading" class="portal-state">正在读取积分规则与流水…</div>
    <div v-else-if="errorMessage && !rules.length" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <template v-else>
      <div v-if="message" class="save-message" role="status">{{ message }}</div>
      <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>

      <section class="points-rule-grid" aria-label="积分规则">
        <article v-for="rule in rules" :key="rule.subcategory">
          <span>{{ rule.categoryLabel }}</span>
          <strong>{{ rule.subcategoryLabel }}</strong>
          <small>
            {{ rule.allocationPolicy === 'PER_MEMBER' ? '每位成员全额计分' : '事项总分由成员分配' }}
            · {{ rule.monthlyCap ? `每月上限 ${rule.monthlyCap} 分` : '不设月度上限' }}
          </small>
        </article>
      </section>

      <div class="points-admin-layout">
        <section class="points-grant-card" aria-labelledby="points-grant-title">
          <header>
            <div>
              <p>NEW GRANT</p>
              <h2 id="points-grant-title">新增积分事项</h2>
            </div>
            <BadgePlus :size="24" aria-hidden="true" />
          </header>

          <form class="points-grant-form" @submit.prevent="submitGrant">
            <label>
              事项名称
              <input v-model="form.title" required maxlength="160" placeholder="例：全国大学生机器人大赛二等奖" />
            </label>
            <label>
              积分类型
              <select v-model="form.subcategory" required>
                <option v-for="rule in rules" :key="rule.subcategory" :value="rule.subcategory">
                  {{ rule.categoryLabel }} · {{ rule.subcategoryLabel }}
                </option>
              </select>
            </label>
            <label>
              发生日期
              <input v-model="form.occurredOn" type="date" required :max="today" />
            </label>
            <label>
              事项总分
              <input v-model.number="form.itemTotalPoints" type="number" required min="1" max="100000" />
              <small v-if="selectedRule?.allocationPolicy === 'PER_MEMBER'">竞赛类每位成员都按该分值计分。</small>
              <small v-else>当前已分配 {{ allocatedPoints }} / {{ form.itemTotalPoints }} 分。</small>
            </label>
            <label>
              唯一来源编号
              <input v-model="form.sourceReference" required maxlength="190" placeholder="例：COMP-2026-001" />
              <small>用于防止重复发分，撤销后重新发放也必须使用新编号。</small>
            </label>
            <label>
              凭证地址
              <input v-model="form.evidenceUrl" required maxlength="1000" placeholder="https://… 或 /uploads/…" />
            </label>
            <label class="full">
              事项说明（可选）
              <textarea v-model="form.description" rows="3" maxlength="1000"></textarea>
            </label>

            <fieldset class="points-allocation-editor full">
              <legend>成员与贡献分配</legend>
              <p>只能选择正式学生成员；积分管理员不能给自己发分。</p>
              <div class="points-member-adder">
                <label>
                  选择成员
                  <select v-model="memberToAdd">
                    <option value="">请选择…</option>
                    <option v-for="member in availableMembers" :key="member.id" :value="member.id">
                      {{ member.name }} · {{ member.memberCode }} · 当前 {{ member.totalPoints }} 分
                    </option>
                  </select>
                </label>
                <button type="button" :disabled="!memberToAdd" @click="addMember">
                  <Plus :size="17" aria-hidden="true" />添加成员
                </button>
              </div>

              <div v-if="form.allocations.length" class="points-allocation-list">
                <article v-for="allocation in form.allocations" :key="allocation.memberProfileId">
                  <header>
                    <div>
                      <strong>{{ allocation.memberName }}</strong>
                      <small>{{ allocation.memberCode }}</small>
                    </div>
                    <button
                      type="button"
                      :aria-label="`移除${allocation.memberName}`"
                      @click="removeMember(allocation.memberProfileId)"
                    >
                      <Trash2 :size="17" aria-hidden="true" />
                    </button>
                  </header>
                  <label>
                    应得分
                    <input
                      v-model.number="allocation.points"
                      type="number"
                      required
                      min="1"
                      max="100000"
                      :readonly="selectedRule?.allocationPolicy === 'PER_MEMBER'"
                    />
                  </label>
                  <label>
                    个人贡献说明
                    <textarea v-model="allocation.contribution" required rows="2" maxlength="500"></textarea>
                  </label>
                </article>
              </div>
              <div v-else class="points-empty-allocation">尚未添加成员。</div>
            </fieldset>

            <footer class="full">
              <p v-if="!allocationValid">
                <CircleAlert :size="16" aria-hidden="true" />
                {{ form.allocations.length ? '请补全贡献说明，并确保分配积分符合事项规则。' : '至少添加一名成员。' }}
              </p>
              <button class="portal-primary" type="submit" :disabled="saving || !canSubmit">
                <BadgePlus :size="17" aria-hidden="true" />{{ saving ? '发放中…' : '确认发放积分' }}
              </button>
            </footer>
          </form>
        </section>

        <section class="points-ledger-card" aria-labelledby="points-ledger-title">
          <header>
            <div>
              <p>AUDIT LEDGER</p>
              <h2 id="points-ledger-title">最近流水</h2>
            </div>
            <span>{{ grants.length }}</span>
          </header>
          <div v-if="grants.length" class="points-ledger-list">
            <article v-for="grant in grants" :key="grant.id" :data-type="grant.type">
              <header>
                <div>
                  <span>{{ grant.categoryLabel }} · {{ grant.subcategoryLabel }}</span>
                  <h3>{{ grant.title }}</h3>
                </div>
                <strong>{{ grant.awardedPoints > 0 ? '+' : '' }}{{ grant.awardedPoints }} 分</strong>
              </header>
              <dl>
                <div>
                  <dt>发生日期</dt>
                  <dd>{{ grant.occurredOn }}</dd>
                </div>
                <div>
                  <dt>经办人</dt>
                  <dd>{{ grant.operatorUsername }}</dd>
                </div>
                <div>
                  <dt>来源编号</dt>
                  <dd>{{ grant.sourceReference }}</dd>
                </div>
                <div>
                  <dt>记录时间</dt>
                  <dd>{{ formatDateTime(grant.createdAt) }}</dd>
                </div>
              </dl>
              <ul>
                <li v-for="allocation in grant.allocations" :key="allocation.memberProfileId">
                  <span>{{ allocation.memberName }} · {{ allocation.contribution }}</span>
                  <b>{{ allocation.creditedPoints > 0 ? '+' : '' }}{{ allocation.creditedPoints }}</b>
                </li>
              </ul>
              <a :href="grant.evidenceUrl" target="_blank" rel="noopener noreferrer">查看凭证</a>
              <button
                v-if="grant.type === 'GRANT' && !isReversed(grant)"
                class="points-reversal-trigger"
                type="button"
                @click="openReversal(grant)"
              >
                <RotateCcw :size="16" aria-hidden="true" />撤销本批积分
              </button>
              <span v-else-if="grant.type === 'GRANT'" class="points-reversed-label">已撤销</span>

              <form
                v-if="reversalTargetId === grant.id"
                class="points-reversal-form"
                @submit.prevent="submitReversal(grant)"
              >
                <label>
                  撤销原因
                  <textarea v-model="reversalForm.reason" required rows="2" maxlength="1000"></textarea>
                </label>
                <label>
                  新凭证地址（可选）
                  <input v-model="reversalForm.evidenceUrl" maxlength="1000" placeholder="留空则沿用原凭证" />
                </label>
                <div>
                  <button type="button" @click="cancelReversal">取消</button>
                  <button type="submit" :disabled="reversalSaving || !reversalForm.reason.trim()">
                    {{ reversalSaving ? '撤销中…' : '确认生成反向流水' }}
                  </button>
                </div>
              </form>
            </article>
          </div>
          <div v-else class="points-ledger-empty">暂无积分发放记录。</div>
        </section>
      </div>
    </template>
  </PortalShell>
</template>
