<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { getAuthenticatedFile } from '../services/authApi'

const props = defineProps({ src: { type: String, required: true }, alt: { type: String, required: true } })
const source = ref('')
const failed = ref(false)
let objectUrl = ''
let version = 0

watch(() => props.src, load, { immediate: true })
onBeforeUnmount(revoke)

async function load() {
  const current = ++version
  revoke(); failed.value = false
  try {
    const url = await getAuthenticatedFile(props.src)
    if (current !== version) return URL.revokeObjectURL(url)
    objectUrl = url; source.value = url
  } catch { if (current === version) failed.value = true }
}
function revoke() { if (objectUrl) URL.revokeObjectURL(objectUrl); objectUrl = ''; source.value = '' }
</script>

<template>
  <img v-if="source && !failed" :src="source" :alt="alt" @error="failed = true" />
  <span v-else class="authenticated-image-fallback">图片加载失败</span>
</template>
