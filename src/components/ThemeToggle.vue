<script setup>
import { Moon, Sun } from 'lucide-vue-next'
import { theme, toggleTheme } from '../services/theme'
</script>

<template>
  <button type="button" class="theme-toggle" :aria-label="theme === 'dark' ? '切换到亮色模式' : '切换到暗色模式'" :title="theme === 'dark' ? '切换到亮色模式' : '切换到暗色模式'" :aria-pressed="theme === 'dark'" @click="toggleTheme($event)">
    <span class="theme-toggle-icon" aria-hidden="true"><Sun v-if="theme === 'dark'" :size="18" /><Moon v-else :size="18" /></span>
    <span>{{ theme === 'dark' ? '亮色' : '暗色' }}</span>
  </button>
</template>
