<script setup>
import {
  CalendarClock,
  CheckCircle2,
  MapPin,
  Pencil,
  Play,
  Plus,
  SkipForward,
  Square,
  Trash2,
  UserRoundCheck,
  UsersRound,
  X,
} from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  authState,
  callNextInterview,
  cancelInterviewSession,
  completeScheduledInterview,
  createInterviewSession,
  endInterviewSessionEarly,
  listInterviewSessions,
  markInterviewNoShow,
  startScheduledInterview,
  updateInterviewSession,
} from '../services/authApi'
import { confirmAction } from '../services/confirm'
import { useToastFeedback } from '../composables/useToastFeedback'
import { showSubmissionFeedback } from '../services/submissionFeedback'

const props = defineProps({ interviewers: { type: Array, default: () => [] } })
const emit = defineEmits(['completed'])
const sessions = ref([])
const loading = ref(true)
const working = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
useToastFeedback({ success: successMessage })
const editingId = ref(null)
const showForm = ref(false)
const resultTarget = ref(null)
const resultDialog = ref(null)
const form = reactive({ startAt: '', endAt: '', location: '', capacity: 1, interviewerUsernames: [] })
const resultForm = reactive({ score: '', evaluation: '', suggestedTags: '', decision: '' })
let pollTimer

const minStart = computed(() => toLocalInput(new Date(Date.now() + 60000).toISOString()))
const maxStart = computed(() => toLocalInput(new Date(Date.now() + 14 * 86400000).toISOString()))
const statusLabels = {
  SCHEDULED: '待开始',
  ACTIVE: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  ENDED_EARLY: '提前结束',
}
const bookingLabels = { WAITING: '等待中', CALLED: '已叫号', IN_PROGRESS: '面试中', COMPLETED: '已完成' }
const decisionLabels = { PASSED: '通过', REJECTED: '未通过', WAITLIST: '候补 / 观察' }

onMounted(async () => {
  await refresh()
  pollTimer = window.setInterval(refreshQuietly, 10000)
})
onBeforeUnmount(() => window.clearInterval(pollTimer))

async function refresh() {
  loading.value = true
  try {
    sessions.value = await listInterviewSessions()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}
async function refreshQuietly() {
  try {
    sessions.value = await listInterviewSessions()
  } catch {
    /* keep the last usable queue */
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    startAt: '',
    endAt: '',
    location: '',
    capacity: 1,
    interviewerUsernames: [authState.account.username],
  })
  showForm.value = true
}

function openEdit(session) {
  editingId.value = session.id
  Object.assign(form, {
    startAt: toLocalInput(session.startAt),
    endAt: toLocalInput(session.endAt),
    location: session.location,
    capacity: session.capacity,
    interviewerUsernames: session.interviewers.map((item) => item.username),
  })
  showForm.value = true
}

async function saveSession() {
  await run(async () => {
    const payload = {
      startAt: new Date(form.startAt).toISOString(),
      endAt: new Date(form.endAt).toISOString(),
      location: form.location.trim(),
      capacity: Number(form.capacity),
      interviewerUsernames: form.interviewerUsernames,
    }
    const updated = editingId.value
      ? await updateInterviewSession(editingId.value, payload)
      : await createInterviewSession(payload)
    upsert(updated)
    showForm.value = false
    successMessage.value = editingId.value ? '面试场次已更新。' : '面试场次已发布。'
  })
}

async function cancelSession(session) {
  if (
    !(await confirmAction({
      title: '取消该面试场次？',
      message: '已预约人员会收到重新预约的站内通知。',
      confirmText: '取消场次',
      cancelText: '保留',
      tone: 'danger',
    }))
  )
    return
  await run(async () => {
    await cancelInterviewSession(session.id)
    await refreshQuietly()
    successMessage.value = '场次已取消，相关预约已释放。'
  })
}

async function callNext(session) {
  await sessionAction(() => callNextInterview(session.id), '已叫下一位面试者。')
}
async function startInterview(session, booking) {
  await sessionAction(() => startScheduledInterview(session.id, booking.bookingId), '面试已开始。')
}
async function noShow(session, booking) {
  await sessionAction(() => markInterviewNoShow(session.id, booking.bookingId), '该面试者已移至队尾并获得新号码。')
}

async function openResult(session, booking) {
  resultTarget.value = { session, booking }
  Object.assign(resultForm, { score: '', evaluation: '', suggestedTags: '', decision: '' })
  await nextTick()
  resultDialog.value?.focus()
}

async function submitResult() {
  const { session, booking } = resultTarget.value
  await run(async () => {
    const updated = await completeScheduledInterview(session.id, booking.bookingId, {
      score: resultForm.score === '' ? null : Number(resultForm.score),
      evaluation: resultForm.evaluation.trim() || null,
      suggestedTags: splitTags(resultForm.suggestedTags),
      decision: resultForm.decision,
    })
    upsert(updated)
    resultTarget.value = null
    emit('completed')
    showSubmissionFeedback({
      eyebrow: 'INTERVIEW RESULT SAVED',
      title: '面试结果已提交',
      message: `${booking.applicantName} 的面试结论已经保存，已向报名者发送站内通知。`,
      confirmLabel: '返回面试队列',
    })
  })
}

async function endEarly(session) {
  if (
    !(await confirmAction({
      title: '提前结束本场面试？',
      message: '队列中尚未完成的预约会全部释放，等待人员会收到重新预约通知。',
      confirmText: '提前结束',
      tone: 'danger',
    }))
  )
    return
  await sessionAction(() => endInterviewSessionEarly(session.id), '面试已提前结束，等待人员已收到重新预约通知。')
}

async function sessionAction(action, message) {
  await run(async () => {
    upsert(await action())
    successMessage.value = message
  })
}

async function run(action) {
  working.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await action()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    working.value = false
  }
}

function upsert(updated) {
  const index = sessions.value.findIndex((item) => item.id === updated.id)
  if (index === -1) sessions.value.unshift(updated)
  else sessions.value[index] = updated
  sessions.value = [...sessions.value].sort((a, b) => new Date(b.startAt) - new Date(a.startAt))
}

function canCall(session) {
  return (
    session.currentUserInterviewer &&
    ['SCHEDULED', 'ACTIVE'].includes(session.status) &&
    Date.now() >= new Date(session.startAt).getTime() &&
    !session.queue.some((item) => ['CALLED', 'IN_PROGRESS'].includes(item.status)) &&
    session.queue.some((item) => item.status === 'WAITING')
  )
}
function splitTags(value) {
  return value
    .split(/[、,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
}
function formatDate(value) {
  return new Date(value).toLocaleString('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}
function toLocalInput(value) {
  const date = new Date(value)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<template>
  <section class="interview-manager">
    <header class="interview-manager-head">
      <div>
        <h2>面试场次与叫号</h2>
        <span>仅线下面试；发布者必须参加，每次叫号 1 人。</span>
      </div>
      <button class="portal-primary" type="button" @click="openCreate"><Plus :size="18" />发布场次</button>
    </header>
    <div v-if="errorMessage" class="form-alert" role="alert">{{ errorMessage }}</div>
    <div v-if="successMessage" class="save-message" role="status">{{ successMessage }}</div>

    <form v-if="showForm" class="interview-session-form" @submit.prevent="saveSession">
      <header>
        <h3>{{ editingId ? '修改面试场次' : '发布面试场次' }}</h3>
        <button type="button" aria-label="关闭" @click="showForm = false"><X :size="18" /></button>
      </header>
      <div>
        <label
          >开始时间<input
            v-model="form.startAt"
            type="datetime-local"
            required
            :min="minStart"
            :max="maxStart" /></label
        ><label
          >结束时间<input v-model="form.endAt" type="datetime-local" required :min="form.startAt || minStart" /></label
        ><label
          >线下面试地点<input
            v-model.trim="form.location"
            required
            maxlength="240"
            placeholder="例如：工科楼 A205" /></label
        ><label>面试人数<input v-model.number="form.capacity" type="number" min="1" max="100" required /></label>
      </div>
      <fieldset>
        <legend>面试官（发布者不可移除）</legend>
        <label v-for="item in props.interviewers" :key="item.username"
          ><input
            v-model="form.interviewerUsernames"
            type="checkbox"
            :value="item.username"
            :disabled="
              item.username ===
              (editingId
                ? sessions.find((session) => session.id === editingId)?.publisherUsername
                : authState.account.username)
            "
          />{{ item.name }} · {{ item.role === 'TEACHER' ? '指导老师' : '核心成员' }}</label
        >
      </fieldset>
      <button class="portal-primary" type="submit" :disabled="working || !form.interviewerUsernames.length">
        {{ working ? '保存中…' : '保存场次' }}
      </button>
    </form>

    <div v-if="loading" class="empty-note">正在读取面试场次…</div>
    <div v-else-if="!sessions.length" class="empty-note">尚未发布面试场次。</div>
    <div v-else class="interview-session-list">
      <article v-for="session in sessions" :key="session.id" class="interview-session-card">
        <header>
          <div>
            <span :class="`session-status ${session.status.toLowerCase()}`">{{ statusLabels[session.status] }}</span>
            <h3>
              {{ formatDate(session.startAt) }}—{{
                new Date(session.endAt).toLocaleTimeString('zh-CN', {
                  hour: '2-digit',
                  minute: '2-digit',
                  hour12: false,
                })
              }}
            </h3>
          </div>
          <div
            class="session-booking-summary"
            :aria-label="`已预约 ${session.bookedCount} 人，容量 ${session.capacity} 人；大一 ${session.firstYearCount ?? 0} 人，大二 ${session.secondYearCount ?? 0} 人`"
          >
            <strong>{{ session.bookedCount }} / {{ session.capacity }}</strong>
            <div class="session-grade-counts" aria-hidden="true">
              <span
                >大一 <b>{{ session.firstYearCount ?? 0 }}</b></span
              >
              <span
                >大二 <b>{{ session.secondYearCount ?? 0 }}</b></span
              >
            </div>
          </div>
        </header>
        <div class="session-meta">
          <span><MapPin :size="16" />{{ session.location }}</span
          ><span><UsersRound :size="16" />{{ session.interviewers.map((item) => item.name).join('、') }}</span
          ><span><CalendarClock :size="16" />发布者 {{ session.publisherUsername }}</span>
        </div>
        <div
          v-if="session.currentUserInterviewer && ['SCHEDULED', 'ACTIVE'].includes(session.status)"
          class="session-actions"
        >
          <button v-if="session.status === 'SCHEDULED'" type="button" @click="openEdit(session)">
            <Pencil :size="16" />修改</button
          ><button type="button" class="danger" @click="cancelSession(session)"><Trash2 :size="16" />取消场次</button
          ><button type="button" :disabled="!canCall(session) || working" @click="callNext(session)">
            <SkipForward :size="16" />叫下一位</button
          ><button type="button" class="danger" :disabled="working" @click="endEarly(session)">
            <Square :size="15" />提前结束
          </button>
        </div>
        <ol v-if="session.queue.length" class="interview-queue">
          <li v-for="booking in session.queue" :key="booking.bookingId" :class="booking.status.toLowerCase()">
            <b>{{ booking.queueNumber }}</b>
            <div>
              <strong>{{ booking.applicantName }}</strong
              ><span
                >{{ bookingLabels[booking.status]
                }}<template v-if="booking.interviewDecision">
                  · {{ decisionLabels[booking.interviewDecision] }}</template
                ></span
              >
            </div>
            <div v-if="session.currentUserInterviewer" class="queue-actions">
              <button v-if="booking.status === 'CALLED'" type="button" @click="startInterview(session, booking)">
                <Play :size="15" />开始</button
              ><button v-if="booking.status === 'CALLED'" type="button" @click="noShow(session, booking)">
                <SkipForward :size="15" />未到场</button
              ><button
                v-if="['CALLED', 'IN_PROGRESS'].includes(booking.status)"
                type="button"
                @click="openResult(session, booking)"
              >
                <CheckCircle2 :size="15" />填写结果
              </button>
            </div>
          </li>
        </ol>
        <p v-else class="empty-note">暂无预约。</p>
      </article>
    </div>

    <div v-if="resultTarget" class="interview-result-overlay" role="presentation" @click.self="resultTarget = null">
      <form
        ref="resultDialog"
        class="interview-result-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="result-title"
        tabindex="-1"
        @keydown.esc="resultTarget = null"
        @submit.prevent="submitResult"
      >
        <header>
          <div>
            <UserRoundCheck :size="21" aria-hidden="true" />
            <h3 id="result-title">{{ resultTarget.booking.applicantName }} · 面试结果</h3>
          </div>
          <button type="button" aria-label="关闭" @click="resultTarget = null">
            <X :size="18" aria-hidden="true" />
          </button>
        </header>
        <label>评分（0—100，可选）<input v-model="resultForm.score" type="number" min="0" max="100" /></label
        ><label
          >简评（通过或候补/观察时必填）<textarea
            v-model.trim="resultForm.evaluation"
            rows="4"
            maxlength="5000"
          /></label
        ><label>建议标签<input v-model="resultForm.suggestedTags" placeholder="用逗号或顿号分隔" /></label>
        <fieldset>
          <legend>面试结论</legend>
          <label><input v-model="resultForm.decision" type="radio" value="PASSED" required />通过</label
          ><label><input v-model="resultForm.decision" type="radio" value="WAITLIST" required />候补 / 观察</label
          ><label><input v-model="resultForm.decision" type="radio" value="REJECTED" required />未通过</label>
        </fieldset>
        <button
          class="portal-primary"
          type="submit"
          :disabled="
            working ||
            !resultForm.decision ||
            (['PASSED', 'WAITLIST'].includes(resultForm.decision) && !resultForm.evaluation)
          "
        >
          提交结果
        </button>
      </form>
    </div>
  </section>
</template>
