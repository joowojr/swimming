import { useEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { IconPencil, IconPlus, IconTrash, IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import ActionButton from '../../components/ActionButton'
import modalStyles from '../../components/ModalShell.module.css'
import {
  createProjectTag,
  deleteProjectTag,
  getProjectTags,
  updateProjectTag,
} from './projectApi'
import type { ProjectTag } from './projectTypes'
import styles from './ProjectTagModal.module.css'

interface ProjectTagModalProps {
  onClose: () => void
  onChanged: () => void
}

type PendingAction = 'create' | `update-${number}` | `delete-${number}` | null

function sortTags(tags: ProjectTag[]) {
  return [...tags].sort((left, right) => left.name.localeCompare(right.name, 'ko'))
}

function requestErrorMessage(error: unknown, fallback: string) {
  const apiError = error as ApiError
  return apiError.errors?.name ?? apiError.message ?? fallback
}

export default function ProjectTagModal({ onClose, onChanged }: ProjectTagModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [tags, setTags] = useState<ProjectTag[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [newName, setNewName] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)

  useEffect(() => {
    dialogRef.current?.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  useEffect(() => {
    let active = true
    void getProjectTags()
      .then((loadedTags) => {
        if (!active) return
        setTags(sortTags(loadedTags))
        setIsLoading(false)
      })
      .catch(() => {
        if (!active) return
        setIsLoading(false)
        setMessage('태그 목록을 불러오지 못했습니다. 잠시 후 다시 열어 주세요.')
      })
    return () => { active = false }
  }, [])

  const requestClose = () => {
    if (pendingAction === null) dialogRef.current?.close()
  }
  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const addTag = async () => {
    const name = newName.trim()
    if (!name) return setMessage('태그 이름을 입력해 주세요.')
    if (tags.some((tag) => tag.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
      return setMessage('같은 이름의 태그가 있어요.')
    }
    setPendingAction('create')
    setMessage(null)
    try {
      const created = await createProjectTag({ name })
      setTags((current) => sortTags([...current, created]))
      setNewName('')
      onChanged()
    } catch (error) {
      setMessage(requestErrorMessage(error, '태그를 추가하지 못했습니다.'))
    } finally {
      setPendingAction(null)
    }
  }

  const startEdit = (tag: ProjectTag) => {
    setEditingId(tag.id)
    setEditingName(tag.name)
    setMessage(null)
  }

  const saveEdit = async () => {
    const name = editingName.trim()
    if (editingId === null || !name) return setMessage('태그 이름을 입력해 주세요.')
    if (tags.some((tag) => tag.id !== editingId && tag.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
      return setMessage('같은 이름의 태그가 있어요.')
    }
    const tagId = editingId
    setPendingAction(`update-${tagId}`)
    setMessage(null)
    try {
      const updated = await updateProjectTag(tagId, { name })
      setTags((current) => sortTags(
        current.map((tag) => tag.id === tagId ? updated : tag),
      ))
      setEditingId(null)
      setEditingName('')
      onChanged()
    } catch (error) {
      setMessage(requestErrorMessage(error, '태그 이름을 수정하지 못했습니다.'))
    } finally {
      setPendingAction(null)
    }
  }

  const removeTag = async (tag: ProjectTag) => {
    const confirmed = window.confirm(
      `'${tag.name}' 태그를 삭제하면 연결된 폴더에서 태그가 해제됩니다. 삭제할까요?`,
    )
    if (!confirmed) return

    setPendingAction(`delete-${tag.id}`)
    setMessage(null)
    try {
      await deleteProjectTag(tag.id)
      setTags((current) => current.filter((item) => item.id !== tag.id))
      if (editingId === tag.id) {
        setEditingId(null)
        setEditingName('')
      }
      onChanged()
    } catch (error) {
      setMessage(requestErrorMessage(error, '태그를 삭제하지 못했습니다.'))
    } finally {
      setPendingAction(null)
    }
  }

  return (
    <dialog ref={dialogRef} className={`${styles.dialog} ${modalStyles.dialog}`} onClose={onClose} onMouseDown={handleBackdrop}
      onCancel={(event) => { if (pendingAction !== null) event.preventDefault() }}>
      <section className={`${styles.modal} ${modalStyles.surface}`} aria-labelledby="project-tag-title">
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="project-tag-title">폴더 태그 관리</h2>
            <p>폴더를 분류할 태그를 추가하고 정리하세요.</p>
          </div>
          <button type="button" className={styles.close} aria-label="태그 관리 닫기" onClick={requestClose} disabled={pendingAction !== null}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <div className={styles.addRow}>
            <input value={newName} maxLength={30} placeholder="새 태그 이름" aria-label="새 태그 이름" disabled={pendingAction !== null}
              onChange={(event) => setNewName(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') void addTag() }} />
            <ActionButton icon={<IconPlus size={16} aria-hidden="true" />} onClick={() => void addTag()}
              isLoading={pendingAction === 'create'} disabled={pendingAction !== null}>추가</ActionButton>
          </div>
          {message && <p className={styles.message} role="alert">{message}</p>}
          {isLoading ? <p className={styles.message} role="status">태그를 불러오는 중…</p> : null}
          <ul className={styles.list}>
            {tags.map((tag) => (
              <li key={tag.id}>
                {editingId === tag.id ? (
                  <input value={editingName} maxLength={30} aria-label={`${tag.name} 태그 이름 수정`} autoFocus disabled={pendingAction !== null}
                    onChange={(event) => setEditingName(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') void saveEdit() }} />
                ) : <span>{tag.name}</span>}
                <div className={styles.actions}>
                  {editingId === tag.id
                    ? <ActionButton variant="plain" onClick={() => void saveEdit()}
                        isLoading={pendingAction === `update-${tag.id}`} disabled={pendingAction !== null}>저장</ActionButton>
                    : <button type="button" className={styles.iconButton} aria-label={`${tag.name} 수정`} onClick={() => startEdit(tag)} disabled={pendingAction !== null}><IconPencil size={16} aria-hidden="true" /></button>}
                  <button type="button" className={styles.iconButton} aria-label={`${tag.name} 삭제`} onClick={() => void removeTag(tag)} disabled={pendingAction !== null}>
                    <IconTrash size={16} aria-hidden="true" />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </dialog>
  )
}
