import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * 사이드바에서 마지막으로 본 페이지. 브라우저에만 남으므로 기기끼리 공유되지 않는다.
 *
 * 두 개를 들고 있는 이유는 하나만 보여주기 위해서다. 위젯이 사이드바에 있어 어느 화면에서나
 * 보이는데, 맨 앞이 지금 보고 있는 페이지면 가리킬 곳이 없다. 바로 뒤 항목이 그 대비책이다.
 */
const KEEP = 2

interface RecentPageState {
  /** 최근 순으로 담긴 사이드바 경로. 이름은 사이드바 정의에서 찾으므로 경로만 저장한다. */
  hrefs: string[]
  record: (href: string) => void
}

export const useRecentPageStore = create<RecentPageState>()(
  persist(
    (set) => ({
      hrefs: [],
      record: (href) => set((state) => (
        state.hrefs[0] === href
          ? state
          : { hrefs: [href, ...state.hrefs.filter((entry) => entry !== href)].slice(0, KEEP) }
      )),
    }),
    {
      name: 'swimming-recent-page',
      partialize: ({ hrefs }) => ({ hrefs }),
    },
  ),
)
