<script setup>
import { Bell, Bot, CheckCheck, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../services/authApi'

const router = useRouter()
const inbox = ref({ unreadCount: 0, messages: [] })
const open = ref(false)
const toast = ref(null)
const initialized = ref(false)
let pollTimer
let toastTimer

const unread = computed(() => inbox.value.messages.filter(message => !message.read))

onMounted(async () => {
  await refresh(true)
  pollTimer = window.setInterval(() => refresh(false), 15000)
  document.addEventListener('visibilitychange', handleVisibility)
})

onBeforeUnmount(() => {
  window.clearInterval(pollTimer)
  window.clearTimeout(toastTimer)
  document.removeEventListener('visibilitychange', handleVisibility)
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
  window.clearTimeout(toastTimer)
  toast.value = messages.length === 1
    ? { title: messages[0].title, summary: messages[0].summary }
    : { title: `你有 ${messages.length} 条新消息`, summary: '打开消息中心查看梅琳娜发来的通知。' }
  toastTimer = window.setTimeout(() => { toast.value = null }, 5000)
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
  <div class="notification-center" @keydown.esc="open = false">
    <button type="button" class="notification-trigger" aria-label="打开站内消息"
      :aria-expanded="open" aria-controls="notification-panel" @click="open = !open">
      <Bell :size="18" aria-hidden="true" />
      <span v-if="inbox.unreadCount" class="notification-badge">{{ inbox.unreadCount > 99 ? '99+' : inbox.unreadCount }}</span>
    </button>

    <section v-if="open" id="notification-panel" class="notification-panel" aria-label="站内消息">
      <header><div><Bot :size="20" aria-hidden="true" /><span><strong>梅琳娜</strong><small>站内消息</small></span></div>
        <button v-if="inbox.unreadCount" type="button" @click="readAll"><CheckCheck :size="16" />全部已读</button>
      </header>
      <div class="notification-list">
        <button v-for="message in inbox.messages" :key="message.id" type="button" :class="{ unread: !message.read }" @click="openMessage(message)">
          <span class="notification-dot" aria-hidden="true" /><span><strong>{{ message.title }}</strong><p>{{ message.summary }}</p><small>{{ new Date(message.createdAt).toLocaleString('zh-CN') }}</small></span>
        </button>
        <p v-if="initialized && !inbox.messages.length" class="notification-empty">暂时没有站内消息。</p>
      </div>
    </section>

    <aside v-if="toast" class="notification-toast" role="status" aria-live="polite">
      <Bot :size="20" aria-hidden="true" /><div><strong>{{ toast.title }}</strong><p>{{ toast.summary }}</p></div>
      <button type="button" aria-label="关闭消息提醒" @click="toast = null"><X :size="16" /></button>
    </aside>
  </div>
</template>
