import { useToast } from '../../hooks/useToast'
import './toast.css'

const ICONS = { success: '✓', warning: '⚠', error: '✕' } as const

export function ToastContainer() {
  const { toasts, dismissToast } = useToast()

  if (toasts.length === 0) return null

  return (
    <div className="toast-stack">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`toast toast-${toast.kind}`}
          role="alert"
          aria-live="polite"
          onClick={() => dismissToast(toast.id)}
        >
          <span className="toast-icon">{ICONS[toast.kind]}</span>
          <span className="toast-message">{toast.message}</span>
        </div>
      ))}
    </div>
  )
}
