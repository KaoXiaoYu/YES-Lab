import { nextTick, onBeforeUnmount, onMounted } from 'vue'

export function useDismissibleLayer(root, { isOpen, close, focusTarget }) {
  const handleClick = (event) => {
    const element = root.value
    if (!element || !isOpen() || element.contains(event.target)) return
    close()
  }

  const handleKeydown = (event) => {
    if (event.key !== 'Escape' || !isOpen()) return
    close()
    nextTick(() => focusTarget?.()?.focus())
  }

  onMounted(() => {
    document.addEventListener('click', handleClick)
    document.addEventListener('keydown', handleKeydown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', handleClick)
    document.removeEventListener('keydown', handleKeydown)
  })
}
