import { watch } from 'vue'
import { toast } from '../services/toast'

/**
 * Routes a page's existing success / error message refs to global toasts so
 * results stay visible wherever the admin has scrolled. Errors stay inline
 * while `keepErrorInline()` is true (e.g. the page itself failed to load).
 */
export function useToastFeedback({ success, error, keepErrorInline = () => false }) {
  if (success)
    watch(success, (message) => {
      if (!message) return
      toast.success(message)
      success.value = ''
    })
  if (error)
    watch(error, (message) => {
      if (!message || keepErrorInline()) return
      toast.error(message)
      error.value = ''
    })
}
