import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'

const stack = []
let lockCount = 0

function lockScroll() {
  if (lockCount++ > 0) return
  const gap = window.innerWidth - document.documentElement.clientWidth
  if (gap > 0) document.body.style.paddingRight = `${gap}px`
  document.body.classList.add('modal-open')
}

function unlockScroll() {
  if (lockCount === 0 || --lockCount > 0) return
  document.body.classList.remove('modal-open')
  document.body.style.paddingRight = ''
}

const focusable =
  'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Shared behaviour for dialogs and drawers: scroll lock, focus moves inside and
 * returns to the opener, Tab stays within the layer, and Escape only closes the
 * top-most layer (a confirm dialog above a drawer closes first).
 */
export function useModalLayer(container, { active, onEscape, initialFocus }) {
  const token = {}
  let opener = null
  let mounted = false

  function release() {
    const index = stack.indexOf(token)
    if (index < 0) return
    stack.splice(index, 1)
    unlockScroll()
    const target = opener
    opener = null
    if (target?.isConnected) nextTick(() => target.focus({ preventScroll: true }))
  }

  async function engage() {
    if (stack.includes(token)) return
    opener = document.activeElement
    stack.push(token)
    lockScroll()
    await nextTick()
    const root = container.value
    if (!root) return
    const target = initialFocus?.() || root.querySelector('[autofocus]') || root.querySelector(focusable) || root
    target.focus({ preventScroll: true })
  }

  function handleKeydown(event) {
    if (stack[stack.length - 1] !== token) return
    if (event.key === 'Escape') {
      event.preventDefault()
      onEscape?.()
      return
    }
    if (event.key !== 'Tab') return
    const root = container.value
    if (!root) return
    const items = [...root.querySelectorAll(focusable)].filter((item) => item.offsetParent !== null)
    if (!items.length) {
      event.preventDefault()
      return
    }
    const first = items[0]
    const last = items[items.length - 1]
    if (event.shiftKey && (document.activeElement === first || !root.contains(document.activeElement))) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  watch(
    active,
    (isActive) => {
      if (!mounted) return
      if (isActive) engage()
      else release()
    },
    { flush: 'post' },
  )

  onMounted(() => {
    mounted = true
    document.addEventListener('keydown', handleKeydown)
    if (active()) engage()
  })

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', handleKeydown)
    release()
  })
}
