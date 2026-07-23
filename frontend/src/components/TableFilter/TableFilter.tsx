import { useMemo, useState } from 'react'
import type { TableInfo } from '../../types/api'
import './table-filter.css'

interface TableFilterProps {
  tables: TableInfo[]
  selectedTables: Set<string>
  activeTable: string | null
  onToggle: (tableName: string) => void
  onSelectAll: (allNames: string[]) => void
  onDeselectAll: () => void
  onTableClick: (tableName: string) => void
}

export function TableFilter({
  tables,
  selectedTables,
  activeTable,
  onToggle,
  onSelectAll,
  onDeselectAll,
  onTableClick,
}: TableFilterProps) {
  const [search, setSearch] = useState('')

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return tables
    return tables.filter((t) => t.tableName.toLowerCase().includes(q))
  }, [tables, search])

  if (tables.length === 0) {
    return (
      <div className="table-filter-empty">
        파일을 업로드하면
        <br />
        테이블 목록이 표시됩니다
      </div>
    )
  }

  return (
    <div className="table-filter">
      <div className="table-filter-search">
        <span className="search-icon">🔍</span>
        <input
          className="table-search-input"
          placeholder="테이블 검색..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === 'Escape' && setSearch('')}
        />
      </div>

      <div className="table-filter-actions">
        <button className="btn btn-outline btn-xs" onClick={() => onSelectAll(tables.map((t) => t.tableName))}>
          전체 선택
        </button>
        <button className="btn btn-outline btn-xs" onClick={onDeselectAll}>
          전체 해제
        </button>
      </div>

      <div className="table-filter-list scroll-thin">
        {filtered.map((table) => (
          <div
            key={table.tableName}
            className={`table-filter-item ${activeTable === table.tableName ? 'active' : ''}`}
          >
            <input
              type="checkbox"
              className="table-checkbox"
              checked={selectedTables.has(table.tableName)}
              onChange={() => onToggle(table.tableName)}
            />
            <span
              className="table-filter-name"
              title={table.tableDescription || ''}
              onClick={() => onTableClick(table.tableName)}
            >
              {table.tableName}
            </span>
            <span className="badge">{table.columnCount}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
