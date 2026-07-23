import { useState } from 'react'
import { Modal } from '../common/Modal'
import { slugifyProjectKey } from '../../lib/slug'
import type { Project } from '../../types/api'
import './project-creator.css'

interface ProjectCreatorProps {
  onClose: () => void
  onCreate: (projectKey: string, projectName: string) => Promise<Project>
  onCreated: (project: Project) => void
}

export function ProjectCreator({ onClose, onCreate, onCreated }: ProjectCreatorProps) {
  const [name, setName] = useState('')
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
    setSubmitting(true)
    setError(null)
    try {
      const project = await onCreate(projectKey, name.trim())
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
        {error && <div className="project-creator-error">{error}</div>}
      </div>
      <div className="project-creator-actions">
        <button className="btn btn-outline" onClick={onClose} disabled={submitting}>
          취소
        </button>
        <button className="btn btn-primary" onClick={submit} disabled={submitting}>
          {submitting ? '생성 중...' : '프로젝트 생성'}
        </button>
      </div>
    </Modal>
  )
}
