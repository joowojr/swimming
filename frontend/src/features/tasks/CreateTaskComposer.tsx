import { useState } from 'react'
import type { FormEvent, RefObject } from 'react'
import { IconLoader2, IconPlus } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import { createTaskWithOptionalPlan } from './taskApi'
import styles from './CreateTaskComposer.module.css'

interface CreateTaskComposerProps {
  projectId: number
  inputRef: RefObject<HTMLInputElement | null>
  onCreated: () => void
  variant?: 'default' | 'embedded'
}

function validateTitle(value: string) {
  if (!value.trim()) return '할 일을 입력해 주세요.'
  if (value.trim().length > 255) return '할 일 제목은 255자 이하로 입력해 주세요.'
  return undefined
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null
}

export default function CreateTaskComposer({
  projectId,
  inputRef,
  onCreated,
  variant = 'default',
}: CreateTaskComposerProps) {
  const [title, setTitle] = useState('')
  const [isTouched, setIsTouched] = useState(false)
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const validationError = validateTitle(title)
    setIsTouched(true)
    setFieldError(validationError)
    setSubmitError(null)

    if (validationError) {
      inputRef.current?.focus()
      return
    }

    setIsSubmitting(true)
    let created = false
    try {
      await createTaskWithOptionalPlan({
        title: title.trim(),
        projectId,
      })
      setTitle('')
      setIsTouched(false)
      setFieldError(undefined)
      created = true
      onCreated()
    } catch (error: unknown) {
      if (isApiError(error) && error.errors?.title) {
        setFieldError(error.errors.title)
        inputRef.current?.focus()
      } else {
        setSubmitError(
          isApiError(error) && error.message
            ? error.message
            : '작업을 만들지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.',
        )
      }
    } finally {
      setIsSubmitting(false)
      if (created) {
        requestAnimationFrame(() => inputRef.current?.focus())
      }
    }
  }

  return (
    <form
      className={`${styles.composer} ${variant === 'embedded' ? styles.embedded : ''}`}
      aria-label="task 추가"
      aria-busy={isSubmitting}
      onSubmit={(event) => void handleSubmit(event)}
      noValidate
    >
      <div className={styles.row}>
        <label className="sr-only" htmlFor="task-title">task 제목</label>
        <input
          ref={inputRef}
          id="task-title"
          value={title}
          style={{border: 'none'}}
          maxLength={255}
          placeholder="할 일을 입력해 주세요."
          aria-required="true"
          aria-invalid={Boolean(fieldError)}
          aria-describedby={fieldError || submitError ? 'task-composer-message' : undefined}
          disabled={isSubmitting}
          onBlur={() => {
            if (!title) return
            setIsTouched(true)
            setFieldError(validateTitle(title))
          }}
          onChange={(event) => {
            const value = event.target.value
            setTitle(value)
            setSubmitError(null)
            if (isTouched) setFieldError(validateTitle(value))
          }}
        />
        <button className={styles.submit} type="submit" disabled={isSubmitting} aria-label="task 추가">
          <span className={styles['submit-visual']} aria-hidden="true">
            {isSubmitting
              ? <IconLoader2 className={styles.spinner} size={16} />
              : <IconPlus size={16} stroke={2} />}
          </span>
        </button>
      </div>
      {(fieldError || submitError) && (
        <p className={styles.message} id="task-composer-message" role="alert">
          {fieldError ?? submitError}
        </p>
      )}
    </form>
  )
}
