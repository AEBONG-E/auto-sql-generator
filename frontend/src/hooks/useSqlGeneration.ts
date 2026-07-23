import { useCallback, useRef, useState } from 'react'
import { generateSql } from '../api/sql'
import { useToast } from './useToast'

export type SqlGenerationStage = 'idle' | 'analyzing' | 'streaming'

export function useSqlGeneration(projectId: number) {
  const { showToast } = useToast()
  const [query, setQuery] = useState('')
  const [output, setOutput] = useState('')
  const [stage, setStage] = useState<SqlGenerationStage>('idle')
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const generate = useCallback(async () => {
    const trimmed = query.trim()
    if (!trimmed) {
      showToast('질문을 입력해 주세요', 'error')
      return
    }

    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller

    setError(null)
    setOutput('')
    setStage('analyzing')

    let firstChunk = true
    try {
      await generateSql(
        projectId,
        trimmed,
        (chunk) => {
          if (firstChunk) {
            setStage('streaming')
            firstChunk = false
          }
          setOutput((prev) => prev + chunk)
        },
        controller.signal,
      )
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        // 사용자가 중단함 — 에러로 취급하지 않음
      } else {
        setError(err instanceof Error ? err.message : '알 수 없는 오류')
      }
    } finally {
      setStage('idle')
      abortRef.current = null
    }
  }, [projectId, query, showToast])

  const abort = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
  }, [])

  const copy = useCallback(async () => {
    if (!output.trim()) return
    await navigator.clipboard.writeText(output)
    showToast('클립보드에 복사되었습니다', 'success')
  }, [output, showToast])

  return { query, setQuery, output, stage, error, generate, abort, copy }
}
