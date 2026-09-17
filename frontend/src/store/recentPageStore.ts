import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * 사이드바에서 마지막으로 본 페이지. 브라우저에만 남으므로 기기끼리 공유되지 않는다.
 *
 * 두 개를 들고 있는 이유는 하나만 보여주기 위해서다. 위젯이 어느 화면에서나 보이는데,
 * 맨 앞이 지금 보고 있는 페이지면 가리킬 곳이 없다. 바로 뒤 항목이 그 대비책이다.
 */
const KEEP = 2

/**
 * 어느 화면인가는 경로가 정한다. 기록은 쿼리까지 남기지만 — 돌아가면 보던 화면이어야 하므로 —
 * 같은 페이지인지 따질 때는 쿼리를 뗀다.
 *
 * 이 구분이 흐려지면 두 자리가 한 페이지로 다 차 버린다. 할 일의 매트릭스(/tasks)와
 * 리스트(/tasks?view=list)는 서로 다른 주소지만 같은 페이지라, 쿼리까지 같아야 같은 것으로
 * 치면 둘이 자리를 하나씩 차지하고 그 페이지에서는 가리킬 곳이 남지 않는다.
 */
export function pathOf(href: string) {
  return href.split('?')[0]
}

interface RecentPageState {
  /** 최근 순으로 담긴 사이드바 경로. 이름은 사이드바 정의에서 찾으므로 경로만 저장한다. */
  hrefs: string[]
  record: (href: string) => void
}

export const useRecentPageStore = create<RecentPageState>()(
  persist(
    (set) => ({
      hrefs: [],
      record: (href) => set((state) => {
        if (state.hrefs[0] === href) return state

        const path = pathOf(href)
        return {
          hrefs: [href, ...state.hrefs.filter((entry) => pathOf(entry) !== path)].slice(0, KEEP),
        }
      }),
    }),
    {
      name: 'swimming-recent-page',
      partialize: ({ hrefs }) => ({ hrefs }),
    },
  ),
)
