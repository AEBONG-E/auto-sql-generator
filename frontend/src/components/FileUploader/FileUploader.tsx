import { useCallback, useRef, useState } from 'react'
import type { UploadStage } from '../../hooks/useProjectWorkspace'
import './file-uploader.css'

interface FileUploaderProps {
  uploadStage: UploadStage
  onFilesSelected: (files: File[]) => void
}

const STAGE_LABEL: Record<UploadStage, string> = {
  idle: '',
  reading: '파일 읽는 중...',
  parsing: '스키마 파싱 중...',
  done: '저장 완료',
}

export function FileUploader({ uploadStage, onFilesSelected }: FileUploaderProps) {
  const [dragOver, setDragOver] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const triggerFileInput = useCallback(() => inputRef.current?.click(), [])

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      setDragOver(false)
      if (e.dataTransfer.files.length > 0) onFilesSelected(Array.from(e.dataTransfer.files))
    },
    [onFilesSelected],
  )

  const onInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (e.target.files && e.target.files.length > 0) onFilesSelected(Array.from(e.target.files))
      e.target.value = ''
    },
    [onFilesSelected],
  )

  const isUploading = uploadStage !== 'idle'

  return (
    <div className="file-uploader">
      <input
        ref={inputRef}
        type="file"
        accept=".xlsx"
        multiple
        className="file-uploader-input"
        onChange={onInputChange}
      />

      {!isUploading && (
        <div
          className={`drop-area ${dragOver ? 'drag-over' : ''}`}
          role="button"
          tabIndex={0}
          onClick={triggerFileInput}
          onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && triggerFileInput()}
          onDragOver={(e) => {
            e.preventDefault()
            setDragOver(true)
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={onDrop}
        >
          <div className="drop-icon">📊</div>
          <div className="drop-title">테이블 정의서 업로드</div>
          <div className="drop-sub">
            .xlsx 파일을 드래그앤드롭하거나
            <br />
            클릭하여 파일을 선택하세요
          </div>
          <div className="drop-multi-hint">📂 여러 파일 동시 선택 가능</div>
          <div className="drop-hint">
            MySQL information_schema 쿼리 결과 형식
            <br />
            필수 컬럼: 테이블명 · 컬럼명 · DATA TYPE · KEY · NULL값여부
          </div>
        </div>
      )}

      {isUploading && (
        <div className="upload-progress" role="status" aria-live="polite">
          <div className="upload-progress-steps">
            <span className="upload-dot active" />
            <span className="upload-progress-line" />
            <span className={`upload-dot ${uploadStage === 'parsing' || uploadStage === 'done' ? 'active' : ''}`} />
            <span className="upload-progress-line" />
            <span className={`upload-dot ${uploadStage === 'done' ? 'active done' : ''}`} />
          </div>
          <div className="upload-progress-text">{STAGE_LABEL[uploadStage]}</div>
        </div>
      )}
    </div>
  )
}
