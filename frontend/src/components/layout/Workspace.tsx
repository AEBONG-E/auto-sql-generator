import { useState } from 'react'
import type { Project } from '../../types/api'
import { useProjectWorkspace } from '../../hooks/useProjectWorkspace'
import { Sidebar } from './Sidebar'
import { ContextBar } from './ContextBar'
import { TabBar, type WorkspaceTab } from './TabBar'
import { FileUploader } from '../FileUploader/FileUploader'
import { ErdViewer } from '../ErdViewer/ErdViewer'
import { SqlGenerator } from '../SqlGenerator/SqlGenerator'
import './workspace.css'

interface WorkspaceProps {
  project: Project
}

export function Workspace({ project }: WorkspaceProps) {
  const {
    files,
    uploadStage,
    upload,
    removeFile,
    resetProject,
    erd,
    erdLoading,
    selectedTables,
    toggleTable,
    selectAllTables,
    deselectAllTables,
  } = useProjectWorkspace(project.id)

  const [tab, setTab] = useState<WorkspaceTab>('erd')
  const [activeDetailTable, setActiveDetailTable] = useState<string | null>(null)
  const [uploaderOpen, setUploaderOpen] = useState(false)

  const hasFiles = files.length > 0
  const tables = erd?.tables ?? []
  const relations = erd?.relations ?? []

  const handleFilesSelected = (selected: File[]) => {
    setUploaderOpen(false)
    upload(selected)
  }

  return (
    <div className="workspace">
      <Sidebar
        project={project}
        files={files}
        tables={tables}
        selectedTables={selectedTables}
        activeDetailTable={activeDetailTable}
        onAddFileClick={() => setUploaderOpen(true)}
        onDeleteFile={removeFile}
        onResetProject={resetProject}
        onToggleTable={toggleTable}
        onSelectAllTables={selectAllTables}
        onDeselectAllTables={deselectAllTables}
        onTableClick={(name) => setActiveDetailTable((prev) => (prev === name ? prev : name))}
      />

      <div className="workspace-content">
        {!hasFiles ? (
          <FileUploader uploadStage={uploadStage} onFilesSelected={handleFilesSelected} />
        ) : (
          <>
            {erd && <ContextBar stats={erd.stats} fileCount={files.length} />}
            <TabBar active={tab} onChange={setTab} />
            {tab === 'erd' ? (
              <ErdViewer
                tables={tables}
                relations={relations}
                loading={erdLoading}
                onUploadClick={() => setUploaderOpen(true)}
              />
            ) : (
              <SqlGenerator projectId={project.id} hasSchema={tables.length > 0} />
            )}
          </>
        )}

        {uploaderOpen && hasFiles && (
          <div className="workspace-upload-overlay">
            <div className="workspace-upload-panel">
              <div className="workspace-upload-panel-header">
                <span>파일 추가</span>
                <button className="btn btn-ghost btn-xs" onClick={() => setUploaderOpen(false)}>
                  ✕
                </button>
              </div>
              <FileUploader uploadStage={uploadStage} onFilesSelected={handleFilesSelected} />
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
