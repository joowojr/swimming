import ActionButton from './ActionButton'
import styles from './DeleteConfirmation.module.css'

interface DeleteConfirmationProps {
  message: string
  isDeleting?: boolean
  ariaLabel?: string
  onCancel: () => void
  onConfirm: () => void
}

export default function DeleteConfirmation({
  message,
  isDeleting = false,
  ariaLabel = '삭제 확인',
  onCancel,
  onConfirm,
}: DeleteConfirmationProps) {
  return (
    <div className={styles.root} role="group" aria-label={ariaLabel}>
      <span>{message}</span>
      <div>
        <ActionButton variant="plain" onClick={onCancel} disabled={isDeleting}>취소</ActionButton>
        <ActionButton className={styles.confirm} isLoading={isDeleting} loadingLabel="삭제 중…" onClick={onConfirm}>삭제</ActionButton>
      </div>
    </div>
  )
}
