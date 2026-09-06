import { ref } from 'vue'

export const theme = ref(document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light')

function applyTheme(value) {
  theme.value = value === 'dark' ? 'dark' : 'light'
  document.documentElement.dataset.theme = theme.value
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme.value === 'dark' ? '#0b1423' : '#f8fafc')
}

export function toggleTheme() {
  applyTheme(theme.value === 'dark' ? 'light' : 'dark')
  try { localStorage.setItem('yeslab-theme', theme.value) } catch { /* Theme still works when storage is unavailable. */ }
}

window.addEventListener('storage', (event) => {
  if (event.key === 'yeslab-theme') applyTheme(event.newValue)
})
applyTheme(theme.value)
