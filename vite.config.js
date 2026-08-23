import { sites } from '@openai/sites-vite-plugin'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue(), sites()],
  server: { host: '0.0.0.0' },
})
