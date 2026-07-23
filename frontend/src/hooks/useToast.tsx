import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

export type ToastKind = 'success' | 'warning' | 'error'

export interface ToastItem {
  id: number
  kind: ToastKind
  message: string
  duration: number
}

interface ToastContextValue {
  toasts: ToastItem[]
  showToast: (message: string, kind?: ToastKind, duration?: number) => void
  dismissToast: (id: number) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const DEFAULT_DURATION: Record<ToastKind, number> = {
  success: 3500,
  error: 3500,
  warning: 4000,
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const idRef = useRef(0)

  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const showToast = useCallback(
    (message: string, kind: ToastKind = 'success', duration?: number) => {
      const id = idRef.current++
      const effectiveDuration = duration ?? DEFAULT_DURATION[kind]
      setToasts((prev) => [...prev, { id, kind, message, duration: effectiveDuration }])
      if (effectiveDuration > 0) {
        setTimeout(() => dismissToast(id), effectiveDuration)
      }
    },
    [dismissToast],
  )

  return (
    <ToastContext.Provider value={{ toasts, showToast, dismissToast }}>
      {children}
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast는 ToastProvider 내부에서 사용해야 합니다')
  return ctx
}
