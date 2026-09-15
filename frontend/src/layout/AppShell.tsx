import type { ReactNode } from 'react'
import { useMatch } from 'react-router-dom'
import MemoSplitLayout from '../components/MemoSplitLayout'
import NoteCard from '../features/note/NoteCard'
import MiniMusicWidget from '../features/sessions/music/MiniMusicWidget'
import { useFolderStore } from '../store/folderStore'
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
  const folders = useFolderStore((state) => state.folders)
  const isPinboard = useMatch('/pinboard') !== null
  const isFolderList = useMatch('/folders') !== null
  const folderDetailMatch = useMatch('/folders/:folderId/*')

  const parsedFolderId = Number(folderDetailMatch?.params.folderId)
  const folderId = Number.isSafeInteger(parsedFolderId) && parsedFolderId > 0 ? parsedFolderId : null
  const hasMemo = isPinboard || isFolderList || folderId !== null
  // 폴더 상세에서는 그 폴더만 정리 대상으로 둔다. 화면 밖 폴더를 고르게 하지 않는다.
  const memoFolders = folderId !== null ? folders.filter((folder) => folder.id === folderId) : folders

  const content = (
    <main className={styles['app-content']} id="main-content" tabIndex={-1}>
      {children}
    </main>
  )

  return (
    <div className={styles['app-shell']}>
      <TopBar userEmail={userEmail} onLogin={onLogin} />
      <div className={styles['app-shell-body']}>
        <SideNavigation />
        {hasMemo ? (
          // 라우트 바깥에 두어야 화면을 옮겨도 노트가 다시 마운트되지 않는다.
          // 폴더가 바뀔 때만 key로 새 인스턴스를 만들어 노트 맥락을 바꾼다.
          <MemoSplitLayout
            className={styles['memo-split']}
            memo={(
              <NoteCard
                key={folderId ?? 'default'}
                folders={memoFolders}
                folderId={folderId ?? undefined}
              />
            )}
          >
            {content}
          </MemoSplitLayout>
        ) : content}
      </div>
      <div className={styles['floating-dock']}>
        <RecentPageButton />
        <MiniMusicWidget />
      </div>
    </div>
  )
}
