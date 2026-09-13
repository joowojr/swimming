import { useState } from 'react'
import { Link } from 'react-router-dom'
import { IconPin, IconPinFilled } from '@tabler/icons-react'
import DdayChip from '../../components/DdayChip'
import { useFolderStore } from '../../store/folderStore.ts'
import { pinFolder } from './folderApi.ts'
import type { Folder } from './folderTypes.ts'
import styles from './FolderCard.module.css'

interface FolderCardProps {
  folder: Folder
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
})

function formatTargetDate(targetDate: string) {
  return dateFormatter.format(new Date(`${targetDate}T00:00:00`))
}

export default function FolderCard({ folder }: FolderCardProps) {
  const applyFolderToStore = useFolderStore((state) => state.apply)
  const [isPinning, setIsPinning] = useState(false)
  const [pinError, setPinError] = useState<string | null>(null)
  const isPinned = folder.pinnedAt !== null

  // 톤은 화면마다 같아야 하므로 목록 순서가 아닌 폴더 id로 고른다.
  const routeTone = [
    styles['is-clay'],
    styles['is-sky'],
    styles['is-pale'],
    styles['is-moss'],
  ][folder.id % 4]

  const togglePin = async () => {
    if (isPinning) return
    setIsPinning(true)
    setPinError(null)
    try {
      applyFolderToStore(await pinFolder(folder.id, !isPinned))
    } catch {
      setPinError(isPinned ? '고정을 해제하지 못했습니다.' : '고정하지 못했습니다.')
    } finally {
      setIsPinning(false)
    }
  }

  return (
    // 고정 버튼은 카드 링크 바깥에 둔다. 링크 안의 버튼은 중첩 상호작용이 된다.
    <div className={`${styles.wrapper} ${routeTone}`}>
      <Link
        className={styles.card}
        to={`/folders/${folder.id}`}
        aria-label={`${folder.name} 상세 보기${folder.hasSource ? ', 저장된 링크 있음' : ''}`}
      >
        <span className={styles.route} aria-hidden="true" />
        <div className={styles.heading}>
          <div className={styles['title-group']}>
            {folder.tag && <span className={styles.tag}>{folder.tag.name}</span>}
            <h3>{folder.name}</h3>
          </div>
          <span className={styles['heading-chips']}>
            {/* 날짜는 아래 footer가 말한다. 위에서는 남은 일수만 본다. */}
            <DdayChip targetDate={folder.targetDate} thresholdDays={Number.POSITIVE_INFINITY} />
          </span>
        </div>
        <p className={styles.description}>
          {folder.description || '폴더 설명이 아직 없습니다.'}
        </p>
        <div className={styles['card-bottom']}>
          {folder.hasSource && (
            <div className={styles['source-row']}>
              <span
                className={styles['source-mark']}
                title="저장된 링크 있음"
                aria-hidden="true"
              >
                🔗
              </span>
            </div>
          )}
          <div className={styles.footer}>
            <span>목표일</span>
            <strong>
              {folder.targetDate ? formatTargetDate(folder.targetDate) : '설정하지 않음'}
            </strong>
          </div>
        </div>
      </Link>
      <button
        type="button"
        className={styles['pin-button']}
        data-pinned={isPinned || undefined}
        onClick={togglePin}
        disabled={isPinning}
        aria-pressed={isPinned}
        title={isPinned ? '고정 해제' : '목록 위에 고정'}
      >
        {isPinned
          ? <IconPinFilled size={16} aria-hidden="true" />
          : <IconPin size={16} aria-hidden="true" />}
        <span className="sr-only">
          {isPinned ? `${folder.name} 고정 해제` : `${folder.name} 목록 위에 고정`}
        </span>
      </button>
      {pinError && <p className={styles['pin-error']} role="status">{pinError}</p>}
    </div>
  )
}
