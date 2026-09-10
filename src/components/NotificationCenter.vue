<script setup>
import { Bell, CheckCheck, X } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getNotifications, getNotificationVisibility, markAllNotificationsRead, markNotificationRead } from '../services/authApi'
import MelinaMascot from './MelinaMascot.vue'

const router = useRouter()
const inbox = ref({ unreadCount: 0, messages: [] })
const open = ref(false)
const toast = ref(null)
const initialized = ref(false)
const visible = ref(false)
const notificationRoot = ref(null)
const anchorStyle = ref({})
let pollTimer
let toastTimer

const unread = computed(() => inbox.value.messages.filter(message => !message.read))

onMounted(async () => {
  try {
    visible.value = (await getNotificationVisibility())?.visible !== false
  } catch {
    // Keep the message center available while upgrading an older backend.
    visible.value = true
  }
  if (!visible.value) return
  await nextTick()
  updatePosition()
  await refresh(true)
  pollTimer = window.setInterval(() => refresh(false), 15000)
  document.addEventListener('visibilitychange', handleVisibility)
  window.addEventListener('resize', updatePosition)
})

onBeforeUnmount(() => {
  window.clearInterval(pollTimer)
  window.clearTimeout(toastTimer)
  document.removeEventListener('visibilitychange', handleVisibility)
  window.removeEventListener('resize', updatePosition)
})

async function refresh(firstLoad) {
  try {
    const previousUnread = new Map(unread.value.map(message => [message.id, `${message.aggregationCount}:${message.updatedAt}`]))
    const next = await getNotifications()
    inbox.value = next || { unreadCount: 0, messages: [] }
    const fresh = inbox.value.messages.filter(message => !message.read
      && previousUnread.get(message.id) !== `${message.aggregationCount}:${message.updatedAt}`)
    if ((firstLoad && unread.value.length) || (!firstLoad && fresh.length)) {
      showToast(firstLoad ? unread.value : fresh)
    }
    initialized.value = true
  } catch {
    initialized.value = true
  }
}

function showToast(messages) {
  updatePosition()
  window.clearTimeout(toastTimer)
  toast.value = messages.length === 1
    ? { title: messages[0].title, summary: messages[0].summary }
    : { title: `你有 ${messages.length} 条新消息`, summary: '打开消息中心查看梅琳娜发来的通知。' }
  toastTimer = window.setTimeout(() => { toast.value = null }, 5000)
}

function togglePanel() {
  open.value = !open.value
  if (open.value) toast.value = null
  nextTick(updatePosition)
}

function updatePosition() {
  const root = notificationRoot.value
  if (!root) return
  const trigger = root.querySelector('.notification-trigger')?.getBoundingClientRect()
  const header = root.closest('.portal-topbar')?.getBoundingClientRect()
  anchorStyle.value = {
    '--notification-top': `${Math.max((header?.bottom || trigger?.bottom || 76) + 10, 12)}px`,
    '--notification-right': `${Math.max(window.innerWidth - (trigger?.right || window.innerWidth - 22), 12)}px`,
  }
}

async function openMessage(message) {
  if (!message.read) inbox.value = await markNotificationRead(message.id)
  open.value = false
  if (message.targetPath) router.push(message.targetPath)
}

async function readAll() {
  inbox.value = await markAllNotificationsRead()
}

function handleVisibility() {
  if (document.visibilityState === 'visible') refresh(false)
}
</script>

<template>
  <div v-if="visible" ref="notificationRoot" class="notification-center" :style="anchorStyle" @keydown.esc="open = false">
    <button type="button" class="notification-trigger" aria-label="打开站内消息"
      :aria-expanded="open" aria-controls="notification-panel" @click="togglePanel">
      <Bell :size="18" aria-hidden="true" />
      <span v-if="inbox.unreadCount" class="notification-badge">{{ inbox.unreadCount > 99 ? '99+' : inbox.unreadCount }}</span>
    </button>

    <section v-if="open" id="notification-panel" class="notification-panel" aria-label="站内消息">
      <header><div><span><strong>梅琳娜</strong><small>站内消息</small></span></div>
        <button v-if="inbox.unreadCount" type="button" @click="readAll"><CheckCheck :size="16" />全部已读</button>
      </header>
      <div class="notification-list">
        <div class="notification-companion">
          <MelinaMascot />
          <div><strong>{{ inbox.messages.length ? '你的信，我替你收好了。' : '我会在这里，等下一封信。' }}</strong><p>点点我，打个招呼吧。</p></div>
        </div>
        <button v-for="message in inbox.messages" :key="message.id" type="button" :class="{ unread: !message.read }" @click="openMessage(message)">
          <span class="notification-dot" aria-hidden="true" /><span><strong>{{ message.title }}</strong><p>{{ message.summary }}</p><small>{{ new Date(message.createdAt).toLocaleString('zh-CN') }}</small></span>
        </button>
        <p v-if="initialized && !inbox.messages.length" class="notification-empty">暂时没有站内消息。</p>
      </div>
    </section>

    <aside v-if="toast" class="notification-toast" role="status" aria-live="polite">
      <MelinaMascot class="notification-toast-mascot" /><div><small class="notification-sender">梅琳娜来信</small><strong>{{ toast.title }}</strong><p>{{ toast.summary }}</p></div>
      <button type="button" class="notification-toast-close" aria-label="关闭消息提醒" @click="toast = null"><X :size="16" /></button>
    </aside>
  </div>
</template>
