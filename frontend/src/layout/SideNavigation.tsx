import { NavLink } from 'react-router-dom'
import { navigationItems } from './navigationItems'
import styles from './SideNavigation.module.css'

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
