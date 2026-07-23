import { apiFetch, apiFetchVoid } from './http'
import { API_BASE } from './config'
import type { ProjectFile } from '../types/api'

const base = (projectId: number) => `${API_BASE}/api/v1/projects/${projectId}`

export function listFiles(projectId: number): Promise<ProjectFile[]> {
  return apiFetch<ProjectFile[]>(`${base(projectId)}/files`)
}

export async function importSchemas(
  projectId: number,
  files: File[],
  signal?: AbortSignal,
): Promise<{ message: string; fileCount: number }> {
  const formData = new FormData()
  files.forEach((f) => formData.append('files', f))
  return apiFetch(`${base(projectId)}/schemas:import`, {
    method: 'POST',
    body: formData,
    signal,
  })
}

export function deleteFile(projectId: number, fileId: number): Promise<void> {
  return apiFetchVoid(`${base(projectId)}/files/${fileId}`, { method: 'DELETE' })
}

export function resetProject(projectId: number): Promise<void> {
  return apiFetchVoid(`${base(projectId)}/reset`, { method: 'POST' })
}
