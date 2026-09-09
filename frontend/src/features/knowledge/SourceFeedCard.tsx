import { useState } from 'react'
import {
  IconExternalLink,
  IconEye,
  IconEyeClosed,
  IconInfoCircle,
  IconLoader2,
  IconPencil,
  IconRefresh,
} from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import DeleteConfirmation from '../../components/DeleteConfirmation'
import DeleteIconButton from '../../components/DeleteIconButton'
import { deleteSource, markSourceRead, markSourceUnread, retrySource } from './knowledgeApi'
import { SOURCE_STATUS_LABEL, sourceFailureMessage } from './knowledgeLabels'
import { sourceMark } from './sourceIcon'
import { formatSavedAt } from './sourceTime'
import type { SourceCard, SourceDeleteResponse } from './knowledgeTypes'
import styles from './SourceFeedCard.module.css'

interface SourceFeedCardProps {
  source: SourceCard
  onDeleted: (sourceId: string, response: SourceDeleteResponse) => void
  onRetried: (source: SourceCard) => void
  /** 읽음 표시가 바뀌었음을 목록에 알린다. 목록이 카드 상태의 주인이다. */
  onReadChanged: (sourceId: string, readAt: string | null) => void
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
export default function SourceFeedCard({
  source,
  onDeleted,
  onRetried,
  onReadChanged,
}: SourceFeedCardProps) {
  const [isConfirming, setIsConfirming] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [isRetrying, setIsRetrying] = useState(false)
  const [retryError, setRetryError] = useState<string | null>(null)
  const [isTogglingRead, setIsTogglingRead] = useState(false)
  const [readError, setReadError] = useState<string | null>(null)
  const isRead = source.readAt !== null
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

  /**
   * 눌린 즉시 화면을 바꾸고 요청은 뒤따라 보낸다. 읽음은 되돌리기 쉬운 표시라
   * 응답을 기다리며 멈춰 있을 이유가 없다. 실패하면 원래 상태로 되돌린다.
   *
   * <p>여기서 만든 시각은 화면 표시용이 아니다. 목록을 다시 불러오면 서버가 정한
   * 시각으로 덮인다. 카드는 읽었는지 여부만 그리므로 둘이 어긋나 보이지 않는다.
   */
  const toggleRead = async () => {
    if (isTogglingRead) return
    const next = isRead ? null : new Date().toISOString()
    setIsTogglingRead(true)
    setReadError(null)
    onReadChanged(source.sourceId, next)
    try {
      if (next) {
        await markSourceRead(source.sourceId)
      } else {
        await markSourceUnread(source.sourceId)
      }
    } catch (error: unknown) {
      onReadChanged(source.sourceId, source.readAt)
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setReadError(apiMessage ?? '읽음 표시를 바꾸지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsTogglingRead(false)
    }
  }

  const retry = async () => {
    if (isRetrying) return
    setIsRetrying(true)
    setRetryError(null)
    try {
      const retried = await retrySource(source.sourceId)
      onRetried(retried)
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setRetryError(apiMessage ?? '링크를 다시 분석하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsRetrying(false)
    }
  }

  return (
    <article className={styles.card} data-status={source.status} data-read={isRead}>
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
          {isDigesting(source) && (
            <span className={styles.status} data-status={source.status}>
              <IconLoader2 className={styles.spinner} size={12} stroke={1.8} aria-hidden="true" />
              {SOURCE_STATUS_LABEL[source.status]}
            </span>
          )}
        </div>
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.read}
            aria-label={isRead ? '읽음 표시 해제' : '읽음으로 표시'}
            aria-pressed={isRead}
            disabled={isTogglingRead}
            onClick={() => void toggleRead()}
          >
            {isRead
              ? <IconEye size={15} stroke={1.8} aria-hidden="true" />
              : <IconEyeClosed size={15} stroke={1.8} aria-hidden="true" />}
          </button>
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
          <span className={styles['title-text']}>{source.title || source.url}</span>
          <IconExternalLink
            className={styles['title-icon']}
            size={14}
            stroke={1.8}
            aria-hidden="true"
          />
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
        <div className={styles.failure}>
          <span className={styles['failure-icon']} aria-hidden="true">
            <IconInfoCircle size={16} stroke={1.8} />
          </span>
          <p className={styles.notice}>
            {sourceFailureMessage(
              source.failureMessage,
              '내용을 정리하지 못했지만 링크는 그대로 저장되어 있어요.',
            )}
          </p>
          {source.retryable && (
            <button
              type="button"
              className={styles.retry}
              disabled={isRetrying}
              aria-busy={isRetrying}
              onClick={() => void retry()}
            >
              {isRetrying
                ? <IconLoader2 className={styles.spinner} size={13} stroke={1.8} aria-hidden="true" />
                : <IconRefresh size={13} stroke={1.8} aria-hidden="true" />}
              {isRetrying ? '다시 분석하는 중' : '다시 분석하기'}
            </button>
          )}
          {retryError && <p className={styles['retry-error']} role="alert">{retryError}</p>}
        </div>
      ) : (
        source.summary && <p className={styles.summary}>{source.summary}</p>
      )}

      {isConfirming && (
        <DeleteConfirmation
          message="이 링크와 정리된 내용이 사라집니다. 연결된 개념은 다른 문서에 남습니다."
          ariaLabel="링크 삭제 확인"
          isDeleting={isDeleting}
          onCancel={() => setIsConfirming(false)}
          onConfirm={() => void remove()}
        />
      )}
      {deleteError && <p className={styles.error} role="alert">{deleteError}</p>}
      {readError && <p className={styles.error} role="alert">{readError}</p>}

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
