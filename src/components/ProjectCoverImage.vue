<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { getAuthenticatedFile } from '../services/authApi'

const props = defineProps({
  coverUrl: { type: String, default: '' },
  alt: { type: String, required: true },
  authenticated: { type: Boolean, default: true },
})

const defaultCover = '/yes-lab-logo.png'
const source = ref(defaultCover)
const usingDefault = ref(true)
let objectUrl = ''
let requestVersion = 0

watch(() => [props.coverUrl, props.authenticated], loadCover, { immediate: true })

onBeforeUnmount(revokeObjectUrl)

async function loadCover() {
  const version = ++requestVersion
  revokeObjectUrl()
  if (!props.coverUrl) {
    useDefault()
    return
  }
  if (!props.authenticated) {
    source.value = props.coverUrl
    usingDefault.value = false
    return
  }
  try {
    const nextUrl = await getAuthenticatedFile(props.coverUrl)
    if (version !== requestVersion) {
      URL.revokeObjectURL(nextUrl)
      return
    }
    objectUrl = nextUrl
    source.value = nextUrl
    usingDefault.value = false
  } catch {
    if (version === requestVersion) useDefault()
  }
}

function revokeObjectUrl() {
  if (!objectUrl) return
  URL.revokeObjectURL(objectUrl)
  objectUrl = ''
}

function useDefault() {
  revokeObjectUrl()
  source.value = defaultCover
  usingDefault.value = true
}
</script>

<template>
  <img class="project-cover-image" :class="{ 'is-default': usingDefault }" :src="source" :alt="alt" @error="useDefault" />
</template>
