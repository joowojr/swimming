import type { ComponentType } from 'react'
import { NavLink } from 'react-router-dom'
import type { IconProps } from '@tabler/icons-react'
import {
  IconCalendar,
  IconFolder,
  IconLayoutDashboard,
  IconUsers,
} from '@tabler/icons-react'
import styles from './SideNavigation.module.css'

interface NavigationItem {
  label: string
  icon: ComponentType<IconProps>
  href?: string
  end?: boolean
  badge?: string
}

const navigationItems: NavigationItem[] = [
  { label: '핀보드', icon: IconLayoutDashboard, href: '/pinboard', end: true },
  { label: '프로젝트', icon: IconFolder, href: '/projects', end: true },
  { label: '다이브 세션', icon: IconUsers, href: '/sessions', end: true },
  { label: '캘린더', icon: IconCalendar },
]

export default function SideNavigation({ projectCount }: { projectCount: number | null }) {
  return (
    <aside className={styles['side-navigation']}>

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
                    end={item.end}
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
