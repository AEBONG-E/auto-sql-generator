import { useMemo, useState } from 'react'
import type { Project } from '../../types/api'
import './project-selector.css'

interface ProjectSelectorProps {
  projects: Project[]
  loading: boolean
  onSelect: (project: Project) => void
  onCreateClick: () => void
}

export function ProjectSelector({ projects, loading, onSelect, onCreateClick }: ProjectSelectorProps) {
  const [search, setSearch] = useState('')

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return projects
    return projects.filter((p) => p.projectName.toLowerCase().includes(q))
  }, [projects, search])

  if (!loading && projects.length === 0) {
    return (
      <div className="project-onboarding">
        <div className="project-onboarding-icon">📁</div>
        <h1>첫 프로젝트를 만들어 시작하세요</h1>
        <p>엑셀 스키마를 업로드하면 ERD와 SQL을 자동으로 생성합니다</p>
        <button className="btn btn-primary" onClick={onCreateClick}>
          + 새 프로젝트
        </button>
      </div>
    )
  }

  return (
    <div className="project-list-screen">
      <div className="project-list-toolbar">
        <input
          className="input project-search"
          placeholder="프로젝트 이름 검색"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button className="btn btn-primary" onClick={onCreateClick}>
          + 새 프로젝트
        </button>
      </div>

      <div className="project-grid">
        {filtered.map((project) => (
          <div key={project.id} className="project-card" onClick={() => onSelect(project)}>
            <div className="project-card-name">{project.projectName}</div>
            <div className="project-card-meta">{project.description || '설명 없음'}</div>
          </div>
        ))}
        <div className="project-card project-card-new" onClick={onCreateClick}>
          + 새 프로젝트
        </div>
      </div>
    </div>
  )
}
