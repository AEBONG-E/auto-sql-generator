import { useCallback, useEffect, useState } from 'react'
import * as projectsApi from '../api/projects'
import type { Project } from '../types/api'
import { ApiError } from '../api/http'
import { useToast } from './useToast'

export function useProjects() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const { showToast } = useToast()

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const list = await projectsApi.listProjects()
      setProjects(list)
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : '프로젝트 목록을 불러오지 못했습니다', 'error')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    refresh()
  }, [refresh])

  const createProject = useCallback(
    async (projectKey: string, projectName: string, description?: string) => {
      const project = await projectsApi.createProject({ projectKey, projectName, description })
      setProjects((prev) => [project, ...prev])
      return project
    },
    [],
  )

  return { projects, loading, refresh, createProject }
}
