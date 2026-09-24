import { useId, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import type { ApiError } from '../../api/client'
import ActionButton from '../../components/ActionButton'
import styles from './FolderInfoForm.module.css'

interface FolderInfoFormProps {
  name: string
  description: string
  onSave: (name: string, description: string) => Promise<void>
  onCancel: () => void
}

type FolderInfoField = 'name' | 'description'
type FieldErrors = Partial<Record<FolderInfoField, string>>

function validate(name: string, description: string): FieldErrors {
  return {
    name: name ? undefined : '폴더 이름을 입력해 주세요.',
    description: description ? undefined : '폴더 설명을 입력해 주세요.',
  }
}

function toApiError(error: unknown) {
  return typeof error === 'object' && error !== null ? error as ApiError : undefined
}

/**
 * 폴더 제목과 설명을 한 번에 고치는 폼. 둘은 폴더 수정 API 한 번으로 함께 저장한다.
 *
 * Esc는 취소, 제목에서 Enter나 설명에서 Ctrl/⌘+Enter는 저장이다.
 */
export default function FolderInfoForm({ name, description, onSave, onCancel }: FolderInfoFormProps) {
  const nameId = useId()
  const descriptionId = useId()
  const nameInputRef = useRef<HTMLInputElement>(null)
  const descriptionInputRef = useRef<HTMLTextAreaElement>(null)
  const [draftName, setDraftName] = useState(name)
  const [draftDescription, setDraftDescription] = useState(description)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const focusFirstError = (errors: FieldErrors) => {
    if (errors.name) nameInputRef.current?.focus()
    else if (errors.description) descriptionInputRef.current?.focus()
  }

  const submit = async (event?: FormEvent) => {
    event?.preventDefault()
    if (isSaving) return

    const nextName = draftName.trim()
    const nextDescription = draftDescription.trim()
    const errors = validate(nextName, nextDescription)
    setFieldErrors(errors)
    setSubmitError(null)
    if (errors.name || errors.description) {
      focusFirstError(errors)
      return
    }
    if (nextName === name && nextDescription === description) {
      onCancel()
      return
    }

    setIsSaving(true)
    try {
      await onSave(nextName, nextDescription)
    } catch (error: unknown) {
      const apiError = toApiError(error)
      const apiErrors = { name: apiError?.errors?.name, description: apiError?.errors?.description }
      setFieldErrors(apiErrors)
      if (apiErrors.name || apiErrors.description) focusFirstError(apiErrors)
      else setSubmitError(apiError?.message ?? '폴더 정보를 저장하지 못했습니다. 다시 시도해 주세요.')
      setIsSaving(false)
    }
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLFormElement>) => {
    if (event.key === 'Escape' && !isSaving) {
      event.preventDefault()
      onCancel()
      return
    }
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey) && !event.nativeEvent.isComposing) {
      event.preventDefault()
      void submit()
    }
  }

  return (
    <form
      className={styles.form}
      aria-label="폴더 정보 수정"
      aria-busy={isSaving}
      noValidate
      onSubmit={(event) => void submit(event)}
      onKeyDown={handleKeyDown}
    >
      <label className="sr-only" htmlFor={nameId}>폴더 제목</label>
      <input
        ref={nameInputRef}
        id={nameId}
        className={styles.name}
        value={draftName}
        maxLength={255}
        autoFocus
        disabled={isSaving}
        aria-invalid={Boolean(fieldErrors.name)}
        aria-describedby={fieldErrors.name ? `${nameId}-error` : undefined}
        onChange={(event) => {
          setDraftName(event.target.value)
          setFieldErrors((errors) => ({ ...errors, name: undefined }))
        }}
      />
      {fieldErrors.name && <p className={styles.error} id={`${nameId}-error`}>{fieldErrors.name}</p>}

      <label className="sr-only" htmlFor={descriptionId}>폴더 설명</label>
      <textarea
        ref={descriptionInputRef}
        id={descriptionId}
        className={styles.description}
        value={draftDescription}
        rows={3}
        placeholder="폴더에서 다루고 싶은 주제를 적어 주세요."
        disabled={isSaving}
        aria-invalid={Boolean(fieldErrors.description)}
        aria-describedby={fieldErrors.description ? `${descriptionId}-error` : undefined}
        onChange={(event) => {
          setDraftDescription(event.target.value)
          setFieldErrors((errors) => ({ ...errors, description: undefined }))
        }}
      />
      {fieldErrors.description && (
        <p className={styles.error} id={`${descriptionId}-error`}>{fieldErrors.description}</p>
      )}

      {submitError && <p className={styles.error} role="alert">{submitError}</p>}

      <div className={styles.actions}>
        <ActionButton variant="plain" disabled={isSaving} onClick={onCancel}>취소</ActionButton>
        <ActionButton type="submit" isLoading={isSaving} loadingLabel="저장 중…">저장</ActionButton>
      </div>
    </form>
  )
}
