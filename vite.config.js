import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    host: '0.0.0.0',
    watch: { ignored: ['**/.codex-run/**'] },
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/actuator': 'http://127.0.0.1:8080',
    },
  },
})
