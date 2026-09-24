import { useEffect, useRef, useState } from 'react'
import type { FormEvent, MouseEvent } from 'react'
import { IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import ActionButton from '../../components/ActionButton'
import { useFolderTagStore } from '../../store/folderTagStore.ts'
import { createFolder } from './folderApi.ts'
import type {
  CreateFolderRequest,
  Folder,
} from './folderTypes.ts'
import styles from './CreateFolder.module.css'
import modalStyles from '../../components/ModalShell.module.css'
import { formatLocalDate } from '../../lib/date'

interface CreateProjectModalProps {
  onClose: () => void
  onCreated: (folder: Folder) => void
}

type ProjectFormField = 'name' | 'description' | 'targetDate' | 'newTagName'
type FieldErrors = Partial<Record<ProjectFormField, string>>
type TouchedFields = Partial<Record<ProjectFormField, boolean>>

function validateName(value: string) {
  if (!value.trim()) return '폴더 이름을 입력해 주세요.'
  if (value.trim().length > 255) return '폴더 이름은 255자 이하로 입력해 주세요.'
  return undefined
}

function validateDescription(value: string) {
  if (!value.trim()) return '폴더 설명을 입력해 주세요.'
  return undefined
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
export default function CreateFolderModal({
  onClose,
  onCreated,
}: CreateProjectModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const nameInputRef = useRef<HTMLInputElement>(null)
  const descriptionInputRef = useRef<HTMLTextAreaElement>(null)
  const targetDateInputRef = useRef<HTMLInputElement>(null)
  const tagInputRef = useRef<HTMLInputElement>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [targetDate, setTargetDate] = useState('')

  const [tagName, setTagName] = useState('')
  const tags = useFolderTagStore((state) => state.tags)
  const tagsStatus = useFolderTagStore((state) => state.status)
  const ensureTagsLoaded = useFolderTagStore((state) => state.ensureLoaded)
  const upsertTag = useFolderTagStore((state) => state.upsert)
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

  useEffect(() => { void ensureTagsLoaded() }, [ensureTagsLoaded])

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
      else if (nextErrors.description) descriptionInputRef.current?.focus()
      else if (nextErrors.targetDate) targetDateInputRef.current?.focus()
      else if (nextErrors.newTagName) tagInputRef.current?.focus()
      return
    }

    const request: CreateFolderRequest = {
      name: name.trim(),
      description: description.trim(),
      targetDate: targetDate || null,
      tagId: matchedTag?.id ?? null,
      newTagName: normalizedTagName && !matchedTag ? normalizedTagName : null,
    }

    setIsSubmitting(true)
    try {
      const created = await createFolder(request)
      // 새 이름으로 만든 태그도 다른 화면의 태그 목록에 바로 보이게 한다.
      if (created.tag) upsertTag(created.tag)
      onCreated(created)
    } catch (error) {
      if (isApiError(error) && error.errors) {
        const apiErrors = {
          name: error.errors.name,
          description: error.errors.description,
          targetDate: error.errors.targetDate,
          newTagName: error.errors.newTagName,
        }
        setFieldErrors(apiErrors)
        if (apiErrors.name) nameInputRef.current?.focus()
        else if (apiErrors.description) descriptionInputRef.current?.focus()
        else if (apiErrors.targetDate) targetDateInputRef.current?.focus()
        else if (apiErrors.newTagName) tagInputRef.current?.focus()
      }
      if (isApiError(error) && error.code === 'FOLDER_TAG_ALREADY_EXISTS') {
        setTagError(error.message ?? '같은 이름의 태그가 이미 있습니다.')
        tagInputRef.current?.focus()
      } else {
        setSubmitError(
          isApiError(error) && error.message
            ? error.message
            : '폴더를 만들지 못했습니다. 입력 내용을 확인한 뒤 다시 시도해 주세요.',
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
      id="create-folder-dialog"
      ref={dialogRef}
      className={`${styles['create-folder-dialog']} ${modalStyles.dialog}`}
      aria-labelledby="create-folder-title"
      aria-describedby="create-folder-description"
      aria-busy={isSubmitting}
      onCancel={(event) => {
        if (isSubmitting) event.preventDefault()
      }}
      onClose={onClose}
      onMouseDown={handleBackdropMouseDown}
    >
      <section className={`${styles['create-folder-modal']} ${modalStyles.surface}`}>
        <header className={`${styles['modal-header']} ${modalStyles.header}`}>
          <div>
            <h2 id="create-folder-title">새 폴더</h2>
            <p id="create-folder-description">새 폴더 정보를 입력해 주세요.</p>
          </div>
          <button
            type="button"
            className={styles['modal-close']}
            aria-label="새 폴더 창 닫기"
            onClick={requestClose}
            disabled={isSubmitting}
          >
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <div className={styles['modal-body']}>
            <div className={styles['modal-field']}>
              <label htmlFor="folder-name">폴더 이름</label>
              <input
                ref={nameInputRef}
                id="folder-name"
                value={name}
                maxLength={255}
                placeholder="예: 포트폴리오 리뉴얼"
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.name)}
                aria-describedby={fieldErrors.name ? 'folder-name-error' : undefined}
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
              {fieldErrors.name && (
                <p className={styles['modal-field-message']} id="folder-name-error" aria-live="polite">
                  {fieldErrors.name}
                </p>
              )}
            </div>

            <div className={styles['modal-field']}>
              <label htmlFor="folder-description-input">설명</label>
              <textarea
                ref={descriptionInputRef}
                id="folder-description-input"
                value={description}
                rows={3}
                placeholder="예: 폴더에서 다루고 싶은 주제를 적어주세요."
                aria-required="true"
                aria-invalid={Boolean(fieldErrors.description)}
                aria-describedby={fieldErrors.description ? 'folder-description-error' : undefined}
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
              {fieldErrors.description && (
                <p className={styles['modal-field-message']} id="folder-description-error" aria-live="polite">
                  {fieldErrors.description}
                </p>
              )}
            </div>

            <div className={styles['modal-field']}>
              <label htmlFor="folder-target-date">목표일 <span>선택</span></label>
              <input
                ref={targetDateInputRef}
                id="folder-target-date"
                type="date"
                value={targetDate}
                min={minimumTargetDate}
                aria-invalid={Boolean(fieldErrors.targetDate)}
                aria-describedby={
                  fieldErrors.targetDate ? 'folder-target-date-error' : undefined
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
              {fieldErrors.targetDate && (
                <p
                  className={styles['modal-field-message']}
                  id="folder-target-date-error"
                  aria-live="polite"
                >
                  {fieldErrors.targetDate}
                </p>
              )}
            </div>

            <fieldset className={styles['modal-field']}>
              <legend>태그 <span>선택</span></legend>
              <div className={styles['tag-input-wrap']}>
                <label className="sr-only" htmlFor="folder-tag">태그 이름</label>
                <input
                  ref={tagInputRef}
                  id="folder-tag"
                  value={tagName}
                  maxLength={30}
                  placeholder="태그를 선택하거나 새 이름을 입력하세요"
                  autoComplete="off"
                  aria-invalid={Boolean(fieldErrors.newTagName || tagError)}
                  aria-describedby={fieldErrors.newTagName || tagError ? 'folder-tag-error' : undefined}
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

                {matchedTag && (
                  <p className={styles['tag-selection-hint']} role="status">
                    기존 태그 <strong>{matchedTag.name}</strong>를 연결합니다. 다른 이름을 입력하면 새 태그로 바뀝니다.
                  </p>
                )}

                {tagsStatus === 'idle' || tagsStatus === 'loading' ? (
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
                ) : normalizedTagName ? (
                  <p className={styles['tag-status']} role="status">
                    입력한 이름으로 새 태그를 만듭니다.
                  </p>
                ) : null}

                {(fieldErrors.newTagName || tagError) && (
                  <p
                    className={styles['modal-field-message']}
                    id="folder-tag-error"
                    aria-live="polite"
                  >
                    {fieldErrors.newTagName ?? tagError}
                  </p>
                )}
              </div>
            </fieldset>

            {submitError && (
              <p className={styles['modal-submit-error']} role="alert">{submitError}</p>
            )}
          </div>

          <footer className={styles['modal-footer']}>
            <ActionButton
              type="submit"
              className={styles['modal-submit']}
              isLoading={isSubmitting}
              loadingLabel="만드는 중…"
            >
              폴더 만들기
            </ActionButton>
          </footer>
        </form>
      </section>
    </dialog>
  )
}
