<script setup>
import TaskSubmissionProgress from '../components/TaskSubmissionProgress.vue'
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
// 悬赏是「提交即完成」，没有待确认；APPROVED 对悬赏的含义是「已完成」。
const bountyStatusLabels = { PENDING: '进行中', APPROVED: '已完成', REJECTED: '已驳回', ABANDONED: '已放弃' }

function statusLabel(task) {
  if (task.taskType === 'BOUNTY') return bountyStatusLabels[task.status] || task.status
  return statusLabels[task.status] || task.status
}

const filtered = computed(() =>
  tasks.value.filter((task) => statusFilter.value === 'ALL' || task.status === statusFilter.value),
)

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
          <span :data-status="task.status">{{ statusLabel(task) }}</span>
          <b>{{ taskStatusLabels[task.taskStatus] || task.taskStatus }}</b>
        </header>
        <h2>{{ task.title }}</h2>
        <p class="task-card-dates">
          {{ task.startDate || '未设置开始' }} — {{ task.endDate || '未设置截止' }}
          <span v-if="task.expired" class="overdue">已截止</span>
          <span v-else-if="task.overdue" class="overdue">已逾期</span>
        </p>
        <TaskSubmissionProgress
          :submitted="task.submittedSubtasks"
          :total="task.totalSubtasks"
          :task-submitted="['SUBMITTED', 'APPROVED'].includes(task.status)"
          :accessible-label="`${task.title}的提交进度`"
        >
          <span v-if="task.points > 0 && task.status !== 'APPROVED'"
            >{{ task.taskType === 'BOUNTY' ? '完成后' : '通过后' }} +{{ task.points }} 积分</span
          >
        </TaskSubmissionProgress>
        <p v-if="task.taskType === 'BOUNTY'" class="bounty-mine">
          <template v-if="task.completionRank">完成名次第 {{ task.completionRank }} 名</template>
          <template v-if="task.prizeSlots">
            <template v-if="task.completionRank"> · </template>
            <!-- 只在有结论（已完成 / 已驳回）时谈获奖，进行中就说「未获得奖金」会误导 -->
            <template v-if="task.status === 'APPROVED' || task.status === 'REJECTED'">
              {{ task.prizeAwarded ? '已获得奖金' : '未获得奖金' }}
            </template>
            <template v-else>奖金 {{ task.prizeSlots }} 份，按完成先后发放</template>
          </template>
          <template v-if="task.prizeDescription"> · {{ task.prizeDescription }}</template>
        </p>
        <p v-if="task.status === 'APPROVED' && task.points > 0" class="task-card-settle">
          {{ task.pointsSettled ? `积分已结算：+${task.awardedPoints ?? 0}` : '积分待结算：任务到期后统一发放' }}
        </p>
        <p v-if="task.pointsSkippedReason" class="task-card-skip">本次未计分：{{ task.pointsSkippedReason }}</p>
        <p v-if="task.status === 'REJECTED' && task.reviewComment" class="task-card-reject">
          驳回意见：{{ task.reviewComment }}
        </p>
        <footer><ClipboardCheck :size="16" aria-hidden="true" />打开任务</footer>
      </RouterLink>
    </section>
  </PortalShell>
</template>
