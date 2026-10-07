import { onBeforeUnmount, watch } from 'vue'
import { authState } from '../services/authApi'
import { toast } from '../services/toast'

const prefix = 'yeslab:draft:'
const maxAgeMs = 14 * 24 * 60 * 60 * 1000

function storageKey(key) {
  const owner = authState.account?.id ?? authState.account?.username ?? 'guest'
  return `${prefix}${owner}:${typeof key === 'function' ? key() : key}`
}

function read(key) {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey(key)) || 'null')
    if (!stored || Date.now() - stored.savedAt > maxAgeMs) return null
    return stored.value
  } catch {
    return null
  }
}

/**
 * Keeps unsent long-form text in this browser so a closed tab or a misclick does not lose it.
 *
 * Saving starts only after `restore()` runs, so a form that loads its server data first
 * never overwrites an older draft with an empty initial state. Call `clear()` after a
 * successful submit. `snapshot` returns a plain, JSON-serialisable value.
 */
export function useDraft(key, snapshot, { delay = 600 } = {}) {
  let armed = false
  let timer = null

  function save() {
    if (!armed) return
    try {
      const value = snapshot()
      localStorage.setItem(storageKey(key), JSON.stringify({ savedAt: Date.now(), value }))
    } catch {
      // Storage may be full or disabled; drafts are a convenience only.
    }
  }

  const stop = watch(
    snapshot,
    () => {
      if (!armed) return
      clearTimeout(timer)
      timer = setTimeout(save, delay)
    },
    { deep: true },
  )

  /** Applies a stored draft (if any and different from the current state), then starts saving. */
  function restore(apply, { announce = true } = {}) {
    const stored = read(key)
    const current = JSON.stringify(snapshot())
    let restored = false
    if (stored != null && JSON.stringify(stored) !== current) {
      apply(stored)
      restored = true
      if (announce) toast.info('已恢复上次未提交的草稿。', { title: '草稿' })
    }
    armed = true
    return restored
  }

  function clear() {
    clearTimeout(timer)
    try {
      localStorage.removeItem(storageKey(key))
    } catch {
      // ignore
    }
  }

  onBeforeUnmount(() => {
    stop()
    if (timer) {
      clearTimeout(timer)
      save()
    }
  })

  return { restore, clear, hasDraft: () => read(key) != null }
}
