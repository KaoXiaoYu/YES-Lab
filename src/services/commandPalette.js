import { reactive } from 'vue'

export const commandPaletteState = reactive({ open: false })

export function openCommandPalette() {
  commandPaletteState.open = true
}

export function closeCommandPalette() {
  commandPaletteState.open = false
}

export const commandShortcutLabel =
  typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent)
    ? '⌘K'
    : 'Ctrl K'
