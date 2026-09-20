<script setup>
import { onMounted, ref } from 'vue'
import { getAssignmentSubtaskSubmissions } from '../services/authApi'

const props = defineProps({
  taskId: { type: String, default: '' },
  assignmentId: { type: String, required: true },
})

const rows = ref([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    rows.value = await getAssignmentSubtaskSubmissions(props.taskId, props.assignmentId)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="task-submissions" aria-label="该对象的子任务提交内容">
    <p v-if="loading" class="empty-note">正在读取提交内容…</p>
    <p v-else-if="errorMessage" class="portal-state error inline" role="alert">{{ errorMessage }}</p>
    <p v-else-if="!rows.length" class="empty-note">该任务没有子任务。</p>
    <ol v-else>
      <li v-for="row in rows" :key="row.subtaskId" :data-submitted="row.submitted">
        <header>
          <strong>{{ row.title }}</strong>
          <span>{{ row.submitted ? '已提交' : '未提交' }}</span>
        </header>
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div v-if="row.contentHtml" class="task-submissions-content" v-html="row.contentHtml"></div>
        <p v-else class="empty-note">尚未提交内容。</p>
        <small v-if="row.submittedAt">提交时间：{{ new Date(row.submittedAt).toLocaleString('zh-CN') }}</small>
      </li>
    </ol>
  </section>
</template>
