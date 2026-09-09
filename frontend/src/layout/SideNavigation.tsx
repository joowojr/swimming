import type { ComponentType } from 'react'
import { NavLink } from 'react-router-dom'
import type { IconProps } from '@tabler/icons-react'
import {
  IconCalendar,
  IconChecklist,
  IconFolder,
  IconLayoutDashboard,
  IconPlayerPlay,
} from '@tabler/icons-react'
import styles from './SideNavigation.module.css'

interface NavigationItem {
  label: string
  icon: ComponentType<IconProps>
  href?: string
  end?: boolean
  disabled?: boolean
}

const navigationItems: NavigationItem[] = [
  { label: '핀보드', icon: IconLayoutDashboard, href: '/pinboard', end: true },
  { label: '할 일', icon: IconChecklist, href: '/tasks', end: true },
  { label: '폴더', icon: IconFolder, href: '/folders', end: true },
  { label: '다이브 세션', icon: IconPlayerPlay, href: '/sessions', end: true },
  { label: '캘린더', icon: IconCalendar, disabled: true },
]

export default function SideNavigation() {
  return (
    <aside className={styles['side-navigation']}>

      <nav aria-label="주요 메뉴">
        <p className={styles['navigation-label']}>Menu</p>
        <ul className={styles['navigation-list']}>
          {navigationItems.map((item) => {
            const Icon = item.icon

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
                  </NavLink>
                ) : (
                  <button
                    className={`${styles['navigation-item']} ${item.disabled ? styles['is-disabled'] : ''}`}
                    type="button"
                    title={`${item.label} · 준비 중`}
                    disabled={item.disabled}
                  >
                    <Icon size={19} stroke={1.8} aria-hidden="true" />
                    <span>{item.label}</span>
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      </nav>
    </aside>
  )
}
