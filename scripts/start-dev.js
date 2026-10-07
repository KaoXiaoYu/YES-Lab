import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { createServer } from 'node:net'
import { fileURLToPath } from 'node:url'

const projectRoot = fileURLToPath(new URL('../', import.meta.url))
const viteCli = new URL('../node_modules/vite/bin/vite.js', import.meta.url)
process.chdir(projectRoot)

let revision = '源码压缩包（没有 Git 版本信息）'
try {
  revision = execFileSync('git', ['rev-parse', '--short', 'HEAD'], {
    cwd: projectRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  }).trim()
} catch {
  // Downloaded source archives can run without Git.
}

console.log(`\nYES Lab 前端 · 当前提交 ${revision}`)
console.log(`源码目录：${projectRoot}`)
console.log('此命令只运行本机源码；更新仓库请先执行 git pull --ff-only 和 npm ci。')
console.log('请打开下方 Local 显示的地址；端口已被占用时会停止，不会自动换端口。\n')

// 与 vite.config.js 的 server.port 保持一致；命令行 --port 优先。
const DEFAULT_PORT = 5173

function readOption(name) {
  const args = process.argv.slice(2)
  const index = args.findIndex((arg) => arg === `--${name}` || arg.startsWith(`--${name}=`))
  if (index === -1) return undefined
  const arg = args[index]
  if (arg.includes('=')) return arg.slice(arg.indexOf('=') + 1)
  const next = args[index + 1]
  return next && !next.startsWith('-') ? next : 'true'
}

// 端口已被监听返回 false；地址不可用（如本机未启用 IPv6）视为空闲，交给 Vite 处理。
function isPortFree(port, host) {
  return new Promise((resolve) => {
    const server = createServer()
    server.once('error', (error) => resolve(error.code !== 'EADDRINUSE'))
    server.once('listening', () => server.close(() => resolve(true)))
    server.listen(port, host)
  })
}

const port = Number(readOption('port')) || DEFAULT_PORT
const host = readOption('host')
const hosts = host && host !== 'true' ? [host] : ['127.0.0.1', '::1']

if (!existsSync(viteCli)) {
  console.error('缺少前端依赖，请先在上述目录执行 npm ci。')
  process.exitCode = 1
} else if (!(await Promise.all(hosts.map((h) => isPortFree(port, h)))).every(Boolean)) {
  console.error(`端口 ${port} 已被占用，通常是之前启动的前端还在运行。请先关闭那个终端或进程，再重新执行 npm run dev。`)
  console.error(
    process.platform === 'win32'
      ? `查看占用进程：netstat -ano | findstr :${port}`
      : `查看占用进程：lsof -nP -iTCP:${port} -sTCP:LISTEN`,
  )
  process.exitCode = 1
} else {
  await import(viteCli.href)
}
