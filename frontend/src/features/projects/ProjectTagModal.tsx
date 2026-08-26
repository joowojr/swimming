import { useEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { IconPencil, IconPlus, IconTrash, IconX } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import modalStyles from '../../components/ModalShell.module.css'
import { getProjectTags } from './projectApi'
import type { ProjectTag } from './projectTypes'
import styles from './ProjectTagModal.module.css'

interface ProjectTagModalProps {
  onClose: () => void
}

export default function ProjectTagModal({ onClose }: ProjectTagModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [tags, setTags] = useState<ProjectTag[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [newName, setNewName] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState('')
  const [message, setMessage] = useState<string | null>(null)

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
        setTags(loadedTags)
        setIsLoading(false)
      })
      .catch(() => {
        if (!active) return
        setIsLoading(false)
        setMessage('태그 목록을 불러오지 못했어요. 목업으로 계속할 수 있어요.')
      })
    return () => { active = false }
  }, [])

  const requestClose = () => dialogRef.current?.close()
  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const addTag = () => {
    const name = newName.trim()
    if (!name) return setMessage('태그 이름을 입력해 주세요.')
    if (tags.some((tag) => tag.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
      return setMessage('같은 이름의 태그가 있어요.')
    }
    setTags((current) => [...current, { id: Date.now(), name }])
    setNewName('')
    setMessage(null)
  }

  const startEdit = (tag: ProjectTag) => {
    setEditingId(tag.id)
    setEditingName(tag.name)
    setMessage(null)
  }

  const saveEdit = () => {
    const name = editingName.trim()
    if (editingId === null || !name) return setMessage('태그 이름을 입력해 주세요.')
    if (tags.some((tag) => tag.id !== editingId && tag.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
      return setMessage('같은 이름의 태그가 있어요.')
    }
    setTags((current) => current.map((tag) => tag.id === editingId ? { ...tag, name } : tag))
    setEditingId(null)
    setMessage(null)
  }

  return (
    <dialog ref={dialogRef} className={`${styles.dialog} ${modalStyles.dialog}`} onClose={onClose} onMouseDown={handleBackdrop}>
      <section className={`${styles.modal} ${modalStyles.surface}`} aria-labelledby="project-tag-title">
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="project-tag-title">프로젝트 태그 관리</h2>
            <p>프로젝트를 분류할 태그를 추가하고 정리하세요.</p>
          </div>
          <button type="button" className={styles.close} aria-label="태그 관리 닫기" onClick={requestClose}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <div className={styles.addRow}>
            <input value={newName} maxLength={30} placeholder="새 태그 이름" aria-label="새 태그 이름"
              onChange={(event) => setNewName(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') addTag() }} />
            <ActionButton icon={<IconPlus size={16} aria-hidden="true" />} onClick={addTag}>추가</ActionButton>
          </div>
          {message && <p className={styles.message} role="status">{message}</p>}
          {isLoading ? <p className={styles.message} role="status">태그를 불러오는 중…</p> : null}
          <ul className={styles.list}>
            {tags.map((tag) => (
              <li key={tag.id}>
                {editingId === tag.id ? (
                  <input value={editingName} maxLength={30} aria-label={`${tag.name} 태그 이름 수정`} autoFocus
                    onChange={(event) => setEditingName(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') saveEdit() }} />
                ) : <span>{tag.name}</span>}
                <div className={styles.actions}>
                  {editingId === tag.id
                    ? <ActionButton variant="plain" onClick={saveEdit}>저장</ActionButton>
                    : <button type="button" className={styles.iconButton} aria-label={`${tag.name} 수정`} onClick={() => startEdit(tag)}><IconPencil size={16} aria-hidden="true" /></button>}
                  <button type="button" className={styles.iconButton} aria-label={`${tag.name} 삭제`} onClick={() => setTags((current) => current.filter((item) => item.id !== tag.id))}>
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
