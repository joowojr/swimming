import type { ReactNode } from 'react'
import MiniMusicWidget from '../features/sessions/music/MiniMusicWidget'
import RecentPageButton from './RecentPageButton'
import SideNavigation from './SideNavigation'
import TopBar from './TopBar'
import styles from './AppShell.module.css'

interface AppShellProps {
  children: ReactNode
  userEmail: string | null
  onLogin: () => void
}

export default function AppShell({
  children,
  userEmail,
  onLogin,
}: AppShellProps) {
  return (
    <div className={styles['app-shell']}>
      <TopBar userEmail={userEmail} onLogin={onLogin} />
      <div className={styles['app-shell-body']}>
        <SideNavigation />
        <main className={styles['app-content']} id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>
      <div className={styles['floating-dock']}>
        <RecentPageButton />
        <MiniMusicWidget />
      </div>
    </div>
  )
}
