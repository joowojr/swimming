import { create } from 'zustand'
import { getActiveSession } from '../features/sessions/sessionApi'
import type { SessionDetailResponse } from '../features/sessions/sessionTypes'

/**
 * 역할: 진행 중인 세션 하나를 단일 출처로 들고 있어, 홈 위젯과 세션 화면이 같은 정보를 각자 조회하지 않게 한다.
 * 서버가 사용자당 진행 중 세션을 하나로 제한하므로(sessions.active_user_id 유니크) 슬롯도 하나만 둔다.
 * 종료된 세션은 이 store가 다루지 않고, 세션 화면이 직접 조회한다.
 */
type ActiveSessionStatus = 'idle' | 'loading' | 'ready' | 'error'

interface ActiveSessionStoreState {
  session: SessionDetailResponse | null
  status: ActiveSessionStatus

  // 방금 생성된 세션인지. 새 세션에는 노트가 있을 수 없어 첫 진입의 노트 조회를 건너뛰는 데만 쓴다.
  isJustCreated: boolean

  load: () => Promise<void>
  markCreated: (session: SessionDetailResponse) => void
  consumeJustCreated: () => void
  apply: (session: SessionDetailResponse) => void
  clear: () => void
}

// 화면을 빠르게 오갈 때 늦게 도착한 응답이 최신 상태를 덮어쓰지 않게 한다.
let latestRequestId = 0

export const useActiveSessionStore = create<ActiveSessionStoreState>((set) => ({
  session: null,
  status: 'idle',
  isJustCreated: false,

  load: async () => {
    const requestId = ++latestRequestId
    set({ status: 'loading' })

    try {
      const session = await getActiveSession()
      if (requestId !== latestRequestId) return
      set({ session, status: 'ready' })
    } catch {
      if (requestId !== latestRequestId) return
      set({ status: 'error' })
    }
  },

  // 생성 응답을 그대로 적재한다. 진행 중인 조회 응답이 이걸 덮어쓰지 않게 요청 번호를 넘긴다.
  markCreated: (session) => {
    latestRequestId += 1
    set({ session, status: 'ready', isJustCreated: true })
  },

  consumeJustCreated: () => set({ isJustCreated: false }),

  apply: (session) => set((current) => current.session?.id === session.id
    ? { session }
    : current),

  clear: () => {
    latestRequestId += 1
    set({ session: null, status: 'ready', isJustCreated: false })
  },
}))
