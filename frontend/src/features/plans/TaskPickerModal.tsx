import { useEffect, useRef, useState } from 'react'
import { IconLoader2, IconPlus, IconX } from '@tabler/icons-react'
import type { MouseEvent } from 'react'
import { getProject } from '../projects/projectApi'
import type { Project, ProjectDetail } from '../projects/projectTypes'
import styles from './TaskPickerModal.module.css'

interface TaskPickerModalProps {
  projects: Project[]
  selectedTaskIds: ReadonlySet<number>
  onAdd: (tasks: ProjectDetail['tasks'], project: Project) => void
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
  onClose,
}: TaskPickerModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [state, setState] = useState<LoadState>({ status: 'loading' })

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
            <h2 id="task-picker-title">계획에 Task 추가</h2>
            <p>오늘 이어가고 싶은 Task를 골라 주세요.</p>
          </div>
          <button type="button" aria-label="Task 선택 창 닫기" onClick={() => dialogRef.current?.close()}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          {state.status === 'loading' ? (
            <p className={styles.state} role="status">
              <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
              Task를 불러오는 중…
            </p>
          ) : state.status === 'error' ? (
            <p className={styles.state} role="alert">Task를 불러오지 못했습니다. 잠시 후 다시 열어 주세요.</p>
          ) : state.details.every((detail) => detail.tasks.length === 0) ? (
            <p className={styles.state}>계획에 추가할 Task가 아직 없습니다.</p>
          ) : (
            state.details.map((detail) => {
              const project = projects.find((item) => item.id === detail.id)
              if (!project || detail.tasks.length === 0) return null
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
                            disabled={selected}
                            onClick={() => {
                              onAdd([task], project)
                              dialogRef.current?.close()
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
