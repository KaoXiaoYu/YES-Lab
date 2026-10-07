<script setup>
import { computed } from 'vue'

const props = defineProps({
  submitted: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  taskSubmitted: Boolean,
  label: { type: String, default: '子任务提交进度' },
  accessibleLabel: { type: String, default: '任务提交进度' },
})
const complete = computed(() => (props.total > 0 ? props.submitted >= props.total : props.taskSubmitted))
const percent = computed(() =>
  props.total > 0 ? Math.min(100, Math.max(0, (props.submitted / props.total) * 100)) : complete.value ? 100 : 0,
)
const valueText = computed(() =>
  props.total > 0 ? `已提交 ${props.submitted} / ${props.total}` : complete.value ? '已提交任务' : '尚未提交任务',
)
</script>

<template>
  <div class="submission-progress" :class="{ 'is-complete': complete }">
    <div class="submission-progress-label">
      <span>{{ complete ? '提交完成' : label }}</span>
      <strong>{{ total > 0 ? `${submitted} / ${total}` : `${percent}%` }}</strong>
    </div>
    <div
      class="submission-progress-track"
      role="progressbar"
      :aria-label="accessibleLabel"
      :aria-valuenow="percent"
      :aria-valuetext="valueText"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <span class="submission-progress-fill" :style="{ width: `${percent}%` }" />
    </div>
    <div v-if="$slots.default" class="submission-progress-note"><slot /></div>
  </div>
</template>

<style scoped>
.submission-progress {
  display: grid;
  gap: 8px;
  width: 100%;
  min-width: 0;
  margin: 8px 0;
  flex-basis: 100%;
  color: var(--admin-muted, var(--color-muted-foreground));
}
.submission-progress-label {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  line-height: 1.5;
}
.submission-progress-track {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--admin-border, var(--color-border));
}
.submission-progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--admin-muted, var(--color-muted-foreground));
}
.is-complete .submission-progress-label {
  color: var(--admin-success, var(--color-success));
}
.is-complete .submission-progress-fill {
  background: var(--admin-success, var(--color-success));
}
.submission-progress-note {
  font-size: 14px;
  line-height: 1.5;
}
</style>
