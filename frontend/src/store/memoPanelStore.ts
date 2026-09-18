import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * 역할: 메모 패널의 열림 여부와 너비를 핀보드·폴더 리스트·폴더 상세가 함께 쓰도록 한곳에 둔다.
 * 한 화면에서 접으면 다른 화면도 접힌 상태로 열리고, 새로고침해도 유지된다.
 */
export const MEMO_PANEL_MIN_WIDTH = 280
export const MEMO_PANEL_MAX_WIDTH = 640
export const MEMO_PANEL_DEFAULT_WIDTH = 360

interface MemoPanelState {
  isOpen: boolean
  width: number
  toggle: () => void
  setOpen: (isOpen: boolean) => void
  setWidth: (width: number) => void
}

function clampWidth(width: number) {
  if (!Number.isFinite(width)) return MEMO_PANEL_DEFAULT_WIDTH
  return Math.min(MEMO_PANEL_MAX_WIDTH, Math.max(MEMO_PANEL_MIN_WIDTH, Math.round(width)))
}

export const useMemoPanelStore = create<MemoPanelState>()(
  persist(
    (set) => ({
      isOpen: true,
      width: MEMO_PANEL_DEFAULT_WIDTH,
      toggle: () => set((state) => ({ isOpen: !state.isOpen })),
      setOpen: (isOpen) => set({ isOpen }),
      setWidth: (width) => set({ width: clampWidth(width) }),
    }),
    {
      name: 'swimming-memo-panel',
      partialize: ({ isOpen, width }) => ({ isOpen, width }),
      // 저장해 둔 값이 경계 밖이면 되돌린다.
      merge: (persisted, current) => {
        const saved = persisted as Partial<MemoPanelState> | undefined
        return {
          ...current,
          ...saved,
          width: clampWidth(saved?.width ?? current.width),
        }
      },
    },
  ),
)
