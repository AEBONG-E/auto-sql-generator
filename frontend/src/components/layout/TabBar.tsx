import { useCallback } from 'react'
import './tab-bar.css'

export type WorkspaceTab = 'erd' | 'sql'

interface TabBarProps {
  active: WorkspaceTab
  onChange: (tab: WorkspaceTab) => void
}

const TABS: { id: WorkspaceTab; label: string }[] = [
  { id: 'erd', label: '🗂 ERD 다이어그램' },
  { id: 'sql', label: '🤖 SQL 생성' },
]

export function TabBar({ active, onChange }: TabBarProps) {
  const onKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const idx = TABS.findIndex((t) => t.id === active)
      if (e.key === 'ArrowRight') onChange(TABS[(idx + 1) % TABS.length].id)
      if (e.key === 'ArrowLeft') onChange(TABS[(idx - 1 + TABS.length) % TABS.length].id)
    },
    [active, onChange],
  )

  return (
    <div className="tab-bar" role="tablist" onKeyDown={onKeyDown}>
      {TABS.map((tab) => (
        <div
          key={tab.id}
          role="tab"
          aria-selected={active === tab.id}
          tabIndex={active === tab.id ? 0 : -1}
          className={`tab-bar-item ${active === tab.id ? 'active' : ''}`}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </div>
      ))}
    </div>
  )
}
