import { reactive } from 'vue'

export const toasts = reactive([])
let nextId = 1
const timers = new Map()

/**
 * Transient operation feedback. Success and info messages dismiss themselves;
 * errors stay until closed so they are not missed.
 */
export function showToast(message, { tone = 'success', title = '', duration } = {}) {
  if (!message) return null
  const id = nextId++
  const timeout = duration ?? (tone === 'error' ? 0 : 4200)
  toasts.push({ id, tone, title, message, timeout })
  if (toasts.length > 4) dismissToast(toasts[0].id)
  if (timeout) scheduleDismiss(id, timeout)
  return id
}

export const toast = {
  success: (message, options) => showToast(message, { ...options, tone: 'success' }),
  error: (message, options) => showToast(message, { ...options, tone: 'error' }),
  info: (message, options) => showToast(message, { ...options, tone: 'info' }),
}

export function scheduleDismiss(id, timeout) {
  clearTimeout(timers.get(id))
  timers.set(
    id,
    setTimeout(() => dismissToast(id), timeout),
  )
}

export function pauseDismiss(id) {
  clearTimeout(timers.get(id))
  timers.delete(id)
}

export function dismissToast(id) {
  pauseDismiss(id)
  const index = toasts.findIndex((item) => item.id === id)
  if (index >= 0) toasts.splice(index, 1)
}
