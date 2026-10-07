<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const glow = ref(null)
let media
let frame = 0
let x = 0
let y = 0
let listening = false

function hide() {
  cancelAnimationFrame(frame)
  frame = 0
  if (glow.value) glow.value.style.opacity = '0'
}

function move(event) {
  if (event.pointerType !== 'mouse' || document.hidden) return
  x = event.clientX
  y = event.clientY
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    if (!glow.value) return
    glow.value.style.transform = `translate3d(${x}px, ${y}px, 0)`
    glow.value.style.opacity = '1'
  })
}

function visibilityChanged() {
  if (document.hidden) hide()
}

function update() {
  const enabled = media.matches
  if (enabled === listening) return
  listening = enabled
  if (enabled) {
    window.addEventListener('pointermove', move, { passive: true })
  } else {
    window.removeEventListener('pointermove', move)
    hide()
  }
}

onMounted(() => {
  media = window.matchMedia(
    '(min-width: 761px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)',
  )
  media.addEventListener('change', update)
  window.addEventListener('blur', hide)
  document.documentElement.addEventListener('pointerleave', hide)
  document.addEventListener('visibilitychange', visibilityChanged)
  update()
})

onBeforeUnmount(() => {
  media?.removeEventListener('change', update)
  window.removeEventListener('pointermove', move)
  window.removeEventListener('blur', hide)
  document.documentElement.removeEventListener('pointerleave', hide)
  document.removeEventListener('visibilitychange', visibilityChanged)
  hide()
})
</script>

<template>
  <div ref="glow" class="ah-pointer-glow" aria-hidden="true"></div>
</template>
