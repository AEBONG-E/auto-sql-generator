import { useState } from 'react'
import type { ProjectFile } from '../../types/api'
import './file-list.css'

interface FileListProps {
  files: ProjectFile[]
  onAddClick: () => void
  onDelete: (fileId: number) => void
}

export function FileList({ files, onAddClick, onDelete }: FileListProps) {
  const [confirmingId, setConfirmingId] = useState<number | null>(null)

  return (
    <div className="file-list-section">
      <div className="file-list-header">
        <span className="section-header">업로드된 파일</span>
        <button className="btn btn-outline btn-xs" onClick={onAddClick} title="파일 추가 업로드">
          + 파일 추가
        </button>
      </div>

      {files.length === 0 && <div className="file-list-empty">파일을 추가하면 메타데이터가 등록됩니다</div>}

      <div className="file-list scroll-thin">
        {files.map((file) => (
          <div key={file.id} className="file-item">
            {confirmingId === file.id ? (
              <>
                <span className="file-confirm-text">삭제할까요?</span>
                <button
                  className="btn btn-danger btn-xs"
                  onClick={() => {
                    onDelete(file.id)
                    setConfirmingId(null)
                  }}
                >
                  확인
                </button>
                <button className="btn btn-outline btn-xs" onClick={() => setConfirmingId(null)}>
                  취소
                </button>
              </>
            ) : (
              <>
                <span className="file-icon">📄</span>
                <span className="file-name" title={file.originalFilename}>
                  {file.originalFilename}
                </span>
                <span className="badge">{file.tableCount}테이블</span>
                <button
                  className="file-delete-btn"
                  onClick={() => setConfirmingId(file.id)}
                  title="삭제"
                  aria-label={`${file.originalFilename} 삭제`}
                >
                  ×
                </button>
              </>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
