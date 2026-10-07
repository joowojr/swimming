import type { ComponentType } from 'react'
import type { IconProps } from '@tabler/icons-react'
import {
  IconCalendar,
  IconChecklist,
  IconFolder,
  IconLayoutDashboard,
  IconPlayerPlay,
  IconSparkles,
} from '@tabler/icons-react'

export interface NavigationItem {
  label: string
  icon: ComponentType<IconProps>
  href?: string
  end?: boolean
  disabled?: boolean
}

/** 사이드바 메뉴와 "이어서 보기" 버튼이 함께 쓴다. 이름과 아이콘의 출처는 여기 하나다. */
export const navigationItems: NavigationItem[] = [
  { label: '핀보드', icon: IconLayoutDashboard, href: '/pinboard', end: true },
  { label: '할 일', icon: IconChecklist, href: '/tasks', end: true },
  { label: '폴더', icon: IconFolder, href: '/folders', end: true },
  { label: '다이브 세션', icon: IconPlayerPlay, href: '/sessions', end: true },
  { label: '캘린더', icon: IconCalendar, disabled: true },
]

/** 사이드바 구분선 아래에 두는 메뉴. 독립 앱처럼 쓰는 화면이 여기에 온다. */
export const secondaryNavigationItems: NavigationItem[] = [
  { label: 'Cowork Board', icon: IconSparkles, href: '/agent-board', end: true },
]

/**
 * 경로가 속한 사이드바 항목. 상세 페이지도 그 기능의 페이지로 본다.
 * 예를 들어 /folders/123은 "폴더"에 속하고, 링크는 그 폴더로 그대로 돌아간다.
 */
export function findSection(pathname: string | undefined) {
  if (!pathname) return undefined
  return [...navigationItems, ...secondaryNavigationItems].find((item) => (
    item.href !== undefined && (pathname === item.href || pathname.startsWith(`${item.href}/`))
  ))
}
