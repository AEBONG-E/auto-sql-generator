import { useCallback, useEffect, useRef, useState } from 'react'
import * as filesApi from '../api/files'
import * as erdApi from '../api/erd'
import type { ErdResponse, ProjectFile } from '../types/api'
import { ApiError } from '../api/http'
import { useToast } from './useToast'
import { useDebouncedCallback } from './useDebouncedCallback'

export type UploadStage = 'idle' | 'reading' | 'parsing' | 'done'

export function useProjectWorkspace(projectId: number) {
  const { showToast } = useToast()

  const [files, setFiles] = useState<ProjectFile[]>([])
  const [filesLoading, setFilesLoading] = useState(true)
  const [uploadStage, setUploadStage] = useState<UploadStage>('idle')

  const [erd, setErd] = useState<ErdResponse | null>(null)
  const [erdLoading, setErdLoading] = useState(false)
  const [selectedTables, setSelectedTables] = useState<Set<string>>(new Set())

  const uploadAbortRef = useRef<AbortController | null>(null)

  const loadFiles = useCallback(async () => {
    setFilesLoading(true)
    try {
      const list = await filesApi.listFiles(projectId)
      setFiles(list)
      return list
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : '파일 목록을 불러오지 못했습니다', 'error')
      return []
    } finally {
      setFilesLoading(false)
    }
  }, [projectId, showToast])

  const loadErd = useCallback(
    async (tableNames?: string[]) => {
      setErdLoading(true)
      try {
        const response = await erdApi.getErd(projectId, tableNames)
        setErd(response)
        if (!tableNames) {
          setSelectedTables(new Set(response.tables.map((t) => t.tableName)))
        }
        return response
      } catch (err) {
        showToast(err instanceof ApiError ? err.message : 'ERD 조회에 실패했습니다', 'error')
        return null
      } finally {
        setErdLoading(false)
      }
    },
    [projectId, showToast],
  )

  useEffect(() => {
    loadFiles().then((list) => {
      if (list.length > 0) loadErd()
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  const upload = useCallback(
    async (fileList: File[]) => {
      const xlsxFiles = fileList.filter((f) => f.name.endsWith('.xlsx'))
      const rejected = fileList.length - xlsxFiles.length
      if (xlsxFiles.length === 0) {
        showToast('.xlsx 파일만 업로드 가능합니다', 'error')
        return
      }
      if (rejected > 0) {
        showToast('.xlsx 파일만 업로드 가능합니다. 나머지는 무시됩니다', 'warning')
      }

      const controller = new AbortController()
      uploadAbortRef.current = controller
      const timeoutId = setTimeout(() => controller.abort(), 60000)

      setUploadStage('reading')
      try {
        setUploadStage('parsing')
        await filesApi.importSchemas(projectId, xlsxFiles, controller.signal)
        setUploadStage('done')
        await loadFiles()
        await loadErd()
        setTimeout(() => setUploadStage('idle'), 500)
      } catch (err) {
        setUploadStage('idle')
        if (err instanceof DOMException && err.name === 'AbortError') {
          showToast('요청 시간 초과 (60초). 서버 상태를 확인하세요', 'error')
        } else {
          showToast(err instanceof ApiError ? err.message : '업로드 실패', 'error')
        }
      } finally {
        clearTimeout(timeoutId)
        uploadAbortRef.current = null
      }
    },
    [projectId, loadFiles, loadErd, showToast],
  )

  const removeFile = useCallback(
    async (fileId: number) => {
      try {
        await filesApi.deleteFile(projectId, fileId)
        const remaining = await loadFiles()
        if (remaining.length === 0) {
          setErd(null)
          setSelectedTables(new Set())
        } else {
          await loadErd()
        }
        showToast('파일이 삭제되었습니다', 'success')
      } catch (err) {
        showToast(err instanceof ApiError ? err.message : '파일 삭제 실패', 'error')
      }
    },
    [projectId, loadFiles, loadErd, showToast],
  )

  const resetProject = useCallback(async () => {
    try {
      await filesApi.resetProject(projectId)
      setFiles([])
      setErd(null)
      setSelectedTables(new Set())
      showToast('프로젝트가 초기화되었습니다', 'success')
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : '프로젝트 초기화 실패', 'error')
    }
  }, [projectId, showToast])

  const applyFilterDebounced = useDebouncedCallback((names: string[]) => {
    loadErd(names.length > 0 ? names : undefined)
  }, 300)

  const toggleTable = useCallback(
    (tableName: string) => {
      setSelectedTables((prev) => {
        const next = new Set(prev)
        if (next.has(tableName)) next.delete(tableName)
        else next.add(tableName)
        applyFilterDebounced(Array.from(next))
        return next
      })
    },
    [applyFilterDebounced],
  )

  const selectAllTables = useCallback(
    (allNames: string[]) => {
      setSelectedTables(new Set(allNames))
      applyFilterDebounced(allNames)
    },
    [applyFilterDebounced],
  )

  const deselectAllTables = useCallback(() => {
    setSelectedTables(new Set())
    applyFilterDebounced([])
  }, [applyFilterDebounced])

  return {
    files,
    filesLoading,
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
  }
}
