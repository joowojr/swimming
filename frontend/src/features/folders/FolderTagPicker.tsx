import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { IconCheck, IconPlus, IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import { useFolderTagStore } from '../../store/folderTagStore.ts'
import { setFolderTag } from './folderApi.ts'
import type { Folder, FolderTag, UpdateFolderTagRequest } from './folderTypes.ts'
import styles from './FolderTagPicker.module.css'

interface FolderTagPickerProps {
  folderId: number
  tag: FolderTag | null
  /** 폴더마다 정해진 색. 카드와 같은 규칙(folderId % 4)을 쓴다. */
  tone: number
  onChanged: (folder: Folder) => void
}


function toApiError(error: unknown) {
  return typeof error === 'object' && error !== null ? error as ApiError : undefined
}

function sameName(left: string, right: string) {
  return left.toLocaleLowerCase() === right.toLocaleLowerCase()
}

/**
 * 이 폴더의 태그를 달고, 바꾸고, 뗀다.
 *
 * 태그 이름 자체를 고치는 API는 그 태그를 쓰는 모든 폴더에 반영되므로 여기서는 쓰지 않는다.
 * 다른 이름을 입력하면 그 이름의 태그로 이 폴더만 옮긴다. 태그 이름 수정과 삭제는 태그 관리 모달이 맡는다.
 */
export default function FolderTagPicker({ folderId, tag, tone, onChanged }: FolderTagPickerProps) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const [isOpen, setIsOpen] = useState(false)
  const tags = useFolderTagStore((state) => state.tags)
  const tagsStatus = useFolderTagStore((state) => state.status)
  const ensureTagsLoaded = useFolderTagStore((state) => state.ensureLoaded)
  const upsertTag = useFolderTagStore((state) => state.upsert)
  const [query, setQuery] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const open = () => {
    setQuery('')
    setError(null)
    setIsOpen(true)
    void ensureTagsLoaded()
  }

  useEffect(() => {
    if (!isOpen) return

    const closeOnOutside = (event: Event) => {
      const node = event.target instanceof Node ? event.target : null
      if (node && !wrapRef.current?.contains(node)) setIsOpen(false)
    }
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false)
    }
    document.addEventListener('pointerdown', closeOnOutside)
    document.addEventListener('focusin', closeOnOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutside)
      document.removeEventListener('focusin', closeOnOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isOpen])

  const normalizedQuery = query.trim()
  const matchedTag = normalizedQuery
    ? tags.find((candidate) => sameName(candidate.name, normalizedQuery))
    : undefined
  const visibleTags = normalizedQuery
    ? tags.filter((candidate) =>
        candidate.name.toLocaleLowerCase().includes(normalizedQuery.toLocaleLowerCase()))
    : tags
  const canCreate = normalizedQuery.length > 0 && !matchedTag && tagsStatus !== 'idle' && tagsStatus !== 'loading'

  const save = async (request: UpdateFolderTagRequest) => {
    if (isSaving) return
    if (request.tagId !== null && request.tagId === tag?.id) {
      setIsOpen(false)
      return
    }

    setIsSaving(true)
    setError(null)
    try {
      const updated = await setFolderTag(folderId, request)
      // 새 이름으로 만든 태그도 다른 화면의 태그 목록에 바로 보이게 한다.
      if (updated.tag) upsertTag(updated.tag)
      onChanged(updated)
      setIsOpen(false)
    } catch (saveError: unknown) {
      const apiError = toApiError(saveError)
      setError(apiError?.errors?.newTagName ?? apiError?.message ?? '태그를 바꾸지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleInputKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing || !normalizedQuery) return
    event.preventDefault()
    if (matchedTag) void save({ tagId: matchedTag.id, newTagName: null })
    else if (canCreate) void save({ tagId: null, newTagName: normalizedQuery })
  }

  return (
    <div className={styles.wrap} ref={wrapRef}>
      <button
        type="button"
        className={tag ? styles.tag : styles['add-tag']}
        data-tone={tone}
        title={tag ? '태그 바꾸기' : '태그 달기'}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        onClick={() => (isOpen ? setIsOpen(false) : open())}
      >
        {tag
          ? (
              <>
                <span className="sr-only">태그 </span>
                {tag.name}
              </>
            )
          : (
              <>
                <IconPlus size={12} aria-hidden="true" />
                태그
              </>
            )}
      </button>

      {isOpen && (
        <div className={styles.panel} role="dialog" aria-label="폴더 태그 선택" aria-busy={isSaving}>
          <label className="sr-only" htmlFor={`folder-tag-query-${folderId}`}>태그 검색 또는 새 이름</label>
          <input
            id={`folder-tag-query-${folderId}`}
            className={styles.query}
            value={query}
            maxLength={30}
            placeholder="태그 검색 또는 새 이름"
            autoComplete="off"
            autoFocus
            disabled={isSaving}
            aria-invalid={Boolean(error)}
            onChange={(event) => { setQuery(event.target.value); setError(null) }}
            onKeyDown={handleInputKeyDown}
          />

          {tagsStatus === 'idle' || tagsStatus === 'loading' ? (
            <p className={styles.status} role="status">태그를 불러오는 중...</p>
          ) : tagsStatus === 'error' ? (
            <p className={styles.status}>태그 목록을 불러오지 못했습니다. 새 이름으로는 달 수 있습니다.</p>
          ) : visibleTags.length > 0 ? (
            <ul className={styles.options} aria-label="태그 목록">
              {visibleTags.map((candidate) => (
                <li key={candidate.id}>
                  <button
                    type="button"
                    className={styles.option}
                    aria-pressed={candidate.id === tag?.id}
                    disabled={isSaving}
                    onClick={() => void save({ tagId: candidate.id, newTagName: null })}
                  >
                    <span>{candidate.name}</span>
                    {candidate.id === tag?.id && <IconCheck size={14} aria-hidden="true" />}
                  </button>
                </li>
              ))}
            </ul>
          ) : !normalizedQuery ? (
            <p className={styles.status}>아직 만든 태그가 없습니다.</p>
          ) : null}

          {canCreate && (
            <button
              type="button"
              className={styles.option}
              disabled={isSaving}
              onClick={() => void save({ tagId: null, newTagName: normalizedQuery })}
            >
              <IconPlus size={14} aria-hidden="true" />
              <span>“{normalizedQuery}” 새 태그로 달기</span>
            </button>
          )}

          {tag && (
            <button
              type="button"
              className={styles.remove}
              disabled={isSaving}
              onClick={() => void save({ tagId: null, newTagName: null })}
            >
              <IconX size={14} aria-hidden="true" />
              <span>태그 삭제</span>
            </button>
          )}

          {error && <p className={styles.error} role="alert">{error}</p>}
        </div>
      )}
    </div>
  )
}
