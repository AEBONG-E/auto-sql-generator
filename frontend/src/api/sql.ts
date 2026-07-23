/**
 * SSE 스트리밍 SQL 생성. EventSource는 POST body를 지원하지 않으므로
 * fetch + ReadableStream reader로 SSE 프레임(`data:` 라인, 빈 줄 구분)을 직접 파싱한다.
 */
export async function generateSql(
  projectId: number,
  query: string,
  onChunk: (chunk: string) => void,
  signal: AbortSignal,
): Promise<void> {
  const res = await fetch(`/api/v1/projects/${projectId}/sql/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ query }),
    signal,
  })

  if (!res.ok || !res.body) {
    throw new Error(`서버 오류: ${res.status}`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let sseBuffer = ''

  const consumeBuffer = (flush: boolean) => {
    while (true) {
      const eventEnd = sseBuffer.search(/\r?\n\r?\n/)
      if (eventEnd === -1) {
        if (!flush) return
        if (!sseBuffer) return
        emitEvent(sseBuffer)
        sseBuffer = ''
        return
      }
      const eventBlock = sseBuffer.slice(0, eventEnd)
      const separatorLength = sseBuffer.startsWith('\r\n\r\n', eventEnd) ? 4 : 2
      sseBuffer = sseBuffer.slice(eventEnd + separatorLength)
      emitEvent(eventBlock)
    }
  }

  const emitEvent = (eventBlock: string) => {
    const dataLines = eventBlock
      .split(/\r?\n/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5))
    if (dataLines.length === 0) return
    const chunk = dataLines.join('\n')
    if (chunk !== '') onChunk(chunk)
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    sseBuffer += decoder.decode(value, { stream: true })
    consumeBuffer(false)
  }

  sseBuffer += decoder.decode()
  consumeBuffer(true)
}
