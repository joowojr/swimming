import { NavLink } from 'react-router-dom'
import { navigationItems, secondaryNavigationItems } from './navigationItems'
import type { NavigationItem } from './navigationItems'
import styles from './SideNavigation.module.css'

function renderItem(item: NavigationItem) {
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
}

interface SideNavigationProps {
  /** page면 페이지 배경색을 그대로 써서 독립 앱처럼 보이게 한다. */
  tone?: 'default' | 'page'
}

export default function SideNavigation({ tone = 'default' }: SideNavigationProps) {
  return (
    <aside className={`${styles['side-navigation']} ${tone === 'page' ? styles['is-page-tone'] : ''}`}>

      <nav aria-label="주요 메뉴">
        <p className={styles['navigation-label']}>Menu</p>
        <ul className={styles['navigation-list']}>
          {navigationItems.map(renderItem)}
        </ul>
        <hr className={styles['navigation-divider']} />
        <ul className={styles['navigation-list']}>
          {secondaryNavigationItems.map(renderItem)}
        </ul>
      </nav>

    </aside>
  )
}
