import './spinner.css'

export function Spinner({ size = 48 }: { size?: number }) {
  return <div className="spinner" style={{ width: size, height: size }} role="status" aria-live="polite" />
}
