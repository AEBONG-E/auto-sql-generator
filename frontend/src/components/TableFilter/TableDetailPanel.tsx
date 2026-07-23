import { useState } from 'react'
import type { TableInfo } from '../../types/api'
import './table-detail-panel.css'

interface TableDetailPanelProps {
  table: TableInfo | null
}

export function TableDetailPanel({ table }: TableDetailPanelProps) {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <div className={`detail-panel ${collapsed ? 'collapsed' : ''}`}>
      <div className="detail-panel-header" onClick={() => setCollapsed((v) => !v)}>
        <span>
          {table
            ? `${table.tableName}${table.tableDescription ? ' — ' + table.tableDescription : ''} (${table.columnCount}컬럼)`
            : '테이블을 클릭하면 컬럼 상세 정보가 표시됩니다'}
        </span>
        <span>{collapsed ? '▼' : '▲'}</span>
      </div>
      {!collapsed && table && (
        <div className="detail-panel-content scroll-thin">
          <table className="detail-table">
            <thead>
              <tr>
                <th>#</th>
                <th>컬럼명</th>
                <th>설명</th>
                <th>타입</th>
                <th>Key</th>
                <th>Null</th>
                <th>기본값</th>
              </tr>
            </thead>
            <tbody>
              {table.columns.map((col) => (
                <tr key={col.ordinal}>
                  <td className="muted">{col.ordinal}</td>
                  <td className="mono strong">
                    {col.columnName}
                    {col.autoIncrement && <span className="ai-hint"> AI</span>}
                  </td>
                  <td className="muted">{col.columnComment || ''}</td>
                  <td className="mono type-col">{col.columnType || col.dataType}</td>
                  <td>
                    {col.keyType === 'PRI' && <span className="badge badge-pk">PK</span>}
                    {col.keyType === 'MUL' && <span className="badge badge-mul">MUL</span>}
                    {col.keyType === 'UNI' && <span className="badge badge-uni">UNI</span>}
                  </td>
                  <td>{!col.nullable && <span className="badge badge-null">NOT NULL</span>}</td>
                  <td className="muted mono">{col.defaultValue || ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
