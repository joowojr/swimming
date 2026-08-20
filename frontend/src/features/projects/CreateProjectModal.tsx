import { useEffect, useRef, useState } from 'react'
import type { FormEvent, MouseEvent } from 'react'
import { IconLoader2, IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
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
type FieldErrors = Partial<Record<'name' | 'description' | 'newTagName', string>>
type TouchedFields = Partial<Record<'name' | 'description' | 'newTagName', boolean>>

function validateName(value: string) {
  if (!value.trim()) return '프로젝트 이름을 입력해 주세요.'
  if (value.trim().length > 255) return '프로젝트 이름은 255자 이하로 입력해 주세요.'
  return undefined
}

function validateDescription(value: string) {
  if (!value.trim()) return '프로젝트 설명을 입력해 주세요.'
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

  const [newTagName, setNewTagName] = useState('')
  const [selectedTagId, setSelectedTagId] = useState<number | null>(null)
  const [tags, setTags] = useState<ProjectTag[]>([])
  const [tagsStatus, setTagsStatus] = useState<TagsStatus>('loading')
  const [touchedFields, setTouchedFields] = useState<TouchedFields>({})
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [tagError, setTagError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

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
      newTagName: validateNewTagName(newTagName),
    }

    const normalizedNewTagName = newTagName.trim()
    const duplicateTag = normalizedNewTagName
      ? tags.find(
          (tag) => tag.name.toLocaleLowerCase() === normalizedNewTagName.toLocaleLowerCase(),
        )
      : undefined

    if (duplicateTag) {
      nextErrors.newTagName = '이미 있는 태그입니다. 위 목록에서 선택해 주세요.'
    }

    setTouchedFields({ name: true, description: true, newTagName: true })
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
      tagId: selectedTagId,
      newTagName: normalizedNewTagName || null,
    }

    setIsSubmitting(true)
    try {
      onCreated(await createProject(request))
    } catch (error) {
      if (isApiError(error) && error.errors) {
        setFieldErrors({
          name: error.errors.name,
          description: error.errors.description,
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
            <p id="create-project-description">프로젝트의 기본 정보를 입력하세요.</p>
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
                placeholder="예: 개인 작업을 정리해 새 포트폴리오로 완성합니다."
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
                onChange={(event) => setTargetDate(event.target.value)}
                disabled={isSubmitting}
              />
            </div>

            <fieldset className={styles['modal-field']}>
              <legend>태그 선택 <span>선택</span></legend>
              {tagsStatus === 'loading' ? (
                <p className={styles['tag-status']} role="status">태그를 불러오고 있습니다.</p>
              ) : tagsStatus === 'error' ? (
                <p className={styles['tag-status']}>기존 태그 목록을 불러오지 못했습니다.</p>
              ) : tags.length > 0 ? (
                <div className={styles['tag-options']}>
                  <button
                    type="button"
                    aria-pressed={selectedTagId === null}
                    onClick={() => {
                      setSelectedTagId(null)
                      setNewTagName('')
                      setFieldErrors((errors) => ({ ...errors, newTagName: undefined }))
                      setTagError(null)
                    }}
                    disabled={isSubmitting}
                  >
                    선택 안 함
                  </button>
                  {tags.map((tag) => (
                    <button
                      type="button"
                      key={tag.id}
                      aria-pressed={selectedTagId === tag.id}
                      onClick={() => {
                        setSelectedTagId(tag.id)
                        setNewTagName('')
                        setFieldErrors((errors) => ({ ...errors, newTagName: undefined }))
                        setTagError(null)
                      }}
                      disabled={isSubmitting}
                    >
                      {tag.name}
                    </button>
                  ))}
                </div>
              ) : (
                <p className={styles['tag-status']}>만들어진 태그가 아직 없습니다.</p>
              )}

              <div className={styles['new-tag-field']}>
                <label htmlFor="project-new-tag">새 태그 직접 입력</label>
                <input
                  id="project-new-tag"
                  value={newTagName}
                  maxLength={30}
                  placeholder="예: 포트폴리오"
                  aria-invalid={Boolean(fieldErrors.newTagName || tagError)}
                  aria-describedby="project-new-tag-hint project-new-tag-error"
                  onBlur={() => {
                    setTouchedFields((fields) => ({ ...fields, newTagName: true }))
                    setFieldErrors((errors) => ({
                      ...errors,
                      newTagName: validateNewTagName(newTagName),
                    }))
                  }}
                  onChange={(event) => {
                    const value = event.target.value
                    setNewTagName(value)
                    setTagError(null)
                    if (value.trim()) setSelectedTagId(null)
                    if (touchedFields.newTagName) {
                      setFieldErrors((errors) => ({
                        ...errors,
                        newTagName: validateNewTagName(value),
                      }))
                    }
                  }}
                  disabled={isSubmitting}
                />
                <p className={styles['new-tag-hint']} id="project-new-tag-hint">
                  입력한 태그는 프로젝트를 만들 때 함께 생성됩니다.
                </p>
                <p
                  className={styles['modal-field-message']}
                  id="project-new-tag-error"
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
            <button type="submit" className={styles['modal-submit']} disabled={isSubmitting}>
              {isSubmitting && <IconLoader2 className={styles['modal-submit-spinner']} size={17} aria-hidden="true" />}
              {isSubmitting ? '만드는 중…' : '프로젝트 만들기'}
            </button>
          </footer>
        </form>
      </section>
    </dialog>
  )
}
