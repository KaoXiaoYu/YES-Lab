import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import brand from './config/branding.json'
import appearance from './config/appearance.json'

const escapeHtml = (value) =>
  String(value).replace(
    /[&<>"']/g,
    (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char],
  )

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const presetName = env.VITE_UI_PRESET || appearance.defaultPreset
  const preset = appearance.presets[presetName]
  if (!preset) throw new Error(`Unknown UI preset: ${presetName}`)
  const presetCss = Object.entries(preset.light)
    .map(([key, value]) => `--color-${key}:${value};`)
    .join('')
  const darkCss = Object.entries(preset.dark)
    .map(([key, value]) => `--color-${key}:${value};`)
    .join('')
  return {
    clearScreen: false,
    plugins: [
      vue(),
      tailwindcss(),
      {
        name: 'yeslab-brand-and-appearance',
        transformIndexHtml(html) {
          const fields = {
            name: brand.name,
            description: brand.description,
            favicon: brand.favicon,
            shareImage: brand.shareImage,
          }
          const branded = html.replace(/%LAB_(\w+)%/g, (_match, key) => escapeHtml(fields[key]))
          return {
            html: branded,
            tags: [
              {
                tag: 'style',
                injectTo: 'head',
                children: `:root:root {${presetCss}--font-body:${preset.fontBody};--font-heading:${preset.fontHeading};--preset-radius:${preset.radius};}:root:root[data-theme='dark'] {${darkCss}}`,
              },
              {
                tag: 'script',
                injectTo: 'head',
                children: `document.documentElement.dataset.uiPreset=${JSON.stringify(presetName).replace(/</g, '\\u003c')}`,
              },
            ],
          }
        },
      },
    ],
    server: {
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      watch: { ignored: ['**/.codex-run/**'] },
      proxy: {
        '/api': 'http://127.0.0.1:8080',
        '/actuator': 'http://127.0.0.1:8080',
      },
    },
  }
})
