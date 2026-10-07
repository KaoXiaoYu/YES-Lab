import { reactive } from 'vue'
import { toast } from './toast'

export const celebrationState = reactive({
  open: false,
  id: 0,
  title: '',
  message: '',
  actionLabel: '',
  actionTo: '',
})

/**
 * Marks a milestone (task finished, bounty claimed, member converted…) with a short,
 * dismissible celebration. Ordinary saves should use `toast` instead.
 * Under reduced motion it degrades to a success toast.
 */
export function celebrate({ title, message = '', actionLabel = '', actionTo = '' }) {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
    toast.success(message || title, { title: message ? title : '' })
    return
  }
  Object.assign(celebrationState, {
    open: true,
    id: celebrationState.id + 1,
    title,
    message,
    actionLabel,
    actionTo,
  })
}

export function closeCelebration() {
  celebrationState.open = false
}
