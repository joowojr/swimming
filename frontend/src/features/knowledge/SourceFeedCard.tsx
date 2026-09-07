import { useState } from 'react'
import { IconExternalLink, IconLoader2, IconPencil } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import DeleteConfirmation from '../../components/DeleteConfirmation'
import DeleteIconButton from '../../components/DeleteIconButton'
import { deleteSource } from './knowledgeApi'
import { SOURCE_STATUS_LABEL } from './knowledgeLabels'
import { sourceMark } from './sourceIcon'
import { formatSavedAt } from './sourceTime'
import type { SourceCard, SourceDeleteResponse } from './knowledgeTypes'
import styles from './SourceFeedCard.module.css'

interface SourceFeedCardProps {
  source: SourceCard
  onDeleted: (sourceId: string, response: SourceDeleteResponse) => void
}

function isDigesting(source: SourceCard) {
  return source.status === 'PENDING' || source.status === 'PROCESSING'
}

/**
 * 저장된 Source 한 장.
 *
 * status가 COMPLETED가 아닌 카드도 그린다. 소화가 비동기로 바뀌면 저장 직후 응답이
 * PENDING으로 돌아오므로, 그때 화면을 고치지 않아도 되도록 처음부터 네 상태를 모두 그린다.
 */
export default function SourceFeedCard({ source, onDeleted }: SourceFeedCardProps) {
  const [isConfirming, setIsConfirming] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const subjects = source.subjects ?? []
  const savedAt = formatSavedAt(source.createdAt)

  const remove = async () => {
    if (isDeleting) return
    setIsDeleting(true)
    setDeleteError(null)
    try {
      const response = await deleteSource(source.sourceId)
      onDeleted(source.sourceId, response)
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setDeleteError(apiMessage ?? '링크를 삭제하지 못했습니다. 다시 시도해 주세요.')
      setIsDeleting(false)
    }
  }

  return (
    <article className={styles.card} data-status={source.status}>
      <div className={styles.top}>
        <div className={styles.meta}>
          {sourceMark(source.domain, 17, styles.mark)}
          {source.domain && <span className={styles.domain}>{source.domain}</span>}
          {savedAt && (
            <>
              <span className={styles.dot} aria-hidden="true">·</span>
              <span className={styles.saved}>{savedAt}</span>
            </>
          )}
          {source.status !== 'COMPLETED' && (
            <span className={styles.status} data-status={source.status}>
              {isDigesting(source) && (
                <IconLoader2 className={styles.spinner} size={12} stroke={1.8} aria-hidden="true" />
              )}
              {SOURCE_STATUS_LABEL[source.status]}
            </span>
          )}
        </div>
        <div className={styles.actions}>
          <a
            className={styles.origin}
            href={source.url}
            target="_blank"
            rel="noreferrer noopener"
          >
            원문
            <IconExternalLink size={14} stroke={1.8} aria-hidden="true" />
          </a>
          <DeleteIconButton
            className={styles.delete}
            iconSize={14}
            label={isConfirming ? '링크 삭제 취소' : '링크 삭제'}
            active={isConfirming}
            disabled={isDeleting}
            hideIcon={isConfirming}
            onClick={() => {
              setIsConfirming((current) => !current)
              setDeleteError(null)
            }}
          >
            {isConfirming ? '취소' : null}
          </DeleteIconButton>
        </div>
      </div>

      <h3 className={styles.title}>
        <a href={source.url} target="_blank" rel="noreferrer noopener">
          {source.title || source.url}
        </a>
      </h3>

      {source.topic && (
        <p className={styles.topic}>
          <IconPencil className={styles['topic-icon']} size={12} stroke={1.8} aria-hidden="true" />
          {source.topic.title}
        </p>
      )}

      {isDigesting(source) ? (
        <div className={styles.skeleton} aria-hidden="true">
          <span />
          <span />
        </div>
      ) : source.status === 'FAILED' ? (
        <p className={styles.notice}>
          내용을 정리하지 못했지만 링크는 그대로 저장되어 있어요.
        </p>
      ) : (
        source.summary && <p className={styles.summary}>{source.summary}</p>
      )}

      {isConfirming && (
        <DeleteConfirmation
          message="이 링크와 정리된 내용이 사라집니다. 개념은 다른 문서에 남습니다."
          ariaLabel="링크 삭제 확인"
          isDeleting={isDeleting}
          onCancel={() => setIsConfirming(false)}
          onConfirm={() => void remove()}
        />
      )}
      {deleteError && <p className={styles.error} role="alert">{deleteError}</p>}

      {subjects.length > 0 && (
        <div className={styles.chips}>
          {subjects.map((subject) => (
            <span key={subject.nodeId} className={styles.subject}>{subject.title}</span>
          ))}
        </div>
      )}
    </article>
  )
}
