import { apiFetch } from './http'
import { API_BASE } from './config'
import type { ErdResponse } from '../types/api'

export function getErd(projectId: number, tableNames?: string[]): Promise<ErdResponse> {
  const query = tableNames && tableNames.length > 0
    ? `?tableNames=${encodeURIComponent(tableNames.join(','))}`
    : ''
  return apiFetch<ErdResponse>(`${API_BASE}/api/v1/projects/${projectId}/erd${query}`)
}
