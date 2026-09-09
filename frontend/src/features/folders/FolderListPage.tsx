import { IconFolders, IconPlus, IconTags } from '@tabler/icons-react'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import FolderCard from './FolderCard.tsx'
import type { Folder } from './folderTypes.ts'
import type { FolderLoadStatus } from './folderTypes.ts'
import styles from './FolderListPage.module.css'

interface ProjectListPageProps {
  folders: Folder[]
  status: FolderLoadStatus
  onRetry: () => void
  onOpenCreate: () => void
  onOpenTagManage: () => void
}

export default function FolderListPage({
  folders,
  status,
  onRetry,
  onOpenCreate,
  onOpenTagManage,
}: ProjectListPageProps) {
  return (
    <section className={styles.page} aria-labelledby="folders-page-title">
      <header className={styles.heading}>
        <div>
          <h1 id="folders-page-title">폴더</h1>
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.secondary} onClick={onOpenTagManage}>
            <IconTags size={17} aria-hidden="true" />
            태그 관리
          </button>
          <ModalTriggerButton
            dialogId="create-folder-dialog"
            icon={<IconPlus size={18} aria-hidden="true" />}
            onClick={onOpenCreate}
          >
            새 폴더
          </ModalTriggerButton>
        </div>
      </header>

      {status === 'loading' || status === 'idle' ? (
        <div className={styles.state} role="status">
          <span className={styles['state-mark']} aria-hidden="true" />
          <p>폴더를 불러오고 있습니다.</p>
        </div>
      ) : status === 'error' ? (
        <div className={styles.state}>
          <p>폴더 목록을 불러오지 못했습니다.</p>
          <button type="button" onClick={onRetry}>다시 불러오기</button>
        </div>
      ) : (
        <>
          <div className={styles['section-heading']}>
            <span>{folders.length}개</span>
          </div>
          {folders.length === 0 ? (
            <div className={styles.empty}>
              <IconFolders size={28} stroke={1.5} aria-hidden="true" />
              <h3>폴더를 시작할 준비가 되었습니다.</h3>
              <p>새 폴더를 만들면 이곳에서 한눈에 확인할 수 있습니다.</p>
            </div>
          ) : (
            <div className={styles.grid}>
              {folders.map((folder) => (
                <FolderCard key={folder.id} folder={folder} />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
