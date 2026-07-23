import { useEffect, useRef, useState } from 'react'
import type { Project } from '../../types/api'
import './header.css'

interface HeaderProps {
  currentProject?: Project
  recentProjects: Project[]
  onSelectProject: (project: Project) => void
  onGoToProjectList: () => void
}

export function Header({ currentProject, recentProjects, onSelectProject, onGoToProjectList }: HeaderProps) {
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!dropdownOpen) return
    const onClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [dropdownOpen])

  return (
    <header className="app-header">
      <div className="app-logo" onClick={onGoToProjectList} role="button" tabIndex={0}>
        <div className="app-logo-icon">🔷</div>
        AutoSQL
      </div>

      {currentProject && (
        <div className="project-switcher" ref={containerRef}>
          <button className="project-switcher-trigger" onClick={() => setDropdownOpen((v) => !v)}>
            {currentProject.projectName} <span className="caret">▾</span>
          </button>
          {dropdownOpen && (
            <div className="project-switcher-dropdown">
              {recentProjects.slice(0, 5).map((p) => (
                <div
                  key={p.id}
                  className={`project-switcher-item ${p.id === currentProject.id ? 'active' : ''}`}
                  onClick={() => {
                    onSelectProject(p)
                    setDropdownOpen(false)
                  }}
                >
                  {p.projectName}
                </div>
              ))}
              <div
                className="project-switcher-item project-switcher-all"
                onClick={() => {
                  onGoToProjectList()
                  setDropdownOpen(false)
                }}
              >
                모든 프로젝트 보기...
              </div>
            </div>
          )}
        </div>
      )}

      <div className="header-actions">
        <button className="btn btn-ghost" disabled title="향후 지원 예정">
          ⚙ 설정
        </button>
      </div>
    </header>
  )
}
