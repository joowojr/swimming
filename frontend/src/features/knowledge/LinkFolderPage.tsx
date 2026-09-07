import { Link, useNavigate, useParams } from 'react-router-dom'
import type { Folder, FolderLoadStatus } from '../folders/folderTypes'
import LinkFolderView from './LinkFolderView'
import styles from './LinkFolderPage.module.css'

interface LinkFolderPageProps {
  folders: Folder[]
  status: FolderLoadStatus
  onDeleted: (folderId: number) => void
}

/** 링크 폴더 화면의 라우트. 폴더는 이미 받아 둔 목록에서 찾는다. */
export default function LinkFolderPage({ folders, status, onDeleted }: LinkFolderPageProps) {
  const navigate = useNavigate()
  const { folderId } = useParams()
  const parsedFolderId = Number(folderId)
  const folder = Number.isSafeInteger(parsedFolderId)
    ? folders.find((candidate) => candidate.id === parsedFolderId)
    : undefined

  if (folder) {
    return (
      <LinkFolderView
        key={folder.id}
        folder={folder}
        onDeleted={(deletedFolderId) => {
          onDeleted(deletedFolderId)
          navigate('/folders?section=links', { replace: true })
        }}
      />
    )
  }

  if (status === 'idle' || status === 'loading') {
    return (
      <section className={styles.page} aria-busy="true">
        <p role="status">폴더를 불러오고 있습니다.</p>
      </section>
    )
  }

  return (
    <section className={styles.page} aria-labelledby="link-folder-error-title">
      <h1 id="link-folder-error-title">
        {status === 'error' ? '폴더를 불러오지 못했습니다.' : '폴더를 찾을 수 없습니다.'}
      </h1>
      <p>폴더 주소를 확인하거나 폴더 목록으로 돌아가 주세요.</p>
      <Link to="/folders?section=links">링크 폴더 목록</Link>
    </section>
  )
}
