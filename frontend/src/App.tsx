import { useCallback, useEffect, useState } from 'react'
import type { Project } from './types/api'
import { ToastProvider, useToast } from './hooks/useToast'
import { useProjects } from './hooks/useProjects'
import { ToastContainer } from './components/common/ToastContainer'
import { ErrorBoundary } from './components/common/ErrorBoundary'
import { Header } from './components/layout/Header'
import { Workspace } from './components/layout/Workspace'
import { ProjectSelector } from './components/ProjectSelector/ProjectSelector'
import { ProjectCreator } from './components/ProjectCreator/ProjectCreator'
import './App.css'

function readProjectIdFromUrl(): number | null {
  const params = new URLSearchParams(window.location.search)
  const raw = params.get('projectId')
  const parsed = raw ? Number(raw) : NaN
  return Number.isFinite(parsed) ? parsed : null
}

function writeProjectIdToUrl(projectId: number | null) {
  const params = new URLSearchParams(window.location.search)
  if (projectId) params.set('projectId', String(projectId))
  else params.delete('projectId')
  const query = params.toString()
  window.history.pushState({}, '', query ? `/?${query}` : '/')
}

function AppShell() {
  const { projects, loading, createProject } = useProjects()
  const { showToast } = useToast()
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(readProjectIdFromUrl)
  const [creatorOpen, setCreatorOpen] = useState(false)

  useEffect(() => {
    const onPopState = () => setSelectedProjectId(readProjectIdFromUrl())
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const selectProject = useCallback((project: Project) => {
    setSelectedProjectId(project.id)
    writeProjectIdToUrl(project.id)
  }, [])

  const goToProjectList = useCallback(() => {
    setSelectedProjectId(null)
    writeProjectIdToUrl(null)
  }, [])

  const currentProject = projects.find((p) => p.id === selectedProjectId)

  // URL에 projectId가 있었지만 목록 로딩 후 존재하지 않는 경우 목록 화면으로 복귀
  useEffect(() => {
    if (!loading && selectedProjectId && !currentProject) {
      showToast('존재하지 않는 프로젝트입니다', 'error')
      goToProjectList()
    }
  }, [loading, selectedProjectId, currentProject, goToProjectList, showToast])

  return (
    <div className="app-root">
      <Header
        currentProject={currentProject}
        recentProjects={projects}
        onSelectProject={selectProject}
        onGoToProjectList={goToProjectList}
      />

      <ErrorBoundary key={currentProject?.id ?? 'list'}>
        {currentProject ? (
          <Workspace key={currentProject.id} project={currentProject} />
        ) : (
          <ProjectSelector
            projects={projects}
            loading={loading}
            onSelect={selectProject}
            onCreateClick={() => setCreatorOpen(true)}
          />
        )}
      </ErrorBoundary>

      {creatorOpen && (
        <ProjectCreator
          onClose={() => setCreatorOpen(false)}
          onCreate={createProject}
          onCreated={(project) => {
            setCreatorOpen(false)
            selectProject(project)
            showToast(`'${project.projectName}' 프로젝트가 생성되었습니다`, 'success')
          }}
        />
      )}

      <ToastContainer />
    </div>
  )
}

function App() {
  return (
    <ToastProvider>
      <AppShell />
    </ToastProvider>
  )
}

export default App
