/**
 * 기존 static/js/drawio-embed.js의 PostMessage 핸드셰이크를 TypeScript로 이식.
 * embed.diagrams.net iframe과 postMessage(JSON) 프로토콜로 통신한다.
 */
import type { RelationInfo, TableInfo } from '../types/api'

const DEFAULT_ORIGIN = 'https://embed.diagrams.net'

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

function tableCellId(table: TableInfo, index: number): string {
  const raw = table.tableName || `table_${index}`
  return 'tbl_' + raw.replace(/[^\w\-:.]/g, '_')
}

function tableColumnsText(table: TableInfo): string {
  const columns = table.columns || []
  if (columns.length === 0) return ''
  return columns
    .map((col) => (col.columnType || col.dataType ? `${col.columnName} : ${col.columnType || col.dataType}` : col.columnName))
    .join('\n')
}

function buildTableValue(table: TableInfo): string {
  const cols = tableColumnsText(table)
  if (!cols) return escapeXml(table.tableName)
  return escapeXml(`${table.tableName}\n--------------------\n${cols}`).replace(/\n/g, '&#xa;')
}

export interface DrawioLayoutOptions {
  startX?: number
  startY?: number
  boxWidth?: number
  boxHeight?: number
  gapX?: number
  rowGap?: number
  perRow?: number
}

export function buildDrawioXml(
  tables: TableInfo[],
  relations: RelationInfo[],
  options: DrawioLayoutOptions = {},
): string {
  const startX = options.startX ?? 40
  const startY = options.startY ?? 40
  const boxWidth = options.boxWidth ?? 240
  const defaultBoxHeight = options.boxHeight ?? 0
  const gapX = options.gapX ?? 300
  const rowGap = options.rowGap ?? 40
  const perRow = options.perRow ?? 3

  const tableHeights = tables.map((t) => {
    const colCount = (t.columns || []).length
    return defaultBoxHeight > 0 ? defaultBoxHeight : Math.max(42, 28 + (colCount + 1) * 14)
  })

  const rowYOffsets: number[] = []
  let currentY = startY
  for (let r = 0; r * perRow < tables.length; r += 1) {
    rowYOffsets.push(currentY)
    let maxHeightInRow = 0
    for (let c = 0; c < perRow && r * perRow + c < tables.length; c += 1) {
      maxHeightInRow = Math.max(maxHeightInRow, tableHeights[r * perRow + c])
    }
    currentY += maxHeightInRow + rowGap
  }

  const cellLines: string[] = []
  const tableCellMap: Record<string, string> = {}

  tables.forEach((table, i) => {
    const cellId = tableCellId(table, i)
    const row = Math.floor(i / perRow)
    const col = i % perRow
    const x = startX + col * gapX
    const y = rowYOffsets[row]
    const height = tableHeights[i]
    const value = buildTableValue(table)

    tableCellMap[table.tableName] = cellId

    cellLines.push(
      `<mxCell id="${escapeXml(cellId)}" value="${value}" style="shape=swimlane;childLayout=stackLayout;horizontal=1;startSize=28;rounded=0;fillColor=#23273a;swimlaneFillColor=#1a1d27;strokeColor=#3a3f55;fontColor=#e4e6f0;fontStyle=1;align=left;verticalAlign=top;" vertex="1" parent="1">` +
        `<mxGeometry x="${x}" y="${y}" width="${boxWidth}" height="${height}" as="geometry"/>` +
        `</mxCell>`,
    )
  })

  relations.forEach((relation, j) => {
    const sourceId = tableCellMap[relation.fromTable]
    const targetId = tableCellMap[relation.toTable]
    if (!sourceId || !targetId) return

    const label = relation.relationType || ''
    cellLines.push(
      `<mxCell id="rel_${j}" value="${escapeXml(label)}" style="endArrow=block;html=1;rounded=0;orthogonalLoop=1;jettySize=auto;strokeColor=#5b8dee;fontColor=#8b90a8;" edge="1" parent="1" source="${escapeXml(sourceId)}" target="${escapeXml(targetId)}">` +
        `<mxGeometry relative="1" as="geometry"/>` +
        `</mxCell>`,
    )
  })

  return (
    '<mxfile host="app.diagrams.net"><diagram name="ERD"><mxGraphModel dx="1200" dy="800" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1600" pageHeight="1200" math="0" shadow="0"><root>' +
    '<mxCell id="0"/><mxCell id="1" parent="0"/>' +
    cellLines.join('') +
    '</root></mxGraphModel></diagram></mxfile>'
  )
}

export type DrawioMessage = {
  event?: string
  xml?: string
  exit?: boolean
  [key: string]: unknown
}

export class DrawioEmbedClient {
  private iframe: HTMLIFrameElement
  private origin: string
  private onMessage: (msg: DrawioMessage) => void
  private boundHandler: (event: MessageEvent) => void
  private connected = false

  constructor(config: {
    iframe: HTMLIFrameElement
    origin?: string
    onMessage: (msg: DrawioMessage) => void
  }) {
    this.iframe = config.iframe
    this.origin = config.origin || DEFAULT_ORIGIN
    this.onMessage = config.onMessage
    this.boundHandler = this.handleMessage.bind(this)
  }

  connect(): boolean {
    if (!this.iframe.contentWindow || this.connected) return false
    window.addEventListener('message', this.boundHandler)
    this.connected = true
    return true
  }

  disconnect(): void {
    if (!this.connected) return
    window.removeEventListener('message', this.boundHandler)
    this.connected = false
  }

  private handleMessage(event: MessageEvent): void {
    if (!event || event.origin !== this.origin) return
    let data: unknown = event.data
    if (typeof data === 'string') {
      try {
        data = JSON.parse(data)
      } catch {
        return
      }
    }
    if (data && typeof data === 'object') {
      this.onMessage(data as DrawioMessage)
    }
  }

  send(message: Record<string, unknown>): boolean {
    if (!this.iframe.contentWindow) return false
    this.iframe.contentWindow.postMessage(JSON.stringify(message), this.origin)
    return true
  }

  loadXml(xml: string): boolean {
    return this.send({ action: 'load', xml })
  }
}
