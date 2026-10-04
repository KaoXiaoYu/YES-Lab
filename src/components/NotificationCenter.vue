<script setup>
import { Bell, CheckCheck, Inbox, X } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getNotifications,
  getNotificationVisibility,
  markAllNotificationsRead,
  markNotificationRead,
} from '../services/authApi'
import { useDismissibleLayer } from '../composables/useDismissibleLayer'
import NotificationMascot from './NotificationMascot.vue'
import { getNotificationMascot } from '../services/notificationMascots'

const router = useRouter()
const route = useRoute()
const inbox = ref({ unreadCount: 0, messages: [] })
const open = ref(false)
const toast = ref(null)
const initialized = ref(false)
const visible = ref(false)
const mascot = ref('MELINA')
const mascotCopy = computed(() => getNotificationMascot(mascot.value))
const notificationRoot = ref(null)
const anchorStyle = ref({})
let pollTimer
let toastTimer
let refreshing = false
let disposed = false

const unread = computed(() => inbox.value.messages.filter((message) => !message.read))

watch(
  () => route.fullPath,
  () => {
    open.value = false
  },
)

onMounted(async () => {
  await refresh(true)
  if (disposed) return
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') refresh(false)
  }, 15000)
  document.addEventListener('visibilitychange', handleVisibility)
  window.addEventListener('resize', updatePosition)
  window.addEventListener('openlims:notifications-updated', handleNotificationUpdate)
  window.addEventListener('openlims:notification-settings-updated', handleNotificationUpdate)
})

onBeforeUnmount(() => {
  disposed = true
  window.clearInterval(pollTimer)
  window.clearTimeout(toastTimer)
  document.removeEventListener('visibilitychange', handleVisibility)
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('openlims:notifications-updated', handleNotificationUpdate)
  window.removeEventListener('openlims:notification-settings-updated', handleNotificationUpdate)
})

async function refresh(firstLoad) {
  if (refreshing) return
  refreshing = true
  try {
    try {
      const settings = await getNotificationVisibility()
      if (disposed) return
      visible.value = settings?.visible !== false
      const previousMascot = mascot.value
      mascot.value = settings?.mascot === 'NAILONG' ? 'NAILONG' : 'MELINA'
      if (mascot.value !== previousMascot) {
        window.dispatchEvent(new CustomEvent('openlims:notification-appearance-changed'))
      }
    } catch {
      // Retain the previous setting on transient failures; support older backends on first load.
      if (!initialized.value) visible.value = true
    }
    if (!visible.value) {
      open.value = false
      toast.value = null
      window.clearTimeout(toastTimer)
      initialized.value = true
      return
    }
    await nextTick()
    updatePosition()
    const previousUnread = new Map(
      unread.value.map((message) => [message.id, `${message.aggregationCount}:${message.updatedAt}`]),
    )
    const next = await getNotifications()
    if (disposed) return
    inbox.value = next || { unreadCount: 0, messages: [] }
    const fresh = inbox.value.messages.filter(
      (message) =>
        !message.read && previousUnread.get(message.id) !== `${message.aggregationCount}:${message.updatedAt}`,
    )
    if ((firstLoad && unread.value.length) || (!firstLoad && fresh.length)) {
      showToast(firstLoad ? unread.value : fresh)
    }
    initialized.value = true
  } catch {
    initialized.value = true
  } finally {
    refreshing = false
  }
}

function showToast(messages) {
  updatePosition()
  window.clearTimeout(toastTimer)
  toast.value =
    messages.length === 1
      ? { title: messages[0].title, summary: messages[0].summary }
      : { title: `你有 ${messages.length} 条新消息`, batch: true }
  toastTimer = window.setTimeout(() => {
    toast.value = null
  }, 5000)
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
  window.dispatchEvent(new CustomEvent('openlims:notifications-updated'))
  open.value = false
  if (message.targetPath) router.push(message.targetPath)
}

async function readAll() {
  inbox.value = await markAllNotificationsRead()
  window.dispatchEvent(new CustomEvent('openlims:notifications-updated'))
}

function handleVisibility() {
  if (document.visibilityState === 'visible') refresh(false)
}

function handleNotificationUpdate() {
  refresh(false)
}

useDismissibleLayer(notificationRoot, {
  isOpen: () => open.value,
  close: () => {
    open.value = false
  },
  focusTarget: () => notificationRoot.value?.querySelector('.notification-trigger'),
})
</script>

<template>
  <div v-if="visible" ref="notificationRoot" class="notification-center" :style="anchorStyle">
    <button
      type="button"
      class="notification-trigger"
      aria-label="打开站内消息"
      :aria-expanded="open"
      aria-controls="notification-panel"
      @click="togglePanel"
    >
      <Bell :size="18" aria-hidden="true" />
      <span v-if="inbox.unreadCount" class="notification-badge">{{
        inbox.unreadCount > 99 ? '99+' : inbox.unreadCount
      }}</span>
    </button>

    <section v-if="open" id="notification-panel" class="notification-panel" aria-label="站内消息">
      <header>
        <div>
          <span
            ><strong>{{ mascotCopy.name }}</strong
            ><small>站内消息</small></span
          >
        </div>
        <button v-if="inbox.unreadCount" type="button" @click="readAll"><CheckCheck :size="16" />全部已读</button>
      </header>
      <div class="notification-list">
        <div class="notification-companion">
          <NotificationMascot :mascot="mascot" />
          <div>
            <strong>{{ unread.length ? mascotCopy.unread : mascotCopy.allRead }}</strong>
            <p>{{ mascotCopy.archiveHint }}</p>
          </div>
        </div>
        <button v-for="message in unread" :key="message.id" type="button" class="unread" @click="openMessage(message)">
          <span class="notification-dot" aria-hidden="true" /><span
            ><strong>{{ message.title }}</strong>
            <p>{{ message.summary }}</p>
            <small>{{ new Date(message.createdAt).toLocaleString('zh-CN') }}</small></span
          >
        </button>
        <p v-if="initialized && !unread.length" class="notification-empty">暂时没有未读消息。</p>
      </div>
      <RouterLink class="notification-inbox-link" to="/inbox" @click="open = false"
        ><Inbox :size="16" aria-hidden="true" />打开完整站内信箱</RouterLink
      >
    </section>

    <aside v-if="toast" class="notification-toast" role="status" aria-live="polite">
      <NotificationMascot :mascot="mascot" class="notification-toast-mascot" />
      <div>
        <small class="notification-sender">{{ mascotCopy.toastLabel }}</small
        ><strong>{{ toast.title }}</strong>
        <p>{{ toast.batch ? mascotCopy.batchSummary : toast.summary }}</p>
      </div>
      <button type="button" class="notification-toast-close" aria-label="关闭消息提醒" @click="toast = null">
        <X :size="16" />
      </button>
    </aside>
  </div>
</template>
