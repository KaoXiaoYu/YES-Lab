<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const glow = ref(null)
let media
let frame = 0
let x = 0
let y = 0
let targetX = 0
let targetY = 0
let velocityX = 0
let velocityY = 0
let stretch = 0
let stretchVelocity = 0
let angle = 0
let hue = 0
let lastTime = 0
let initialized = false
let listening = false

function hide() {
  cancelAnimationFrame(frame)
  frame = 0
  initialized = false
  velocityX = velocityY = stretch = stretchVelocity = 0
  if (glow.value) glow.value.style.opacity = '0'
}

function render() {
  const deformation = Math.max(-0.025, Math.min(0.12, stretch))
  glow.value.style.transform = `translate3d(${x}px, ${y}px, 0) rotate(${angle}rad) scale(${1 + deformation}, ${1 - deformation * 0.4})`
  glow.value.style.filter = `hue-rotate(${hue}deg)`
}

function spring(now) {
  frame = 0
  if (!glow.value || !listening || document.hidden) return
  const elapsed = Math.min((now - lastTime) / 1000, 0.064)
  lastTime = now
  // Small integration steps keep the same spring feel at 30/60/120 Hz.
  const steps = Math.max(1, Math.ceil(elapsed / (1 / 120)))
  const dt = elapsed / steps
  const previousX = x
  const previousY = y
  for (let i = 0; i < steps; i++) {
    velocityX += ((targetX - x) * 180 - velocityX * 12) * dt
    velocityY += ((targetY - y) * 180 - velocityY * 12) * dt
    x += velocityX * dt
    y += velocityY * dt
    const targetStretch = Math.min(Math.hypot(velocityX, velocityY) / 9000, 0.12)
    stretchVelocity += ((targetStretch - stretch) * 240 - stretchVelocity * 16) * dt
    stretch += stretchVelocity * dt
  }
  hue = (hue + Math.hypot(x - previousX, y - previousY) * 0.18) % 360
  const speed = Math.hypot(velocityX, velocityY)
  if (speed > 5) angle = Math.atan2(velocityY, velocityX)
  const settled =
    Math.hypot(targetX - x, targetY - y) < 0.15 &&
    speed < 2 &&
    Math.abs(stretch) < 0.0005 &&
    Math.abs(stretchVelocity) < 0.005
  if (settled) {
    x = targetX
    y = targetY
    velocityX = velocityY = stretch = stretchVelocity = 0
  }
  render()
  if (!settled) frame = requestAnimationFrame(spring)
}

function move(event) {
  if (event.pointerType !== 'mouse' || document.hidden) return
  targetX = event.clientX
  targetY = event.clientY
  if (!initialized) {
    initialized = true
    x = targetX
    y = targetY
    render()
  }
  glow.value.style.opacity = '1'
  if (frame) return
  lastTime = performance.now()
  frame = requestAnimationFrame(spring)
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
