import { useState } from 'react'
import { Modal } from '../common/Modal'
import { slugifyProjectKey } from '../../lib/slug'
import { SQL_DIALECT_OPTIONS } from '../../lib/sqlDialect'
import type { Project, SqlDialect } from '../../types/api'
import './project-creator.css'

interface ProjectCreatorProps {
  onClose: () => void
  onCreate: (projectKey: string, projectName: string, sqlDialect: SqlDialect) => Promise<Project>
  onCreated: (project: Project) => void
}

export function ProjectCreator({ onClose, onCreate, onCreated }: ProjectCreatorProps) {
  const [name, setName] = useState('')
  const [sqlDialect, setSqlDialect] = useState<SqlDialect | ''>('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const projectKey = slugifyProjectKey(name)

  const submit = async () => {
    if (!name.trim()) {
      setError('프로젝트 이름을 입력해 주세요')
      return
    }
    if (!projectKey) {
      setError('영문, 숫자, 하이픈을 포함한 이름을 입력해 주세요')
      return
    }
    if (!sqlDialect) {
      setError('SQL 방언을 선택해 주세요')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const project = await onCreate(projectKey, name.trim(), sqlDialect)
      onCreated(project)
    } catch (err) {
      setError(err instanceof Error ? err.message : '프로젝트 생성에 실패했습니다')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="새 프로젝트" onClose={onClose}>
      <div className="project-creator-field">
        <label htmlFor="project-name">프로젝트 이름</label>
        <input
          id="project-name"
          className="input"
          autoFocus
          value={name}
          placeholder="my-service-db"
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') submit()
          }}
        />
        <div className="project-creator-hint">
          영문, 숫자, 하이픈 사용 가능{projectKey ? ` · key: ${projectKey}` : ''}
        </div>
      </div>
      <div className="project-creator-field project-creator-field-gap">
        <label htmlFor="project-sql-dialect">SQL 방언</label>
        <select
          id="project-sql-dialect"
          className="input"
          value={sqlDialect}
          onChange={(e) => setSqlDialect(e.target.value as SqlDialect | '')}
        >
          <option value="" disabled>
            방언을 선택해 주세요
          </option>
          {SQL_DIALECT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <div className="project-creator-hint">생성할 SQL 의 대상 DB 입니다. 생성 후에도 바꿀 수 있습니다</div>
        {error && <div className="project-creator-error">{error}</div>}
      </div>
      <div className="project-creator-actions">
        <button className="btn btn-outline" onClick={onClose} disabled={submitting}>
          취소
        </button>
        <button className="btn btn-primary" onClick={submit} disabled={submitting || !sqlDialect}>
          {submitting ? '생성 중...' : '프로젝트 생성'}
        </button>
      </div>
    </Modal>
  )
}
