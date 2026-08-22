import { useEffect, useRef, useState } from 'react'
import { IconLoader2, IconPlus, IconX } from '@tabler/icons-react'
import type { MouseEvent } from 'react'
import { getProject } from '../projects/projectApi'
import type { Project, ProjectDetail } from '../projects/projectTypes'
import styles from './TaskPickerModal.module.css'

interface TaskPickerModalProps {
  projects: Project[]
  selectedTaskIds: ReadonlySet<number>
  onAdd: (tasks: ProjectDetail['tasks']) => Promise<void>
  onAddAdHoc: (title: string, projectId: number | null) => Promise<void>
  onClose: () => void
}

type LoadState =
  | { status: 'loading' }
  | { status: 'ready'; details: ProjectDetail[] }
  | { status: 'error' }

export default function TaskPickerModal({
  projects,
  selectedTaskIds,
  onAdd,
  onAddAdHoc,
  onClose,
}: TaskPickerModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [title, setTitle] = useState('')
  const [projectId, setProjectId] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    dialog.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  useEffect(() => {
    let active = true
    void Promise.all(projects.map((project) => getProject(project.id)))
      .then((details) => { if (active) setState({ status: 'ready', details }) })
      .catch(() => { if (active) setState({ status: 'error' }) })
    return () => { active = false }
  }, [projects])

  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) dialogRef.current?.close()
  }

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby="task-picker-title"
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <section className={styles.modal}>
        <header className={styles.header}>
          <div>
            <h2 id="task-picker-title">할 일 추가</h2>
            <p>새 할 일을 만들거나 기존 작업을 골라 주세요.</p>
          </div>
          <button type="button" aria-label="Task 선택 창 닫기" onClick={() => dialogRef.current?.close()}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <form
            className={styles['quick-add']}
            onSubmit={(event) => {
              event.preventDefault()
              const trimmedTitle = title.trim()
              if (!trimmedTitle || isSubmitting) return
              setIsSubmitting(true)
              setSubmitError(null)
              void onAddAdHoc(trimmedTitle, projectId ? Number(projectId) : null)
                .then(() => dialogRef.current?.close())
                .catch(() => setSubmitError('할 일을 추가하지 못했습니다. 다시 시도해 주세요.'))
                .finally(() => setIsSubmitting(false))
            }}
          >
            <label htmlFor="daily-plan-ad-hoc-title">할 일 직접 추가</label>
            <select
              aria-label="할 일을 추가할 프로젝트"
              value={projectId}
              disabled={isSubmitting}
              onChange={(event) => setProjectId(event.target.value)}
            >
              <option value="">프로젝트 선택</option>
              {projects.map((project) => (
                <option value={project.id} key={project.id}>{project.name}</option>
              ))}
            </select>
            <div className={styles['quick-add-row']}>
              <input
                id="daily-plan-ad-hoc-title"
                value={title}
                maxLength={255}
                placeholder="할 일을 입력해 주세요"
                onChange={(event) => setTitle(event.target.value)}
              />
              <button type="submit" disabled={!title.trim() || isSubmitting}>추가</button>
            </div>
            {submitError && <p role="alert">{submitError}</p>}
          </form>
          {state.status === 'loading' ? (
            <p className={styles.state} role="status">
              <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
              작업을 불러오는 중…
            </p>
          ) : state.status === 'error' ? (
            <p className={styles.state} role="alert">작업을 불러오지 못했습니다. 잠시 후 다시 열어 주세요.</p>
          ) : state.details.every((detail) => detail.tasks.length === 0) ? (
            <p className={styles.state}>계획에 추가할 Task가 아직 없습니다.</p>
          ) : (
            state.details.map((detail) => {
              if (detail.tasks.length === 0) return null
              return (
                <section className={styles.group} key={detail.id} aria-labelledby={`picker-project-${detail.id}`}>
                  <h3 id={`picker-project-${detail.id}`}>{detail.name}</h3>
                  <ul>
                    {detail.tasks.map((task) => {
                      const selected = selectedTaskIds.has(task.id)
                      return (
                        <li key={task.id}>
                          <span>{task.title}</span>
                          <button
                            type="button"
                            disabled={selected || isSubmitting}
                            onClick={() => {
                              setIsSubmitting(true)
                              setSubmitError(null)
                              void onAdd([task])
                                .then(() => dialogRef.current?.close())
                                .catch(() => setSubmitError('작업을 추가하지 못했습니다. 다시 시도해 주세요.'))
                                .finally(() => setIsSubmitting(false))
                            }}
                          >
                            {!selected && <IconPlus size={16} aria-hidden="true" />}
                            {selected ? '추가됨' : '추가'}
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                </section>
              )
            })
          )}
        </div>
      </section>
    </dialog>
  )
}
