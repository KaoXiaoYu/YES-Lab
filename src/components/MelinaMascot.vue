<script setup>
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'
import melinaArtwork from '../assets/melina-mail.svg'
import particleTargets from '../assets/melina-particles'

const uid = `melina-${useId()}`
const phase = ref('loading')
const greeting = ref(false)
let entranceTimer
let greetingTimer
let motionPreference

// Anatomical layers follow the same illustration, so the resting silhouette stays intact.
const headOutline = 'M70 0H490V307H378L377 282 351 271 327 263Q274 273 222 262L204 283 193 308H70Z'
const offeringOutline = 'M230 286 318 287 317 310Q330 303 339 322L345 355 341 372Q331 385 321 374L309 351 236 349 226 369Q211 381 198 368L194 352 205 330Q205 303 229 306Z'
// Keep the original eye and upper lashes as separate moving vector layers.
// The contour follows the lash tips; the small hair backing restores what they cover.
const eyeOutline = 'M186 174Q213 171 238 184L242 189 239 189 245 198 237 196 235 218 230 223 200 225 194 221 188 213 176 205 164 195 173 196 171 186 181 184Z'
const upperLashOutline = 'M164 173H247V199L237 196Q228 185 211 186Q196 186 187 196L187 212 177 205 164 200Z'
const particles = particleTargets.map(([x, y, color], index) => {
  const delay = (index % 13) * 12
  const duration = 540 + (index % 9) * 15
  return {
    x, y, color, radius: 3 + (index % 4) * .65,
    style: {
      '--wind-x': `${Math.round(Math.sin(index * 2.4) * 155 - 105)}px`,
      '--wind-y': `${Math.round(Math.cos(index * 1.7) * 160 + 80)}px`,
      '--arc-x': `${Math.round(Math.sin(index * 1.3) * 55 - 60)}px`,
      '--arc-y': `${Math.round(Math.cos(index * 1.9) * 65 - 25)}px`,
      '--flight-delay': `${delay}ms`,
      '--flight-duration': `${duration}ms`,
      '--reveal-delay': `${Math.round(delay + duration * .64)}ms`,
    },
  }
})

function finishEntrance() {
  window.clearTimeout(entranceTimer)
  phase.value = 'ready'
}

function beginEntrance() {
  if (phase.value !== 'loading') return
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return finishEntrance()
  phase.value = 'forming'
  entranceTimer = window.setTimeout(finishEntrance, 900)
}

function greet() {
  if (phase.value === 'loading' || greeting.value) return
  // A tap during assembly immediately brings her forward to answer the user.
  if (phase.value === 'forming') finishEntrance()
  greeting.value = true
  greetingTimer = window.setTimeout(() => { greeting.value = false }, 1900)
}

function handleMotionPreference(event) {
  if (event.matches) finishEntrance()
}

onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  motionPreference.addEventListener('change', handleMotionPreference)
})

onBeforeUnmount(() => {
  window.clearTimeout(entranceTimer)
  window.clearTimeout(greetingTimer)
  motionPreference?.removeEventListener('change', handleMotionPreference)
})
</script>

<template>
  <button type="button" class="melina-mascot" :class="{ 'is-greeting': greeting }" :data-phase="phase"
    aria-label="和梅琳娜打招呼" title="点点我，和我打个招呼" @click="greet">
    <svg viewBox="56 -12 448 588" fill="none" aria-hidden="true" focusable="false">
      <defs>
        <g :id="`${uid}-art`">
          <image :href="melinaArtwork" x="70" y="0" width="420" height="560" @load="beginEntrance" />
        </g>
        <clipPath :id="`${uid}-head`" clipPathUnits="userSpaceOnUse"><path :d="headOutline" /></clipPath>
        <clipPath :id="`${uid}-letter`" clipPathUnits="userSpaceOnUse"><path :d="offeringOutline" /></clipPath>
        <clipPath :id="`${uid}-eye`" clipPathUnits="userSpaceOnUse"><path :d="eyeOutline" /></clipPath>
        <clipPath :id="`${uid}-upper-lash`" clipPathUnits="userSpaceOnUse"><path :d="upperLashOutline" /></clipPath>
        <mask :id="`${uid}-eye-globe`" maskUnits="userSpaceOnUse" x="160" y="170" width="90" height="60" style="mask-type: luminance">
          <path :d="eyeOutline" fill="white" /><path :d="upperLashOutline" fill="black" />
        </mask>
        <linearGradient :id="`${uid}-skin`" x1="0" y1="0" x2="1" y2="1">
          <stop stop-color="#f8dfd6" /><stop offset=".6" stop-color="#fce9e0" /><stop offset="1" stop-color="#fbe6dd" />
        </linearGradient>
        <mask :id="`${uid}-body`" maskUnits="userSpaceOnUse" x="50" y="-12" width="460" height="588" style="mask-type: luminance">
          <rect x="50" y="-12" width="460" height="588" fill="white" />
          <path :d="headOutline" fill="black" /><path :d="offeringOutline" fill="black" />
        </mask>
        <mask v-if="phase === 'forming'" :id="`${uid}-assembly`" maskUnits="userSpaceOnUse" x="50" y="-12" width="460" height="588" style="mask-type: luminance">
          <circle v-for="(particle, index) in particles" :key="index" class="melina-reveal-cell"
            :cx="particle.x" :cy="particle.y" r="37" fill="white" :style="particle.style" />
          <rect class="melina-reveal-finish" x="50" y="-12" width="460" height="588" fill="white" />
        </mask>
      </defs>

      <g class="melina-character" :mask="phase === 'forming' ? `url(#${uid}-assembly)` : undefined">
        <g class="melina-torso">
          <!-- Clothing behind the original hands is revealed only as the letter lifts. -->
          <path :d="offeringOutline" fill="#5f5065" />
          <path d="M231 340q42 8 81 0l8 45h-98Z" fill="#b6a28f" />
          <path d="M211 257Q271 271 342 257L354 306H201Z" fill="#f4e8d4" />
          <use :href="`#${uid}-art`" :mask="`url(#${uid}-body)`" />
        </g>
        <g class="melina-head">
          <use :href="`#${uid}-art`" :clip-path="`url(#${uid}-head)`" />
          <g :clip-path="`url(#${uid}-eye)`">
            <path :d="eyeOutline" :fill="`url(#${uid}-skin)`" />
            <!-- Restore the lock behind the outer lash tips, inside the eye cutout only. -->
            <path d="M160 170H187Q177 186 175 198Q178 213 188 228H160Z" fill="#cba195" />
            <path d="M187 170Q177 186 175 198Q178 213 188 228" stroke="#704d45" stroke-width="1.8" />
            <g class="melina-eye-globe">
              <use :href="`#${uid}-art`" :mask="`url(#${uid}-eye-globe)`" />
            </g>
            <g class="melina-upper-lash">
              <use :href="`#${uid}-art`" :clip-path="`url(#${uid}-upper-lash)`" />
            </g>
          </g>
          <g class="melina-smile">
            <ellipse cx="267" cy="234" rx="17" ry="9" fill="#fce9e2" />
            <path d="M256 234q12 6 24-3" fill="none" stroke="#67404b" stroke-width="1.8" stroke-linecap="round" />
          </g>
        </g>
        <g class="melina-letter-hands">
          <use :href="`#${uid}-art`" :clip-path="`url(#${uid}-letter)`" />
        </g>
      </g>

      <g v-if="phase === 'forming'" class="melina-particle-cloud" pointer-events="none">
        <g v-for="(particle, index) in particles" :key="index" :transform="`translate(${particle.x} ${particle.y})`">
          <g class="melina-particle" :style="particle.style">
            <path d="M-13 4Q-6 0 0 0" stroke="#bd985b" stroke-width="1.5" opacity=".7" />
            <circle :r="particle.radius + 2" fill="#e9c982" opacity=".2" />
            <circle :r="particle.radius" :fill="index % 3 ? '#c69b52' : particle.color" />
          </g>
        </g>
      </g>
      <g class="melina-sparkles" fill="#dfb45f">
        <path d="m476 328 5 15 15 5-15 5-5 15-5-15-15-5 15-5Z" />
        <path d="m85 354 3 10 10 3-10 3-3 10-3-10-10-3 10-3Z" />
      </g>
    </svg>
    <span class="melina-response" aria-live="polite">{{ greeting ? '梅琳娜轻轻举起信封，抬眼回应了你。' : '' }}</span>
  </button>
</template>

<style scoped>
.melina-mascot {
  display: block; flex: 0 0 auto; width: 88px; height: 124px; min-width: 44px;
  padding: 0; border: 0; border-radius: 16px; background: transparent;
  cursor: pointer; touch-action: manipulation; transition: background-color 180ms ease-out;
}
.melina-mascot svg { display: block; width: 100%; height: 100%; overflow: visible; }
.melina-mascot:focus-visible { outline: 2px solid var(--color-secondary); outline-offset: 3px; }
.melina-mascot:active { background: var(--color-surface-soft); }
.melina-mascot[data-phase="loading"] .melina-character { opacity: 0; }
.melina-head { transform-origin: 277px 269px; }
.melina-torso { transform-origin: 278px 542px; }
.melina-letter-hands { transform-origin: 273px 356px; }
.melina-eye-globe, .melina-upper-lash { transform-origin: 207px 220px; }
.melina-mascot[data-phase="ready"] .melina-torso { animation: melina-breathe 4.8s ease-in-out infinite; }
.melina-mascot[data-phase="ready"] .melina-eye-globe { animation: melina-blink 6.5s linear infinite; }
.melina-mascot[data-phase="ready"] .melina-upper-lash { animation: melina-blink-lash 6.5s linear infinite; }
.melina-smile, .melina-sparkles { opacity: 0; }
.is-greeting .melina-head { animation: melina-nod 1.9s ease-in-out; }
.is-greeting .melina-letter-hands { animation: melina-offer-letter 1.9s cubic-bezier(.4,0,.2,1); }
.melina-mascot.is-greeting .melina-eye-globe { animation: melina-greeting-blink 1.9s linear; }
.melina-mascot.is-greeting .melina-upper-lash { animation: melina-greeting-lash 1.9s linear; }
.is-greeting .melina-smile { opacity: 1; animation: melina-smile 1.9s ease-out; }
.is-greeting .melina-sparkles { opacity: 1; }
.melina-particle { opacity: 0; animation: melina-gather var(--flight-duration) cubic-bezier(.22,.55,.38,1) var(--flight-delay) both; }
.melina-reveal-cell { transform-box: fill-box; transform-origin: center; animation: melina-materialize 240ms ease-out var(--reveal-delay) both; }
.melina-reveal-finish { animation: melina-complete 120ms linear 740ms both; }
.melina-response { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@keyframes melina-gather {
  0% { opacity: 0; transform: translate(var(--wind-x), var(--wind-y)) scale(.4); }
  18% { opacity: .8; }
  52% { opacity: 1; transform: translate(var(--arc-x), var(--arc-y)) scale(1); }
  82% { opacity: .95; transform: translate(0, 0) scale(.8); }
  100% { opacity: 0; transform: translate(0, 0) scale(.25); }
}
@keyframes melina-materialize { from { opacity: 0; transform: scale(.1); } to { opacity: 1; transform: scale(1); } }
@keyframes melina-complete { from { opacity: 0; } to { opacity: 1; } }
@keyframes melina-breathe { 0%, 100% { transform: scaleY(1); } 50% { transform: scaleY(1.006); } }
@keyframes melina-blink { 0%, 87%, 92.4%, 100% { transform: scaleY(1); opacity: 1; } 89%, 89.6% { transform: scaleY(0); opacity: 0; } }
@keyframes melina-blink-lash { 0%, 87%, 92.4%, 100% { transform: translateY(0) scaleY(1); } 89%, 89.6% { transform: translateY(-7px) scaleY(.32); } }
@keyframes melina-greeting-blink { 0%, 6%, 25%, 100% { transform: scaleY(1); opacity: 1; } 13%, 15% { transform: scaleY(0); opacity: 0; } }
@keyframes melina-greeting-lash { 0%, 6%, 25%, 100% { transform: translateY(0) scaleY(1); } 13%, 15% { transform: translateY(-7px) scaleY(.32); } }
@keyframes melina-nod { 0%, 100% { transform: rotate(0); } 32% { transform: rotate(2deg); } 68% { transform: rotate(-.6deg); } }
@keyframes melina-offer-letter {
  0%, 100% { transform: translateY(0) rotate(0); }
  32%, 64% { transform: translateY(-12px) rotate(-1.5deg); }
  82% { transform: translateY(-3px) rotate(-.3deg); }
}
@keyframes melina-smile { 0%, 8%, 100% { opacity: 0; } 20%, 80% { opacity: 1; } }
@media (hover: hover) and (pointer: fine) { .melina-mascot:hover { background: var(--color-surface-soft); } }
@media (prefers-reduced-motion: reduce) {
  .melina-mascot, .melina-mascot * { animation: none !important; transition: none !important; }
  .melina-particle-cloud { display: none; }
}
</style>
