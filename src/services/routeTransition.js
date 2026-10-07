// Lets the router restore scroll only after the outgoing page has faded out,
// so the old page never visibly jumps to the top before it disappears.
const waiting = []

export function afterRouteLeave() {
  return new Promise((resolve) => {
    waiting.push(resolve)
    setTimeout(resolve, 200)
  })
}

export function notifyRouteLeft() {
  waiting.splice(0).forEach((resolve) => resolve())
}
