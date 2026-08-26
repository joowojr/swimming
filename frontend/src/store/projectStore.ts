import { create } from 'zustand'
import { getProjects } from '../features/projects/projectApi'
import type { Project, ProjectLoadStatus } from '../features/projects/projectTypes'

/** 역할: 프로젝트 목록의 조회와 변경을 한곳에서 관리해, 화면마다 콜백을 배선하지 않게 한다. */
interface ProjectStoreState {
  projects: Project[]
  status: ProjectLoadStatus
  ownerId: number | null

  load: (userId: number) => Promise<void>
  add: (project: Project) => void
  apply: (project: Project) => void
  remove: (projectId: number) => void
  reset: () => void
}

// 사용자가 빠르게 바뀔 때 늦게 도착한 응답이 최신 목록을 덮어쓰지 않게 한다.
let latestRequestId = 0

export const useProjectStore = create<ProjectStoreState>((set, get) => ({
  projects: [],
  status: 'idle',
  ownerId: null,

  load: async (userId) => {
    const requestId = ++latestRequestId

    // 다른 사용자의 목록이면 응답을 기다리지 않고 즉시 비운다.
    set(get().ownerId === userId
      ? { status: 'loading' }
      : { projects: [], status: 'loading', ownerId: userId })

    try {
      const projects = await getProjects()
      if (requestId !== latestRequestId) return
      set({ projects, status: 'ready' })
    } catch {
      if (requestId !== latestRequestId) return
      set({ status: 'error' })
    }
  },

  add: (project) => set((current) => ({
    projects: [project, ...current.projects],
    status: 'ready',
  })),

  apply: (project) => set((current) => ({
    projects: current.projects.map((candidate) => candidate.id === project.id ? project : candidate),
  })),

  remove: (projectId) => set((current) => ({
    projects: current.projects.filter((project) => project.id !== projectId),
  })),

  reset: () => {
    latestRequestId += 1
    set({ projects: [], status: 'idle', ownerId: null })
  },
}))
