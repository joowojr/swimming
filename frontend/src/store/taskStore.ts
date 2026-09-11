import { create } from 'zustand'
import { getTaskList } from '../features/tasks/taskApi'
import type { TaskCacheEntry, TaskSort } from '../features/tasks/taskTypes'

/**
 * 역할: task의 단일 출처. 제목·상태·중요·즉시를 한곳에서 들고 있어,
 * 캘린더·매트릭스·폴더 목록이 같은 task를 각자 복제해 두지 않게 한다.
 * 목록 순서(allIds)는 전체 조회로만 채운다. 다른 응답에 실려 온 task는 캐시만 채우고 순서에 끼어들지 않는다.
 */
type TaskLoadStatus = 'idle' | 'loading' | 'ready' | 'error'

interface TaskStoreState {
  byId: Record<number, TaskCacheEntry>
  // 전체 조회로 받은 순서(생성 최신순). 아직 받지 않았으면 null.
  allIds: number[] | null
  status: TaskLoadStatus
  // 목록 멤버십이 바뀐 횟수. 페이징된 목록(매트릭스)은 로컬 패치가 안 되어 이 값을 보고 다시 받는다.
  listRevision: number

  loadAll: (sort?: TaskSort) => Promise<void>
  add: (task: TaskCacheEntry) => void
  upsert: (tasks: TaskCacheEntry[]) => void
  remove: (taskIds: number[]) => void
  reset: () => void
}

// 화면을 빠르게 오갈 때 늦게 도착한 응답이 최신 목록을 덮어쓰지 않게 한다.
let latestRequestId = 0

function toById(tasks: TaskCacheEntry[]): Record<number, TaskCacheEntry> {
  const next: Record<number, TaskCacheEntry> = {}
  tasks.forEach((task) => {
    next[task.id] = task
  })
  return next
}

export const useTaskStore = create<TaskStoreState>((set) => ({
  byId: {},
  allIds: null,
  status: 'idle',
  listRevision: 0,

  loadAll: async (sort = 'desc') => {
    const requestId = ++latestRequestId
    set({ status: 'loading' })

    try {
      const tasks = await getTaskList(sort)
      if (requestId !== latestRequestId) return
      set((current) => ({
        byId: { ...current.byId, ...toById(tasks) },
        allIds: tasks.map((task) => task.id),
        status: 'ready',
        listRevision: current.listRevision + 1,
      }))
    } catch {
      if (requestId !== latestRequestId) return
      set({ status: 'error' })
    }
  },

  // 새로 만든 task. 전체 목록이 생성 최신순이라 앞에 붙인다.
  add: (task) => set((current) => ({
    byId: { ...current.byId, [task.id]: task },
    allIds: current.allIds === null ? null : [task.id, ...current.allIds],
    status: 'ready',
    listRevision: current.listRevision + 1,
  })),

  // 캘린더·매트릭스·폴더 상세 응답에 실려 온 task를 캐시에 넣는다.
  // 변경 응답 하나를 반영할 때도 upsert([task])를 쓴다. 목록 순서는 건드리지 않는다.
  upsert: (tasks) => set((current) => ({
    byId: { ...current.byId, ...toById(tasks) },
  })),

  remove: (taskIds) => set((current) => {
    const removed = new Set(taskIds)
    const byId: Record<number, TaskCacheEntry> = {}
    Object.values(current.byId).forEach((task) => {
      if (!removed.has(task.id)) byId[task.id] = task
    })
    return {
      byId,
      allIds: current.allIds?.filter((id) => !removed.has(id)) ?? null,
    }
  }),

  reset: () => {
    latestRequestId += 1
    set((current) => ({ byId: {}, allIds: null, status: 'idle', listRevision: current.listRevision + 1 }))
  },
}))
