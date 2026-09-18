import type { ComponentType } from 'react'
import { IconClock, IconSparkles } from '@tabler/icons-react'
import type { IconProps } from '@tabler/icons-react'

export interface UpdateNoteItem {
  icon: ComponentType<IconProps>
  title: string
  description: string
}

export interface UpdateNote {
  /** 닫은 기록을 여기에 묶는다. 배포마다 새로 짓는다. 같은 id를 다시 쓰면 닫은 사람에게 다시 뜨지 않는다. */
  id: string
  /** 게시일. 한국 시간 그날 0시부터 센다. */
  publishedAt: string
  /** 게시일부터 며칠 보여 줄지. 비우면 DEFAULT_VISIBLE_DAYS. */
  visibleDays?: number
  items: UpdateNoteItem[]
}

const DEFAULT_VISIBLE_DAYS = 14
const DAY_MS = 24 * 60 * 60 * 1000

/**
 * 역할: 배포마다 사용자에게 알릴 새 기능. 코드는 그대로 두고 여기만 고친다.
 *
 * 알릴 기능이 있는 배포에서 맨 위에 하나를 더한다. 보여 주는 것은 맨 위의 것 하나뿐이고,
 * 게시일부터 정한 날수가 지나면 저절로 사라진다. 지난 항목은 지우지 않고 남겨 둔다 —
 * 무엇을 언제 알렸는지 기록이 된다.
 */
export const UPDATE_NOTES: UpdateNote[] = [
  {
    id: '2026-09-18',
    publishedAt: '2026-09-18',
    items: [
      {
        icon: IconClock,
        title: '타이머 · 최근 방문 · 음악',
        description: '어느 화면에서나 타이머를 시작하고, 이전 화면으로 돌아가고, 음악을 재생할 수 있어요. 타이머와 세션이 끝나면 짧은 알림음이 울려요.',
      },
      {
        icon: IconSparkles,
        title: '쌓인 링크를 카테고리로 묶어요',
        description: '링크 폴더의 그래프 보기에서 ‘AI 카테고리 정리’를 누르면 링크들을 분류합니다. 정리한 뒤 새로 저장한 링크는 알맞은 카테고리에 들어가요.',
      },
    ],
  },
]

/** 지금 보여 줄 소식. 맨 위의 것이 표시 기간 안이면 그것, 아니면 없다. */
export function currentUpdateNote(notes: UpdateNote[], now: number): UpdateNote | null {
  const latest = notes[0]
  if (!latest) return null

  const startsAt = Date.parse(`${latest.publishedAt}T00:00:00+09:00`)
  const endsAt = startsAt + (latest.visibleDays ?? DEFAULT_VISIBLE_DAYS) * DAY_MS
  return now >= startsAt && now < endsAt ? latest : null
}
