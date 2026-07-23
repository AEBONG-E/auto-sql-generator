import type { ErdStats } from '../../types/api'
import './context-bar.css'

interface ContextBarProps {
  stats: ErdStats
  fileCount: number
}

export function ContextBar({ stats, fileCount }: ContextBarProps) {
  return (
    <div className="context-bar">
      <span>
        📊 테이블 <span className="badge">{stats.tableCount}</span>
      </span>
      <span>
        컬럼 <span className="badge">{stats.columnCount}</span>
      </span>
      <span>
        관계 <span className="badge">{stats.relationCount}</span>
      </span>
      <span>
        파일 <span className="badge">{fileCount}</span>
      </span>
    </div>
  )
}
