import { create } from 'zustand'
import { getFolders } from '../features/folders/folderApi.ts'
import type {
  Folder,
  FolderLoadStatus,
  FolderStatusFilter,
} from '../features/folders/folderTypes.ts'

/** 역할: 폴더 목록의 조회와 변경을 한곳에서 관리해, 화면마다 콜백을 배선하지 않게 한다. */
interface FolderStoreState {
  folders: Folder[]
  status: FolderLoadStatus
  ownerId: number | null
  /** 목록을 어떤 상태로 좁혀 볼지. 서버 조회와 목록 유지 규칙이 함께 쓴다. */
  filter: FolderStatusFilter

  load: (userId: number) => Promise<void>
  changeFilter: (filter: FolderStatusFilter) => Promise<void>
  add: (folder: Folder) => void
  apply: (folder: Folder) => void
  updateHasSource: (folderId: number, hasSource: boolean) => void
  remove: (folderId: number) => void
  reset: () => void
}

/**
 * 서버 목록과 같은 규칙으로 정렬한다. 고정한 폴더가 먼저, 그 안에서는 최근에 고정한
 * 순이고, 고정하지 않은 폴더는 최근 생성 순이다. 고정을 바꾼 뒤 목록을 다시 불러오지
 * 않아도 자리가 맞게 한다.
 */
function sortFolders(folders: Folder[]): Folder[] {
  return [...folders].sort((left, right) => {
    if (left.pinnedAt && right.pinnedAt) {
      return Date.parse(right.pinnedAt) - Date.parse(left.pinnedAt)
    }
    if (left.pinnedAt) return -1
    if (right.pinnedAt) return 1
    return Date.parse(right.createdAt) - Date.parse(left.createdAt)
  })
}

/** 지금 보고 있는 목록에 남을 폴더인지 본다. 상태를 바꾼 뒤 목록을 다시 부르지 않아도 자리가 맞게 한다. */
function matchesFilter(folder: Folder, filter: FolderStatusFilter): boolean {
  if (filter === 'ALL') return true
  if (filter === 'ACTIVE') return folder.status !== 'ARCHIVED'
  return folder.status === filter
}

// 사용자가 빠르게 바뀔 때 늦게 도착한 응답이 최신 목록을 덮어쓰지 않게 한다.
let latestRequestId = 0

export const useFolderStore = create<FolderStoreState>((set, get) => ({
  folders: [],
  status: 'idle',
  ownerId: null,
  filter: 'ACTIVE',

  load: async (userId) => {
    const requestId = ++latestRequestId

    // 다른 사용자의 목록이면 응답을 기다리지 않고 즉시 비운다.
    set(get().ownerId === userId
      ? { status: 'loading' }
      : { folders: [], status: 'loading', ownerId: userId })

    try {
      const folders = await getFolders(get().filter)
      if (requestId !== latestRequestId) return
      set({ folders, status: 'ready' })
    } catch {
      if (requestId !== latestRequestId) return
      set({ status: 'error' })
    }
  },

  changeFilter: async (filter) => {
    const { ownerId, filter: currentFilter } = get()
    if (filter === currentFilter) return
    set({ filter })
    if (ownerId !== null) await get().load(ownerId)
  },

  add: (folder) => set((current) => ({
    folders: matchesFilter(folder, current.filter)
      ? sortFolders([folder, ...current.folders])
      : current.folders,
    status: 'ready',
  })),

  apply: (folder) => set((current) => {
    const others = current.folders.filter((candidate) => candidate.id !== folder.id)
    return {
      folders: matchesFilter(folder, current.filter)
        ? sortFolders([folder, ...others])
        : others,
    }
  }),

  updateHasSource: (folderId, hasSource) => set((current) => ({
    folders: current.folders.map((folder) => folder.id === folderId
      ? { ...folder, hasSource }
      : folder),
  })),

  remove: (folderId) => set((current) => ({
    folders: current.folders.filter((folder) => folder.id !== folderId),
  })),

  reset: () => {
    latestRequestId += 1
    set({ folders: [], status: 'idle', ownerId: null, filter: 'ACTIVE' })
  },
}))
