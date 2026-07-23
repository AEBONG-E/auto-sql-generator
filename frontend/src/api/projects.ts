import { apiFetch } from './http'
import type { Project } from '../types/api'

const BASE = '/api/v1/projects'

export function listProjects(): Promise<Project[]> {
  return apiFetch<Project[]>(BASE)
}

export function getProject(projectId: number): Promise<Project> {
  return apiFetch<Project>(`${BASE}/${projectId}`)
}

export function createProject(params: {
  projectKey: string
  projectName: string
  description?: string
}): Promise<Project> {
  return apiFetch<Project>(BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
}
