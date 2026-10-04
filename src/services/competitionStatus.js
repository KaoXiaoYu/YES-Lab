export function competitionState(item) {
  if (item.lifecycle === 'PLANNED') return '未开始'
  if (item.lifecycle === 'ONGOING') return '历史：进行中'
  if (item.resultStatus === 'PENDING_RESULT') return '完赛 · 待成绩'
  if (item.resultStatus === 'NO_AWARD') return '完赛 · 已结束 · 未获奖'
  if (item.resultStatus === 'AWARDED' || item.awardName) return '完赛 · 已结束 · 获奖'
  return '完赛 · 历史信息待补齐'
}
export function competitionOutcome(item) {
  if (item.resultStatus === 'PENDING_RESULT') return '等待公布成绩'
  if (item.resultStatus === 'NO_AWARD') return '未获奖'
  return item.awardName || (item.lifecycle === 'FINISHED' ? '历史信息待补齐' : '尚未完赛')
}
