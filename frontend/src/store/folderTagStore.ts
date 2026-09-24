import { create } from 'zustand'
import { getTags } from '../features/folders/folderApi.ts'
import type { FolderLoadStatus, FolderTag } from '../features/folders/folderTypes.ts'

/**
 * 역할: 폴더 태그 목록을 한 번 불러와 태그 관리 모달, 새 폴더 모달, 폴더 상세의 태그 선택이 함께 쓴다.
 *
 * 태그를 만들거나 고치거나 지운 곳이 이 store에 바로 반영해, 다른 화면이 다시 요청하지 않아도 같은 목록을 본다.
 * 폴더가 들고 있는 태그 이름은 폴더 목록의 몫이라 여기서 바꾸지 않는다.
 */
interface FolderTagStoreState {
  /** 이름 가나다순. */
  tags: FolderTag[]
  status: FolderLoadStatus

  /** 아직 불러오지 않았거나 실패했을 때만 요청한다. */
  ensureLoaded: () => Promise<void>
  /** 새 태그는 넣고, 같은 id가 있으면 바꾼다. */
  upsert: (tag: FolderTag) => void
  remove: (tagId: number) => void
  reset: () => void
}

function sortTags(tags: FolderTag[]) {
  return [...tags].sort((left, right) => left.name.localeCompare(right.name, 'ko'))
}

// 로그아웃으로 비운 뒤 늦게 도착한 응답이 목록을 되살리지 않게 한다.
let latestRequestId = 0

export const useFolderTagStore = create<FolderTagStoreState>((set, get) => ({
  tags: [],
  status: 'idle',

  ensureLoaded: async () => {
    const { status } = get()
    if (status === 'loading' || status === 'ready') return

    const requestId = ++latestRequestId
    set({ status: 'loading' })
    try {
      const tags = await getTags()
      if (requestId !== latestRequestId) return
      set({ tags: sortTags(tags), status: 'ready' })
    } catch {
      if (requestId !== latestRequestId) return
      set({ status: 'error' })
    }
  },

  upsert: (tag) => set((current) => ({
    tags: sortTags([...current.tags.filter((candidate) => candidate.id !== tag.id), tag]),
  })),

  remove: (tagId) => set((current) => ({
    tags: current.tags.filter((tag) => tag.id !== tagId),
  })),

  reset: () => {
    latestRequestId += 1
    set({ tags: [], status: 'idle' })
  },
}))
