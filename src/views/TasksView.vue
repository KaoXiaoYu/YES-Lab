<script setup>
import { ClipboardCheck, ListChecks } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import PortalShell from '../components/PortalShell.vue'
import { getMyTasks } from '../services/authApi'

const tasks = ref([])
const loading = ref(true)
const errorMessage = ref('')
const statusFilter = ref('ALL')

const statusLabels = {
  PENDING: '待完成',
  SUBMITTED: '等待确认',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const taskStatusLabels = { PUBLISHED: '进行中', CLOSED: '已结束' }

const filtered = computed(() =>
  tasks.value.filter((task) => statusFilter.value === 'ALL' || task.status === statusFilter.value),
)

function progressPercent(task) {
  if (!task.totalSubtasks) return 0
  return Math.round((task.submittedSubtasks / task.totalSubtasks) * 100)
}

onMounted(async () => {
  try {
    tasks.value = await getMyTasks()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <PortalShell
    eyebrow="COLLABORATION / TASKS"
    title="我的任务"
    description="查看管理员发放给你的任务，逐项完成子任务后提交完成说明，由管理员人工确认。"
  >
    <section class="task-toolbar" aria-label="任务筛选">
      <label
        >任务状态<select v-model="statusFilter">
          <option value="ALL">全部状态</option>
          <option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option>
        </select></label
      >
    </section>

    <div v-if="loading" class="portal-state">正在读取任务…</div>
    <div v-else-if="errorMessage" class="portal-state error" role="alert">{{ errorMessage }}</div>
    <div v-else-if="!filtered.length" class="portal-state project-empty">
      <ListChecks :size="28" aria-hidden="true" /><strong>暂无任务</strong><span>管理员发放任务后会出现在这里。</span>
    </div>

    <section v-else class="task-card-grid" aria-label="我的任务列表">
      <RouterLink
        v-for="task in filtered"
        :key="task.assignmentId"
        class="task-card"
        :to="`/tasks/${task.assignmentId}`"
      >
        <header>
          <span :data-status="task.status">{{ statusLabels[task.status] || task.status }}</span>
          <b>{{ taskStatusLabels[task.taskStatus] || task.taskStatus }}</b>
        </header>
        <h2>{{ task.title }}</h2>
        <p class="task-card-dates">
          {{ task.startDate || '未设置开始' }} — {{ task.endDate || '未设置截止' }}
          <span v-if="task.overdue" class="overdue">已逾期</span>
        </p>
        <div v-if="task.totalSubtasks" class="task-progress">
          <div
            class="task-progress-track"
            role="progressbar"
            aria-valuemin="0"
            :aria-valuenow="task.submittedSubtasks"
            :aria-valuemax="task.totalSubtasks"
            :aria-label="`${task.title} 的子任务完成进度`"
          >
            <span :style="{ width: `${progressPercent(task)}%` }"></span>
          </div>
          <span class="task-progress-label">
            已提交 {{ task.submittedSubtasks }} / {{ task.totalSubtasks }}
            <span v-if="task.points > 0"> · 通过后 +{{ task.awardedPoints ?? task.points }} 积分</span>
          </span>
        </div>
        <p v-if="task.pointsSkippedReason" class="task-card-skip">本次未计分：{{ task.pointsSkippedReason }}</p>
        <p v-if="task.status === 'REJECTED' && task.reviewComment" class="task-card-reject">
          驳回意见：{{ task.reviewComment }}
        </p>
        <footer><ClipboardCheck :size="16" aria-hidden="true" />打开任务</footer>
      </RouterLink>
    </section>
  </PortalShell>
</template>
