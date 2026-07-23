import { useSqlGeneration } from '../../hooks/useSqlGeneration'
import './sql-generator.css'

interface SqlGeneratorProps {
  projectId: number
  hasSchema: boolean
}

export function SqlGenerator({ projectId, hasSchema }: SqlGeneratorProps) {
  const { query, setQuery, output, stage, error, generate, abort, copy } = useSqlGeneration(projectId)

  const isBusy = stage !== 'idle'

  return (
    <div className="sql-generator">
      <div className="sql-input-area">
        <textarea
          className="sql-input"
          placeholder="자연어로 질문하세요. 예) 최근 30일 동안 주문 금액이 가장 높은 고객 상위 10명을 조회해줘"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') generate()
          }}
        />
        <div className="sql-input-actions">
          <button className="btn btn-primary" disabled={isBusy || !hasSchema} onClick={generate}>
            {isBusy ? '생성 중...' : '생성'}
          </button>
          {isBusy && (
            <button className="btn btn-outline" onClick={abort}>
              중단
            </button>
          )}
        </div>
      </div>

      <div className="sql-output-wrapper">
        <div className="sql-output-header">
          <span>생성된 SQL</span>
          {!isBusy && output.trim() && (
            <button className="btn btn-outline btn-xs" onClick={copy}>
              📋 복사
            </button>
          )}
        </div>

        {stage === 'analyzing' && (
          <div className="sql-stage-indicator" role="status" aria-live="polite">
            <div className="sql-stage-label">단계 1/2 · 테이블 분석 중...</div>
            <div className="sql-stage-bar">
              <div className="sql-stage-bar-fill" style={{ width: '45%' }} />
            </div>
          </div>
        )}

        <div className="sql-output-box scroll-thin">
          {error && <span className="sql-error">오류: {error}</span>}
          {!error && !output && stage === 'idle' && (
            <span className="sql-placeholder">위에 질문을 입력하고 생성 버튼을 누르세요</span>
          )}
          {!error && output && (
            <>
              {output}
              {stage === 'streaming' && <span className="sql-cursor" />}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
