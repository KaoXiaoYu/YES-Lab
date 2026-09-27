const mascots = {
  MELINA: {
    name: '梅琳娜',
    eyebrow: 'INBOX / MELINA',
    unread: '有新的信，我替你收好了。',
    allRead: '未读消息已经处理完啦。',
    archiveHint: '完整记录会一直留在站内信箱。',
    toastLabel: '梅琳娜来信',
    batchSummary: '打开消息中心查看梅琳娜发来的通知。',
    description: '查看梅琳娜送达的流程提醒、审核结果与团队通知。未读消息会保留醒目标记，已读历史也可以随时回看。',
    emptyUnread: '新的流程提醒会继续由梅琳娜送到这里。',
  },
  NAILONG: {
    name: '奶龙',
    eyebrow: 'INBOX / NAILONG',
    unread: '嘿嘿，有新消息，快来看看！',
    allRead: '消息都看完啦，哈哈！',
    archiveHint: '以前的消息也在站内信箱里。',
    toastLabel: '奶龙提醒',
    batchSummary: '奶龙发现了新消息，快打开消息中心看看！',
    description: '奶龙帮你收好了流程提醒、审核结果和团队通知。快看看有没有新消息，以前的记录也都在这里。',
    emptyUnread: '有新的消息，奶龙会来提醒你。',
  },
}

export function getNotificationMascot(mascot) {
  return mascots[mascot] || mascots.MELINA
}
