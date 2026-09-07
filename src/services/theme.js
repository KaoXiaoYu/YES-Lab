import { ref } from 'vue'

export const theme = ref(document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light')

function applyTheme(value) {
  theme.value = value === 'dark' ? 'dark' : 'light'
  document.documentElement.dataset.theme = theme.value
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme.value === 'dark' ? '#0b1423' : '#f8fafc')
}

function persistTheme() {
  try { localStorage.setItem('yeslab-theme', theme.value) } catch { /* Theme still works when storage is unavailable. */ }
}

export function toggleTheme(event) {
  const nextTheme = theme.value === 'dark' ? 'light' : 'dark'
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!document.startViewTransition || reducedMotion) {
    applyTheme(nextTheme)
    persistTheme()
    return
  }

  const x = event?.clientX ?? window.innerWidth
  const y = event?.clientY ?? 0
  const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
  const transition = document.startViewTransition(() => {
    applyTheme(nextTheme)
    persistTheme()
  })
  transition.ready.then(() => document.documentElement.animate(
    { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
    { duration: 560, easing: 'cubic-bezier(.22, 1, .36, 1)', pseudoElement: '::view-transition-new(root)' },
  )).catch(() => {})
}

window.addEventListener('storage', (event) => {
  if (event.key === 'yeslab-theme') applyTheme(event.newValue)
})
applyTheme(theme.value)
