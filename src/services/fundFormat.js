export function formatMoney(value) {
  if (value === null || value === undefined) return '待登记'
  const [whole, fraction = ''] = String(value).split('.')
  return `¥${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction.padEnd(2, '0').slice(0, 2)}`
}
export function labDate() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}
export function labTime(value) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(value),
      )
    : '尚未登记'
}
