import type { ReactNode } from 'react'
import SideNavigation from './SideNavigation'
import TopBar from './TopBar'
import styles from './AppShell.module.css'

interface AppShellProps {
  children: ReactNode
  userEmail: string | null
  folderCount: number | null
  onLogin: () => void
}

export default function AppShell({
  children,
  userEmail,
  folderCount,
  onLogin,
}: AppShellProps) {
  return (
    <div className={styles['app-shell']}>
      <TopBar userEmail={userEmail} onLogin={onLogin} />
      <div className={styles['app-shell-body']}>
        <SideNavigation folderCount={folderCount} />
        <main className={styles['app-content']} id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  )
}
