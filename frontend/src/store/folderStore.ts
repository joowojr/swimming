import { create } from 'zustand'
import { getFolders } from '../features/folders/folderApi.ts'
import type { Folder, FolderLoadStatus } from '../features/folders/folderTypes.ts'

/** 역할: 폴더 목록의 조회와 변경을 한곳에서 관리해, 화면마다 콜백을 배선하지 않게 한다. */
interface FolderStoreState {
  folders: Folder[]
  status: FolderLoadStatus
  ownerId: number | null

  load: (userId: number) => Promise<void>
  add: (folder: Folder) => void
  apply: (folder: Folder) => void
  updateHasSource: (folderId: number, hasSource: boolean) => void
  remove: (folderId: number) => void
  reset: () => void
}

// 사용자가 빠르게 바뀔 때 늦게 도착한 응답이 최신 목록을 덮어쓰지 않게 한다.
let latestRequestId = 0

export const useFolderStore = create<FolderStoreState>((set, get) => ({
  folders: [],
  status: 'idle',
  ownerId: null,

  load: async (userId) => {
    const requestId = ++latestRequestId

    // 다른 사용자의 목록이면 응답을 기다리지 않고 즉시 비운다.
    set(get().ownerId === userId
      ? { status: 'loading' }
      : { folders: [], status: 'loading', ownerId: userId })

    try {
      const folders = await getFolders()
      if (requestId !== latestRequestId) return
      set({ folders, status: 'ready' })
    } catch {
      if (requestId !== latestRequestId) return
      set({ status: 'error' })
    }
  },

  add: (folder) => set((current) => ({
    folders: [folder, ...current.folders],
    status: 'ready',
  })),

  apply: (folder) => set((current) => ({
    folders: current.folders.map((candidate) => candidate.id === folder.id ? folder : candidate),
  })),

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
    set({ folders: [], status: 'idle', ownerId: null })
  },
}))
