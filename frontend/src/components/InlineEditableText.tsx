import { useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { IconPencil } from '@tabler/icons-react'
import styles from './InlineEditableText.module.css'

interface InlineEditableTextProps {
  value: string
  ariaLabel: string
  emptyText?: string
  maxLength?: number
  requiredMessage: string
  className?: string
  /** 제목과 연필을 표시하는 영역에만 적용한다. 입력창·안내 문구에는 적용하지 않는다. */
  displayClassName?: string
  errorClassName?: string
  disabled?: boolean
  /** 보기 모드에서 제목을 두 줄까지 감싼다. 기본은 한 줄 말줄임. */
  wrap?: boolean
  /** 제목은 일반 텍스트로 표시하고 연필 버튼으로만 편집을 시작한다. */
  showEditButton?: boolean
  /** 편집을 시작했을 때 입력창 아래에 보여 줄 영향 범위 안내. */
  editingMessage?: string
  onSave: (value: string) => Promise<void>
  getErrorMessage?: (error: unknown) => string
}

export default function InlineEditableText({
  value,
  ariaLabel,
  emptyText = '',
  maxLength,
  requiredMessage,
  className,
  displayClassName,
  errorClassName,
  disabled = false,
  wrap = false,
  showEditButton = false,
  editingMessage,
  onSave,
  getErrorMessage,
}: InlineEditableTextProps) {
  const errorId = useId()
  const cancelRequestedRef = useRef(false)
  const [isEditing, setIsEditing] = useState(false)
  const [draft, setDraft] = useState(value)
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const startEditing = () => {
    if (disabled || isSaving) return
    cancelRequestedRef.current = false
    setDraft(value)
    setError(null)
    setIsEditing(true)
  }

  const cancelEditing = () => {
    if (isSaving) return
    cancelRequestedRef.current = true
    setDraft(value)
    setError(null)
    setIsEditing(false)
  }

  const save = async () => {
    if (cancelRequestedRef.current) {
      cancelRequestedRef.current = false
      return
    }
    if (isSaving) return

    const nextValue = draft.trim()
    if (!nextValue) {
      setError(requiredMessage)
      return
    }
    if (nextValue === value) {
      setIsEditing(false)
      setError(null)
      return
    }

    setIsSaving(true)
    setError(null)
    try {
      await onSave(nextValue)
      setIsEditing(false)
    } catch (saveError: unknown) {
      setError(getErrorMessage?.(saveError) ?? `${ariaLabel}을 저장하지 못했습니다.`)
    } finally {
      setIsSaving(false)
    }
  }

  const handleDisplayKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== 'Enter' && event.key !== 'F2') return
    event.preventDefault()
    startEditing()
  }

  const handleEditorKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      cancelEditing()
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      event.currentTarget.blur()
    }
  }

  const display = showEditButton ? (
    <span className={`${styles.text} ${wrap ? styles['display-wrap'] : ''}`}>
      {value || emptyText}
    </span>
  ) : (
    <button
      type="button"
      className={`${styles.display} ${wrap ? styles['display-wrap'] : ''}`}
      title={`더블 클릭하여 ${ariaLabel} 수정`}
      disabled={disabled}
      onDoubleClick={startEditing}
      onKeyDown={handleDisplayKeyDown}
    >
      {value || emptyText}
    </button>
  )

  return (
    <span className={`${styles.root} ${className ?? ''}`} aria-busy={isSaving}>
      {isEditing ? (
        <>
          <input
            className={styles.input}
            value={draft}
            maxLength={maxLength}
            aria-label={ariaLabel}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? errorId : undefined}
            disabled={isSaving}
            autoFocus
            onChange={(event) => {
              setDraft(event.target.value)
              setError(null)
            }}
            onBlur={() => void save()}
            onKeyDown={handleEditorKeyDown}
          />
          {editingMessage && (
            <span className={styles['editing-message']} role="status">{editingMessage}</span>
          )}
        </>
      ) : showEditButton ? (
        <span className={`${styles['display-row']} ${displayClassName ?? ''}`}>
          {display}
          <button
            type="button"
            className={styles['edit-button']}
            aria-label={`${ariaLabel} 수정`}
            disabled={disabled}
            onClick={startEditing}
          >
            <IconPencil size={12} stroke={1.8} aria-hidden="true" />
          </button>
        </span>
      ) : display}
      {error && (
        <span className={`${styles.error} ${errorClassName ?? ''}`}
              id={errorId}
              role="alert">{error}
        </span>
      )}
    </span>
  )
}
