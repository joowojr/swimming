import { useNavigate } from 'react-router-dom'
import ModeToggle from '../../components/ModeToggle'
import styles from './FolderViewSwitch.module.css'

type FolderView = 'tasks' | 'links'

const FOLDER_VIEWS: { value: FolderView; label: string }[] = [
  { value: 'tasks', label: '할 일' },
  { value: 'links', label: '링크' },
]

interface FolderViewSwitchProps {
  folderId: number
  current: FolderView
}

/**
 * 한 폴더의 두 화면을 오간다.
 *
 * 링크는 저장된 것이 있을 때만 볼 자리가 있으므로, 목록에서 받아 둔 `hasSource`로 판단한다.
 * 폴더를 열 때마다 링크가 있는지 따로 묻지 않는다.
 */
export default function FolderViewSwitch({ folderId, current }: FolderViewSwitchProps) {
  const navigate = useNavigate()

  return (
    <div className={styles.switch}>
      <ModeToggle
        ariaLabel="폴더 화면 전환"
        options={FOLDER_VIEWS}
        value={current}
        onChange={(next) => {
          if (next === current) return
          navigate(next === 'links' ? `/folders/${folderId}/links` : `/folders/${folderId}`)
        }}
      />
    </div>
  )
}
