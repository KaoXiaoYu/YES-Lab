import { reactive } from 'vue'

const defaults = {
  open: false,
  eyebrow: 'SUBMISSION COMPLETE',
  title: '提交成功',
  message: '内容已经保存。',
  confirmLabel: '我知道了',
}

export const submissionFeedbackState = reactive({ ...defaults })

export function showSubmissionFeedback(options = {}) {
  Object.assign(submissionFeedbackState, defaults, options, { open: true })
}

export function closeSubmissionFeedback() {
  submissionFeedbackState.open = false
}
