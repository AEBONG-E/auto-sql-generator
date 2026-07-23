import { Component, type ErrorInfo, type ReactNode } from 'react'
import './error-boundary.css'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * 렌더 단계 예외에 대한 최상위 안전망. 클래스 컴포넌트만 에러 바운더리가 될 수 있다(React 제약).
 * DTO 형태 불일치 등 예기치 못한 렌더 오류가 흰 화면 대신 복구 UI로 이어지도록 한다.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="error-boundary">
          <div className="error-boundary-icon">⚠</div>
          <div className="error-boundary-title">예기치 못한 오류가 발생했습니다</div>
          <div className="error-boundary-message">{this.state.error.message}</div>
          <button className="btn btn-primary" onClick={() => window.location.reload()}>
            새로고침
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
