import { reactive } from 'vue'

const defaults = {
  title: '确认操作',
  message: '',
  details: [],
  confirmText: '确认',
  cancelText: '取消',
  tone: 'default',
}

export const confirmState = reactive({ open: false, ...defaults, resolve: null })

/**
 * In-app replacement for window.confirm. Resolves true when the user confirms.
 * `tone: 'danger'` styles the action as destructive and moves initial focus to Cancel.
 */
export function confirmAction(options) {
  const settings = typeof options === 'string' ? { message: options } : options || {}
  confirmState.resolve?.(false)
  return new Promise((resolve) => {
    Object.assign(confirmState, defaults, settings, { open: true, resolve })
  })
}

export function settleConfirm(result) {
  const resolve = confirmState.resolve
  confirmState.open = false
  confirmState.resolve = null
  resolve?.(Boolean(result))
}
