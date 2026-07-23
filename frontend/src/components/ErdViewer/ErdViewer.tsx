import { useEffect, useRef, useState } from 'react'
import type { RelationInfo, TableInfo } from '../../types/api'
import { buildDrawioXml, DrawioEmbedClient, type DrawioMessage } from '../../lib/drawio'
import { useToast } from '../../hooks/useToast'
import { Spinner } from '../common/Spinner'
import './erd-viewer.css'

interface ErdViewerProps {
  tables: TableInfo[]
  relations: RelationInfo[]
  loading: boolean
  onUploadClick: () => void
}

function downloadDrawioXml(xml: string) {
  const blob = new Blob([xml], { type: 'application/xml' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `erd-${Date.now()}.drawio`
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function ErdViewer({ tables, relations, loading, onUploadClick }: ErdViewerProps) {
  const { showToast } = useToast()
  const hostRef = useRef<HTMLDivElement>(null)
  const clientRef = useRef<DrawioEmbedClient | null>(null)
  const readyRef = useRef(false)
  const pendingXmlRef = useRef<string | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (tables.length === 0 || !hostRef.current) return

    const host = hostRef.current
    host.innerHTML = ''

    const iframe = document.createElement('iframe')
    iframe.style.width = '100%'
    iframe.style.height = '100%'
    iframe.style.border = '0'
    iframe.setAttribute('title', 'ERD 다이어그램 편집기')
    iframe.src = 'https://embed.diagrams.net/?embed=1&ui=min&spin=1&proto=json&configure=1&dark=1'
    host.appendChild(iframe)

    const onMessage = (msg: DrawioMessage) => {
      if (msg.event === 'configure') {
        client.send({ action: 'configure', config: {} })
        return
      }
      if (msg.event === 'init' || msg.event === 'ready') {
        readyRef.current = true
        setReady(true)
        if (pendingXmlRef.current) client.loadXml(pendingXmlRef.current)
        return
      }
      if (msg.event === 'save') {
        const xml = typeof msg.xml === 'string' ? msg.xml : ''
        if (xml) {
          downloadDrawioXml(xml)
          showToast('ERD 파일이 저장되었습니다', 'success')
        }
        return
      }
    }

    const client = new DrawioEmbedClient({ iframe, onMessage })
    client.connect()
    clientRef.current = client
    readyRef.current = false
    setReady(false)

    return () => {
      client.disconnect()
      clientRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tables.length === 0])

  useEffect(() => {
    if (tables.length === 0) return
    const xml = buildDrawioXml(tables, relations)
    pendingXmlRef.current = xml
    if (readyRef.current && clientRef.current) {
      clientRef.current.loadXml(xml)
    }
  }, [tables, relations])

  if (tables.length === 0) {
    return (
      <div className="erd-empty">
        <div className="erd-empty-icon">🗂️</div>
        <div className="erd-empty-text">
          파일을 업로드하면
          <br />
          ERD가 자동 생성됩니다
        </div>
        <button className="btn btn-primary" onClick={onUploadClick}>
          + 파일 업로드
        </button>
      </div>
    )
  }

  return (
    <div className="erd-viewer">
      <div className="erd-toolbar">
        <button
          className="btn btn-outline btn-xs"
          disabled={!ready}
          onClick={() => clientRef.current?.send({ action: 'save' })}
        >
          저장
        </button>
        <button
          className="btn btn-outline btn-xs"
          disabled={!ready || !pendingXmlRef.current}
          onClick={() => pendingXmlRef.current && clientRef.current?.loadXml(pendingXmlRef.current)}
        >
          다시 맞춤
        </button>
      </div>
      <div className="erd-canvas-wrapper">
        {(loading || !ready) && (
          <div className="erd-loading-overlay">
            <Spinner />
          </div>
        )}
        <div className="erd-canvas" ref={hostRef} />
      </div>
    </div>
  )
}
