import { useEffect, useRef, useState } from 'react'
import type { Project, ProjectFile, TableInfo } from '../../types/api'
import { FileList } from '../FileList/FileList'
import { TableFilter } from '../TableFilter/TableFilter'
import { TableDetailPanel } from '../TableFilter/TableDetailPanel'
import './sidebar.css'

interface SidebarProps {
  project: Project
  files: ProjectFile[]
  tables: TableInfo[]
  selectedTables: Set<string>
  activeDetailTable: string | null
  onAddFileClick: () => void
  onDeleteFile: (fileId: number) => void
  onResetProject: () => void
  onToggleTable: (name: string) => void
  onSelectAllTables: (names: string[]) => void
  onDeselectAllTables: () => void
  onTableClick: (name: string) => void
}

export function Sidebar({
  project,
  files,
  tables,
  selectedTables,
  activeDetailTable,
  onAddFileClick,
  onDeleteFile,
  onResetProject,
  onToggleTable,
  onSelectAllTables,
  onDeselectAllTables,
  onTableClick,
}: SidebarProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirmingReset, setConfirmingReset] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!menuOpen) return
    const onClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [menuOpen])

  const activeTable = tables.find((t) => t.tableName === activeDetailTable) || null

  return (
    <aside className="sidebar" role="complementary">
      <div className="sidebar-project-context">
        <div className="sidebar-project-name">
          <span>📁 {project.projectName}</span>
          <div className="sidebar-project-menu" ref={menuRef}>
            <button className="btn btn-ghost btn-xs" onClick={() => setMenuOpen((v) => !v)}>
              ···
            </button>
            {menuOpen && (
              <div className="sidebar-project-dropdown">
                <div
                  className="sidebar-project-dropdown-item danger"
                  onClick={() => {
                    setMenuOpen(false)
                    setConfirmingReset(true)
                  }}
                >
                  프로젝트 초기화
                </div>
              </div>
            )}
          </div>
        </div>
        <div className="sidebar-project-meta">
          파일 {files.length}개 · 테이블 {tables.length}개
        </div>
        {confirmingReset && (
          <div className="sidebar-reset-confirm">
            <span>모든 파일과 메타데이터가 삭제됩니다.</span>
            <div className="sidebar-reset-confirm-actions">
              <button
                className="btn btn-danger btn-xs"
                onClick={() => {
                  onResetProject()
                  setConfirmingReset(false)
                }}
              >
                초기화
              </button>
              <button className="btn btn-outline btn-xs" onClick={() => setConfirmingReset(false)}>
                취소
              </button>
            </div>
          </div>
        )}
      </div>

      <FileList files={files} onAddClick={onAddFileClick} onDelete={onDeleteFile} />

      <TableFilter
        tables={tables}
        selectedTables={selectedTables}
        activeTable={activeDetailTable}
        onToggle={onToggleTable}
        onSelectAll={onSelectAllTables}
        onDeselectAll={onDeselectAllTables}
        onTableClick={onTableClick}
      />

      <TableDetailPanel table={activeTable} />
    </aside>
  )
}
