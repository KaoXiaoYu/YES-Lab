import { onBeforeUnmount, onMounted, unref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { confirmAction } from '../services/confirm'

const discardOptions = {
  title: '放弃未保存的修改？',
  message: '当前修改还没有保存，离开后将丢失。',
  confirmText: '放弃修改',
  cancelText: '继续编辑',
  tone: 'danger',
}

/**
 * Protects in-progress edits: route changes, tab closing and in-page switches
 * (call `confirmDiscard()` before replacing the edited record).
 * `isDirty` may be a ref, computed, or function.
 */
export function useUnsavedGuard(isDirty, options = {}) {
  const dirty = () => Boolean(typeof isDirty === 'function' ? isDirty() : unref(isDirty))
  const settings = { ...discardOptions, ...options }

  async function confirmDiscard(overrides) {
    if (!dirty()) return true
    return confirmAction({ ...settings, ...overrides })
  }

  function beforeUnload(event) {
    if (!dirty()) return
    event.preventDefault()
    event.returnValue = ''
  }

  onBeforeRouteLeave(() => confirmDiscard())
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

  return { confirmDiscard }
}
