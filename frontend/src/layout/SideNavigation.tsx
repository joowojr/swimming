import type { ComponentType } from 'react'
import { NavLink } from 'react-router-dom'
import type { IconProps } from '@tabler/icons-react'
import {
  IconCalendar,
  IconChartBar,
  IconChevronDown,
  IconFolder,
  IconLayoutDashboard,
  IconUsers,
} from '@tabler/icons-react'
import styles from './SideNavigation.module.css'

interface NavigationItem {
  label: string
  icon: ComponentType<IconProps>
  href?: string
  badge?: string
}

const navigationItems: NavigationItem[] = [
  { label: '대시보드', icon: IconLayoutDashboard, href: '/projects' },
  { label: '프로젝트', icon: IconFolder },
  { label: '그룹 세션', icon: IconUsers },
  { label: '통계', icon: IconChartBar },
  { label: '캘린더', icon: IconCalendar },
]

export default function SideNavigation({ projectCount }: { projectCount: number | null }) {
  return (
    <aside className={styles['side-navigation']}>
      <div className={styles['workspace-switcher-wrap']}>
        <button
          className={styles['workspace-switcher']}
          type="button"
          title="워크스페이스 전환 · 준비 중"
          disabled
        >
          <span className={styles['workspace-avatar']} aria-hidden="true">
            S
          </span>
          <span className={styles['workspace-copy']}>
            <strong>스위밍</strong>
            <span>개인 워크스페이스</span>
          </span>
          <IconChevronDown className={styles['workspace-chevron']} size={16} aria-hidden="true" />
        </button>
      </div>

      <nav aria-label="주요 메뉴">
        <p className={styles['navigation-label']}>Menu</p>
        <ul className={styles['navigation-list']}>
          {navigationItems.map((item) => {
            const Icon = item.icon
            const badge = item.label === '프로젝트' && projectCount !== null
              ? String(projectCount)
              : item.badge

            return (
              <li key={item.label}>
                {item.href ? (
                  <NavLink
                    className={({ isActive }) => (
                      `${styles['navigation-item']} ${isActive ? styles['is-active'] : ''}`
                    )}
                    to={item.href}
                  >
                    <Icon size={19} stroke={1.8} aria-hidden="true" />
                    <span>{item.label}</span>
                    {badge && (
                      <span className={styles['navigation-badge']} aria-label={`프로젝트 ${badge}개`}>
                        {badge}
                      </span>
                    )}
                  </NavLink>
                ) : (
                  <button
                    className={styles['navigation-item']}
                    type="button"
                    title={`${item.label} · 준비 중`}
                    disabled
                  >
                    <Icon size={19} stroke={1.8} aria-hidden="true" />
                    <span>{item.label}</span>
                    {badge && (
                      <span className={styles['navigation-badge']} aria-label={`프로젝트 ${badge}개`}>
                        {badge}
                      </span>
                    )}
                  </button>
                )}
              </li>
            )
          })}
        </ul>

        <div className={styles['favorites-section']}>
          <p className={styles['navigation-label']}>Favorites</p>
          <p className={styles['favorites-empty']}>즐겨찾기한 프로젝트가 여기에 표시됩니다.</p>
        </div>
      </nav>
    </aside>
  )
}
