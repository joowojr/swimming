import type { KeyboardEvent } from 'react'
import { useState } from 'react'
import { IconCalendarDue } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import DdayChip from '../../components/DdayChip'
import DeleteConfirmation from '../../components/DeleteConfirmation'
import DeleteIconButton from '../../components/DeleteIconButton'
import InlineEditableText from '../../components/InlineEditableText'
import { useFolderStore } from '../../store/folderStore.ts'
import { deleteFolder, updateFolder } from './folderApi.ts'
import type { Folder, FolderStatus, FolderTag } from './folderTypes.ts'
import styles from './FolderHeader.module.css'

/** 폴더 화면들이 공통으로 쓰는 만큼만 받는다. Folder와 FolderDetail 둘 다 이 모양을 만족한다. */
export interface FolderHeaderFolder {
  id: number
  name: string
  description: string
  targetDate: string | null
  status: FolderStatus
  tag: FolderTag | null
}

interface FolderHeaderProps {
  folder: FolderHeaderFolder
  titleId: string
  /** 폴더 삭제 시 무엇이 함께 사라지는지는 화면마다 다르다. */
  deleteMessage: string
  /** 링크 폴더는 진행 상태를 다루지 않아 배지를 그리지 않는다. */
  showStatus?: boolean
  onUpdated?: (folder: Folder) => void
  onDeleted: (folderId: number) => void
}

type EditableFolderTextField = 'name' | 'description'

const folderStatusLabel: Record<FolderStatus, string> = {
  IN_PROGRESS: '진행 중',
  ARCHIVED: '보관됨',
}

const targetDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
})

function formatTargetDate(targetDate: string | null) {
  return targetDate
    ? targetDateFormatter.format(new Date(`${targetDate}T00:00:00`))
    : '설정하지 않음'
}

function toApiError(error: unknown) {
  return typeof error === 'object' && error !== null ? error as ApiError : undefined
}

/**
 * 폴더의 정보와 폴더 자체를 다루는 기능. 이름·설명·목표일 수정과 폴더 삭제를 갖는다.
 *
 * 할 일 폴더와 링크 폴더가 같은 폴더를 서로 다른 화면에서 보는 것이라, 폴더를 다루는 자리는
 * 하나여야 한다. 한쪽에서 고친 수정 규칙이 다른 쪽에 반영되지 않는 일을 막는다.
 */
export default function FolderHeader({
  folder,
  titleId,
  deleteMessage,
  showStatus = true,
  onUpdated,
  onDeleted,
}: FolderHeaderProps) {
  const applyFolderToStore = useFolderStore((state) => state.apply)
  const [isSaving, setIsSaving] = useState(false)
  const [isEditingTargetDate, setIsEditingTargetDate] = useState(false)
  const [editValue, setEditValue] = useState('')
  const [editError, setEditError] = useState<string | null>(null)
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const applyUpdated = (updated: Folder) => {
    applyFolderToStore(updated)
    onUpdated?.(updated)
  }

  const saveTextField = async (field: EditableFolderTextField, value: string) => {
    setIsSaving(true)
    try {
      applyUpdated(await updateFolder(folder.id, {
        name: field === 'name' ? value : folder.name,
        description: field === 'description' ? value : folder.description,
        targetDate: folder.targetDate,
        status: folder.status,
        tagId: folder.tag?.id ?? null,
      }))
    } finally {
      setIsSaving(false)
    }
  }

  const getFieldError = (error: unknown, field: EditableFolderTextField) => {
    const apiError = toApiError(error)
    return apiError?.errors?.[field] ?? apiError?.message ?? '폴더 정보를 저장하지 못했습니다.'
  }

  const startEditingTargetDate = () => {
    if (isSaving) return
    setIsEditingTargetDate(true)
    setEditValue(folder.targetDate ?? '')
    setEditError(null)
  }

  const cancelEditingTargetDate = () => {
    if (isSaving) return
    setIsEditingTargetDate(false)
    setEditValue('')
    setEditError(null)
  }

  const saveTargetDate = async () => {
    if (!isEditingTargetDate || isSaving) return
    const targetDate = editValue || null
    if (targetDate === folder.targetDate) {
      cancelEditingTargetDate()
      return
    }

    setIsSaving(true)
    setEditError(null)
    try {
      applyUpdated(await updateFolder(folder.id, {
        name: folder.name,
        description: folder.description,
        targetDate,
        status: folder.status,
        tagId: folder.tag?.id ?? null,
      }))
      setIsEditingTargetDate(false)
      setEditValue('')
    } catch (error: unknown) {
      const apiError = toApiError(error)
      setEditError(apiError?.errors?.targetDate ?? apiError?.message ?? '목표일을 저장하지 못했습니다.')
    } finally {
      setIsSaving(false)
    }
  }

  const removeFolder = async () => {
    if (isDeleting) return
    setIsDeleting(true)
    setDeleteError(null)
    try {
      await deleteFolder(folder.id)
      onDeleted(folder.id)
    } catch (error: unknown) {
      setDeleteError(toApiError(error)?.message ?? '폴더를 삭제하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsDeleting(false)
    }
  }

  const handleDisplayKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Enter' && event.key !== 'F2') return
    event.preventDefault()
    startEditingTargetDate()
  }

  const handleEditorKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      cancelEditingTargetDate()
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      event.currentTarget.blur()
    }
  }

  return (
    <header className={styles.header}>
      <div className={styles['header-top']}>
        <div className={styles.badges} data-tone={folder.id % 4}>
          {folder.tag && <span className={styles.tag}>{folder.tag.name}</span>}
          {showStatus && (
            <span className={styles['folder-status']} data-status={folder.status}>
              {folderStatusLabel[folder.status]}
            </span>
          )}
          <DdayChip targetDate={folder.targetDate} />
        </div>
        <DeleteIconButton
          className={styles['compact-delete-button']}
          iconSize={14}
          label="폴더 삭제"
          active={isConfirmingDelete}
          disabled={isDeleting}
          onClick={() => {
            setIsConfirmingDelete((current) => !current)
            setDeleteError(null)
          }}
        >
          <span>{isConfirmingDelete ? '취소' : '폴더 삭제'}</span>
        </DeleteIconButton>
      </div>

      {isConfirmingDelete && (
        <DeleteConfirmation
          message={deleteMessage}
          ariaLabel="폴더 삭제 확인"
          isDeleting={isDeleting}
          onCancel={() => setIsConfirmingDelete(false)}
          onConfirm={() => void removeFolder()}
        />
      )}
      {deleteError && <p className={styles['delete-error']} role="alert">{deleteError}</p>}

      <div className={styles['editable-group']}>
        <h1 id={titleId}>
          <InlineEditableText
            value={folder.name}
            ariaLabel="폴더 제목"
            maxLength={255}
            requiredMessage="폴더 이름을 입력해 주세요."
            disabled={isSaving}
            onSave={(value) => saveTextField('name', value)}
            getErrorMessage={(error) => getFieldError(error, 'name')}
          />
        </h1>
      </div>
      <div className={styles['editable-group']}>
        <p>
          <InlineEditableText
            value={folder.description}
            emptyText="폴더 설명이 아직 없습니다."
            ariaLabel="폴더 설명"
            requiredMessage="폴더 설명을 입력해 주세요."
            disabled={isSaving}
            onSave={(value) => saveTextField('description', value)}
            getErrorMessage={(error) => getFieldError(error, 'description')}
          />
        </p>
      </div>

      <div className={styles['header-bottom']}>
        {isEditingTargetDate ? (
          <span className={styles['date-editor']}>
            <input
              type="date"
              value={editValue}
              aria-label="폴더 목표일"
              aria-invalid={Boolean(editError)}
              disabled={isSaving}
              autoFocus
              onChange={(event) => { setEditValue(event.target.value); setEditError(null) }}
              onBlur={() => void saveTargetDate()}
              onKeyDown={handleEditorKeyDown}
            />
          </span>
        ) : (
          <button
            type="button"
            className={styles['target-date-chip']}
            title="더블 클릭하여 목표일 수정"
            disabled={isSaving}
            onDoubleClick={startEditingTargetDate}
            onKeyDown={handleDisplayKeyDown}
          >
            <IconCalendarDue size={14} stroke={1.8} aria-hidden="true" />
            <span>
              <span className="sr-only">목표일 </span>
              {formatTargetDate(folder.targetDate)}
            </span>
          </button>
        )}
      </div>
      {editError && <p className={styles['target-date-error']} role="alert">{editError}</p>}
    </header>
  )
}
