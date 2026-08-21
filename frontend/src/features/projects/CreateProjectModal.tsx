import { useEffect, useRef, useState } from 'react'
import type { FormEvent, MouseEvent } from 'react'
import { IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import ActionButton from '../../components/ActionButton'
import { createProject, getProjectTags } from './projectApi'
import type {
  CreateProjectRequest,
  Project,
  ProjectTag,
} from './projectTypes'
import styles from './CreateProjectModal.module.css'

interface CreateProjectModalProps {
  onClose: () => void
  onCreated: (project: Project) => void
}

type TagsStatus = 'loading' | 'ready' | 'error'
type ProjectFormField = 'name' | 'description' | 'targetDate' | 'newTagName'
type FieldErrors = Partial<Record<ProjectFormField, string>>
type TouchedFields = Partial<Record<ProjectFormField, boolean>>

function validateName(value: string) {
  if (!value.trim()) return '프로젝트 이름을 입력해 주세요.'
  if (value.trim().length > 255) return '프로젝트 이름은 255자 이하로 입력해 주세요.'
  return undefined
}

function validateDescription(value: string) {
  if (!value.trim()) return '프로젝트 설명을 입력해 주세요.'
  return undefined
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function validateTargetDate(value: string, minimumDate: string) {
  if (value && value < minimumDate) return '목표일은 오늘 또는 이후 날짜로 선택해 주세요.'
  return undefined
}

function validateNewTagName(value: string) {
  if (value.trim().length > 30) return '태그 이름은 30자 이내로 입력해 주세요.'
  return undefined
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null
}
export default function CreateProjectModal({
  onClose,
  onCreated,
}: CreateProjectModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const nameInputRef = useRef<HTMLInputElement>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [targetDate, setTargetDate] = useState('')

  const [tagName, setTagName] = useState('')
  const [tags, setTags] = useState<ProjectTag[]>([])
  const [tagsStatus, setTagsStatus] = useState<TagsStatus>('loading')
  const [touchedFields, setTouchedFields] = useState<TouchedFields>({})
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [tagError, setTagError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const minimumTargetDate = formatLocalDate(new Date())

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return

    dialog.showModal()
    nameInputRef.current?.focus()

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [])

  useEffect(() => {
    let active = true

    void getProjectTags()
      .then((response) => {
        if (!active) return
        setTags(response)
        setTagsStatus('ready')
      })
      .catch(() => {
        if (!active) return
        setTagsStatus('error')
      })

    return () => {
      active = false
    }
  }, [])

  const requestClose = () => {
    if (!isSubmitting) dialogRef.current?.close()
  }

  const handleBackdropMouseDown = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const nextErrors: FieldErrors = {
      name: validateName(name),
      description: validateDescription(description),
      targetDate: validateTargetDate(targetDate, minimumTargetDate),
      newTagName: validateNewTagName(tagName),
    }

    const normalizedTagName = tagName.trim()
    const matchedTag = normalizedTagName
      ? tags.find(
          (tag) => tag.name.toLocaleLowerCase() === normalizedTagName.toLocaleLowerCase(),
        )
      : undefined

    setTouchedFields({
      name: true,
      description: true,
      targetDate: true,
      newTagName: true,
    })
    setFieldErrors(nextErrors)
    setSubmitError(null)
    setTagError(null)
    if (Object.values(nextErrors).some(Boolean)) {
      if (nextErrors.name) nameInputRef.current?.focus()
      return
    }

    const request: CreateProjectRequest = {
      name: name.trim(),
      description: description.trim(),
      targetDate: targetDate || null,
      tagId: matchedTag?.id ?? null,
      newTagName: normalizedTagName && !matchedTag ? normalizedTagName : null,
    }

    setIsSubmitting(true)
    try {
      onCreated(await createProject(request))
    } catch (error) {
      if (isApiError(error) && error.errors) {
        setFieldErrors({
          name: error.errors.name,
          description: error.errors.description,
          targetDate: error.errors.targetDate,
          newTagName: error.errors.newTagName,
        })
      }
      if (isApiError(error) && error.code === 'PROJECT_TAG_ALREADY_EXISTS') {
        setTagError(error.message ?? '같은 이름의 태그가 이미 있습니다.')
      } else {
        setSubmitError(
          isApiError(error) && error.message
            ? error.message
            : '프로젝트를 만들지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.',
        )
      }
      setIsSubmitting(false)
    }
  }

  const normalizedTagName = tagName.trim()
  const matchedTag = normalizedTagName
    ? tags.find(
        (tag) => tag.name.toLocaleLowerCase() === normalizedTagName.toLocaleLowerCase(),
      )
    : undefined
  const visibleTags = matchedTag
    ? tags
    : normalizedTagName
      ? tags.filter((tag) =>
          tag.name.toLocaleLowerCase().includes(normalizedTagName.toLocaleLowerCase()),
        )
      : tags

  return (
    <dialog
      ref={dialogRef}
      className={styles['create-project-dialog']}
      aria-labelledby="create-project-title"
      aria-describedby="create-project-description"
      aria-busy={isSubmitting}
      onCancel={(event) => {
        if (isSubmitting) event.preventDefault()
      }}
      onClose={onClose}
      onMouseDown={handleBackdropMouseDown}
    >
      <section className={styles['create-project-modal']}>
        <header className={styles['modal-header']}>
          <div>
            <h2 id="create-project-title">새 프로젝트</h2>
            <p id="create-project-description">새 프로젝트 정보를 입력해 주세요.</p>
          </div>
          <button
            type="button"
            className={styles['modal-close']}
            aria-label="새 프로젝트 창 닫기"
            onClick={requestClose}
            disabled={isSubmitting}
          >
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <div className={styles['modal-body']}>
            <div className={styles['modal-field']}>
              <label htmlFor="project-name">프로젝트 이름</label>
              <input
                ref={nameInputRef}
                id="project-name"
                value={name}
                maxLength={255}
                placeholder="예: 포트폴리오 리뉴얼"
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.name)}
                aria-describedby={fieldErrors.name ? 'project-name-error' : undefined}
                onBlur={() => {
                  setTouchedFields((fields) => ({ ...fields, name: true }))
                  setFieldErrors((errors) => ({ ...errors, name: validateName(name) }))
                }}
                onChange={(event) => {
                  const value = event.target.value
                  setName(value)
                  if (touchedFields.name) {
                    setFieldErrors((errors) => ({ ...errors, name: validateName(value) }))
                  }
                }}
                disabled={isSubmitting}
              />
              <p className={styles['modal-field-message']} id="project-name-error" aria-live="polite">
                {fieldErrors.name ?? '\u00a0'}
              </p>
            </div>

            <div className={styles['modal-field']}>
              <label htmlFor="project-description-input">설명</label>
              <textarea
                id="project-description-input"
                value={description}
                rows={3}
                placeholder="예: 프로젝트에서 이루고 싶은 목표를 적어주세요."
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.description)}
                aria-describedby={fieldErrors.description ? 'project-description-error' : undefined}
                onBlur={() => {
                  setTouchedFields((fields) => ({ ...fields, description: true }))
                  setFieldErrors((errors) => ({ ...errors, description: validateDescription(description) }))
                }}
                onChange={(event) => {
                  const value = event.target.value
                  setDescription(value)
                  if (touchedFields.description) {
                    setFieldErrors((errors) => ({ ...errors, description: validateDescription(value) }))
                  }
                }}
                disabled={isSubmitting}
              />
              <p className={styles['modal-field-message']} id="project-description-error" aria-live="polite">
                {fieldErrors.description ?? '\u00a0'}
              </p>
            </div>

            <div className={styles['modal-field']}>
              <label htmlFor="project-target-date">목표일 <span>선택</span></label>
              <input
                id="project-target-date"
                type="date"
                value={targetDate}
                min={minimumTargetDate}
                aria-invalid={Boolean(fieldErrors.targetDate)}
                aria-describedby={
                  fieldErrors.targetDate ? 'project-target-date-error' : undefined
                }
                onBlur={() => {
                  setTouchedFields((fields) => ({ ...fields, targetDate: true }))
                  setFieldErrors((errors) => ({
                    ...errors,
                    targetDate: validateTargetDate(targetDate, minimumTargetDate),
                  }))
                }}
                onChange={(event) => {
                  const value = event.target.value
                  setTargetDate(value)
                  if (touchedFields.targetDate) {
                    setFieldErrors((errors) => ({
                      ...errors,
                      targetDate: validateTargetDate(value, minimumTargetDate),
                    }))
                  }
                }}
                disabled={isSubmitting}
              />
              <p
                className={styles['modal-field-message']}
                id="project-target-date-error"
                aria-live="polite"
              >
                {fieldErrors.targetDate ?? '\u00a0'}
              </p>
            </div>

            <fieldset className={styles['modal-field']}>
              <legend>태그 <span>선택</span></legend>
              <div className={styles['tag-input-wrap']}>
                {!matchedTag && (
                  <>
                    <label className="sr-only" htmlFor="project-tag">태그 이름</label>
                    <input
                      id="project-tag"
                      value={tagName}
                      maxLength={30}
                      placeholder="태그를 선택하거나 새 이름을 입력하세요"
                      autoComplete="off"
                      aria-invalid={Boolean(fieldErrors.newTagName || tagError)}
                      aria-describedby="project-tag-error"
                      onBlur={() => {
                        setTouchedFields((fields) => ({ ...fields, newTagName: true }))
                        setFieldErrors((errors) => ({
                          ...errors,
                          newTagName: validateNewTagName(tagName),
                        }))
                      }}
                      onChange={(event) => {
                        const value = event.target.value
                        setTagName(value)
                        setTagError(null)
                        if (touchedFields.newTagName) {
                          setFieldErrors((errors) => ({
                            ...errors,
                            newTagName: validateNewTagName(value),
                          }))
                        }
                      }}
                      disabled={isSubmitting}
                    />
                  </>
                )}

                {tagsStatus === 'loading' ? (
                  <p className={styles['tag-status']} role="status">태그를 불러오는 중...</p>
                ) : tagsStatus === 'error' ? (
                  <p className={styles['tag-status']}>
                    기존 태그를 확인하지 못했습니다. 입력한 이름으로 생성을 시도합니다.
                  </p>
                ) : visibleTags.length > 0 ? (
                  <div className={styles['tag-options']} aria-label="태그 제안">
                    {visibleTags.map((tag) => (
                      <button
                        type="button"
                        key={tag.id}
                        aria-pressed={matchedTag?.id === tag.id}
                        onMouseDown={(event) => event.preventDefault()}
                        onClick={() => {
                          setTagName(matchedTag?.id === tag.id ? '' : tag.name)
                          setFieldErrors((errors) => ({
                            ...errors,
                            newTagName: undefined,
                          }))
                          setTagError(null)
                        }}
                        disabled={isSubmitting}
                      >
                        {tag.name}
                      </button>
                    ))}
                  </div>
                ) : null}

                <p
                  className={styles['modal-field-message']}
                  id="project-tag-error"
                  aria-live="polite"
                >
                  {fieldErrors.newTagName ?? tagError ?? '\u00a0'}
                </p>
              </div>
            </fieldset>

            {submitError && (
              <p className={styles['modal-submit-error']} role="alert">{submitError}</p>
            )}
          </div>

          <footer className={styles['modal-footer']}>
            <button type="button" className={styles['modal-cancel']} onClick={requestClose} disabled={isSubmitting}>
              취소
            </button>
            <ActionButton
              type="submit"
              className={styles['modal-submit']}
              isLoading={isSubmitting}
              loadingLabel="만드는 중…"
            >
              만들기
            </ActionButton>
          </footer>
        </form>
      </section>
    </dialog>
  )
}
