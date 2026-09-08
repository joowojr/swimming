import { Link } from 'react-router-dom'
import DdayChip from '../../components/DdayChip'
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
  // 톤은 화면마다 같아야 하므로 목록 순서가 아닌 폴더 id로 고른다.
  const routeTone = [
    styles['is-clay'],
    styles['is-sky'],
    styles['is-pale'],
    styles['is-moss'],
  ][folder.id % 4]

  return (
    <Link
      className={`${styles.card} ${routeTone}`}
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
  )
}
