import type { ReactNode } from 'react'
import SideNavigation from './SideNavigation'
import TopBar from './TopBar'
import styles from './AppShell.module.css'

interface AppShellProps {
  children: ReactNode
  userEmail: string | null
  projectCount: number | null
  onLogin: () => void
}

export default function AppShell({
  children,
  userEmail,
  projectCount,
  onLogin,
}: AppShellProps) {
  return (
    <div className={styles['app-shell']}>
      <TopBar userEmail={userEmail} onLogin={onLogin} />
      <div className={styles['app-shell-body']}>
        <SideNavigation projectCount={projectCount} />
        <main className={styles['app-content']} id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  )
}
