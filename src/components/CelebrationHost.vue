<script setup>
import { animate } from 'motion'
import { PartyPopper, X } from '@lucide/vue'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { celebrationState, closeCelebration } from '../services/celebrate'

const router = useRouter()
const card = ref(null)
const canvas = ref(null)
let closeTimer = null
let frame = 0

watch(
  () => celebrationState.id,
  async () => {
    if (!celebrationState.open) return
    await nextTick()
    if (card.value) {
      animate(
        card.value,
        { opacity: [0, 1], transform: ['translateY(24px) scale(0.92)', 'translateY(0) scale(1)'] },
        {
          type: 'spring',
          stiffness: 420,
          damping: 24,
        },
      )
    }
    burst()
    scheduleClose()
  },
)

function scheduleClose(delay = 3800) {
  clearTimeout(closeTimer)
  closeTimer = setTimeout(dismiss, delay)
}

function holdOpen() {
  clearTimeout(closeTimer)
}

async function dismiss() {
  clearTimeout(closeTimer)
  if (!celebrationState.open) return
  if (card.value) {
    await animate(card.value, { opacity: 0, transform: 'translateY(12px) scale(0.96)' }, { duration: 0.18 }).finished
  }
  closeCelebration()
}

async function runAction() {
  const target = celebrationState.actionTo
  await dismiss()
  if (target) router.push(target)
}

function palette() {
  const styles = getComputedStyle(document.documentElement)
  const pick = (name, fallback) => styles.getPropertyValue(name).trim() || fallback
  return [
    pick('--color-accent', '#1d4ed8'),
    pick('--color-success', '#16a34a'),
    pick('--color-warning', '#d97706'),
    pick('--color-primary', '#1e3a5f'),
    '#f472b6',
  ]
}

function burst() {
  const element = canvas.value
  if (!element) return
  cancelAnimationFrame(frame)
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  const width = window.innerWidth
  const height = window.innerHeight
  element.width = width * ratio
  element.height = height * ratio
  const context = element.getContext('2d')
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  const colors = palette()
  const originX = width / 2
  const originY = height * 0.62
  const pieces = Array.from({ length: 110 }, () => {
    const angle = -Math.PI / 2 + (Math.random() - 0.5) * 1.9
    const speed = 7 + Math.random() * 9
    return {
      x: originX,
      y: originY,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      size: 5 + Math.random() * 6,
      spin: Math.random() * Math.PI,
      color: colors[Math.floor(Math.random() * colors.length)],
    }
  })
  let tick = 0
  const step = () => {
    tick += 1
    context.clearRect(0, 0, width, height)
    for (const piece of pieces) {
      piece.vy += 0.28
      piece.vx *= 0.986
      piece.x += piece.vx
      piece.y += piece.vy
      piece.spin += 0.18
      context.save()
      context.translate(piece.x, piece.y)
      context.rotate(piece.spin)
      context.globalAlpha = Math.max(0, 1 - tick / 140)
      context.fillStyle = piece.color
      context.fillRect(-piece.size / 2, -piece.size / 4, piece.size, piece.size / 2)
      context.restore()
    }
    if (tick < 140) frame = requestAnimationFrame(step)
    else context.clearRect(0, 0, width, height)
  }
  frame = requestAnimationFrame(step)
}

function onKeydown(event) {
  if (event.key === 'Escape' && celebrationState.open) dismiss()
}
window.addEventListener('keydown', onKeydown)

onBeforeUnmount(() => {
  clearTimeout(closeTimer)
  cancelAnimationFrame(frame)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div v-if="celebrationState.open" class="ui-celebration">
    <canvas ref="canvas" class="ui-celebration-confetti" aria-hidden="true"></canvas>
    <section
      ref="card"
      class="ui-celebration-card"
      role="status"
      aria-live="polite"
      @mouseenter="holdOpen"
      @mouseleave="scheduleClose(1600)"
      @focusin="holdOpen"
    >
      <span class="ui-celebration-icon"><PartyPopper :size="22" aria-hidden="true" /></span>
      <div>
        <strong>{{ celebrationState.title }}</strong>
        <p v-if="celebrationState.message">{{ celebrationState.message }}</p>
      </div>
      <button v-if="celebrationState.actionLabel" class="ui-celebration-action" type="button" @click="runAction">
        {{ celebrationState.actionLabel }}
      </button>
      <button class="ui-celebration-close" type="button" aria-label="关闭" @click="dismiss">
        <X :size="16" aria-hidden="true" />
      </button>
    </section>
  </div>
</template>
