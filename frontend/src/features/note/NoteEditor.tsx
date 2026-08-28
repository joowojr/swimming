import type { ChangeEvent, RefObject } from 'react'
import { IconArchive, IconPlus, IconSparkles } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import DeleteIconButton from '../../components/DeleteIconButton'
import type { LoadStatus, SaveStatus } from './noteViewTypes'
import styles from './NoteCard.module.css'
import { useNoteEditorStore } from './noteEditorStore'

/** 역할: 노트 입력, 자동 저장 상태, 노트 단위 액션을 표시한다. 저장과 삭제의 실제 처리는 NoteCard가 소유한다. */
interface NoteEditorProps {
  memo: string
  loadStatus: LoadStatus
  saveStatus: SaveStatus
  selectedNoteId: number | null
  isArchived: boolean
  disabled: boolean
  isStartingNew: boolean
  isArchiving: boolean
  isDeleting: boolean
  textareaRef: RefObject<HTMLTextAreaElement | null>
  onMemoChange: (event: ChangeEvent<HTMLTextAreaElement>) => void
  onMemoBlur: () => void
  onOrganize: () => void
  onNewMemo: () => void
  onArchive: () => void
  onConfirmArchive: () => void
  onDismissArchive: () => void
  onRequestDelete: () => void
  onCancelDelete: () => void
  onDelete: () => void
  onRestore: () => void
}

function getStatusMessage(
  memo: string,
  loadStatus: LoadStatus,
  saveStatus: SaveStatus,
  actionMessage: string | null,
) {
  if (loadStatus === 'loading') return '노트를 불러오는 중…'
  if (loadStatus === 'error') return '노트를 불러오지 못했어요'
  if (actionMessage) return actionMessage
  if (!memo.trim()) return '입력하면 자동으로 저장돼요'
  if (saveStatus === 'saving') return '저장 중…'
  if (saveStatus === 'saved') return '저장됨'
  if (saveStatus === 'error') return '저장하지 못했어요. 다시 입력하면 재시도해요'
  return '입력을 멈추면 자동으로 저장돼요'
}

export default function NoteEditor({
  memo,
  loadStatus,
  saveStatus,
  selectedNoteId,
  isArchived,
  disabled,
  isStartingNew,
  isArchiving,
  isDeleting,
  textareaRef,
  onMemoChange,
  onMemoBlur,
  onOrganize,
  onNewMemo,
  onArchive,
  onConfirmArchive,
  onDismissArchive,
  onRequestDelete,
  onCancelDelete,
  onDelete,
  onRestore,
}: NoteEditorProps) {
  const { actionMessage, isConfirmingDelete, recentlyArchivedId, archiveSuggestionNoteId } = useNoteEditorStore()
  const recentlyArchived = recentlyArchivedId !== null
  const archiveSuggested = archiveSuggestionNoteId !== null
  const statusMessage = getStatusMessage(memo, loadStatus, saveStatus, actionMessage)

  return (
    <>
      <div className={styles['memo-head']}>
        <h3 id="memo-title" className={styles['memo-title']}>노트</h3>
        <div className={styles['memo-head-actions']}>
          <button type="button" className={styles['memo-icon-action']} onClick={onArchive}
            disabled={selectedNoteId === null || disabled || isArchived} aria-label="현재 노트 보관" title="노트 보관">
            <IconArchive size={16} aria-hidden="true" />
          </button>
          <DeleteIconButton
            className={styles['memo-borderless']}
            label="현재 노트 삭제"
            disabled={selectedNoteId === null || disabled}
            aria-expanded={isConfirmingDelete}
            title="노트 삭제"
            onClick={onRequestDelete}
          />
          <ActionButton className={styles['new-memo-action']} icon={<IconPlus size={16} aria-hidden="true" />}
            variant="outline" onClick={onNewMemo} disabled={loadStatus !== 'ready' || disabled || isStartingNew}>
            새 노트
          </ActionButton>
        </div>
      </div>

      {isConfirmingDelete && (
        <div className={styles['delete-confirmation']} role="group" aria-label="노트 삭제 확인">
          <span>이 노트를 삭제할까요?</span>
          <div>
            <ActionButton variant="plain" onClick={onCancelDelete} disabled={isDeleting}>취소</ActionButton>
            <ActionButton isLoading={isDeleting} loadingLabel="삭제 중…" onClick={onDelete}>삭제</ActionButton>
          </div>
        </div>
      )}

      {archiveSuggested && !recentlyArchived && (
        <div className={`${styles['delete-confirmation']} ${styles['archive-confirmation']}`} role="status">
          <span>노트를 보관해드릴까요?</span>
          <div>
            <ActionButton className={styles['archive-keep-action']} variant="plain" onClick={onDismissArchive}>유지</ActionButton>
            <ActionButton variant="plain" onClick={onConfirmArchive} disabled={isArchiving}>보관</ActionButton>
          </div>
        </div>
      )}

      {recentlyArchived && (
        <div className={`${styles['delete-confirmation']} ${styles['archive-confirmation']}`} role="status">
          <span>노트를 보관했어요</span>
          <div>
            <ActionButton variant="plain" onClick={onRestore} disabled={isArchiving}>실행 취소</ActionButton>
          </div>
        </div>
      )}

      <textarea ref={textareaRef} className={styles['memo-paper']} placeholder="떠오르는 일을 편하게 적어두세요."
        value={memo} onChange={onMemoChange} onBlur={onMemoBlur} disabled={disabled} />

      <div className={styles['memo-foot']}>
        <span className={styles['memo-hint']} role="status" aria-live="polite">{statusMessage}</span>
        <ActionButton className={styles['organize-action']} icon={<IconSparkles size={16} aria-hidden="true" />}
          onClick={onOrganize} disabled={!memo.trim() || disabled}>
          할 일로 정리
        </ActionButton>
      </div>

    </>
  )
}
