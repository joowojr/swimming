import { useEffect, useRef, useState } from 'react'
import { IconCheck, IconLoader2, IconPlus, IconX } from '@tabler/icons-react'
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

type SubmittingAction = 'ad-hoc' | 'tasks' | null

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
  const [taskProjectId, setTaskProjectId] = useState('')
  const [pendingTaskIds, setPendingTaskIds] = useState<Set<number>>(new Set())
  const [submittingAction, setSubmittingAction] = useState<SubmittingAction>(null)
  const [adHocError, setAdHocError] = useState<string | null>(null)
  const [adHocNotice, setAdHocNotice] = useState<string | null>(null)
  const [taskSubmitError, setTaskSubmitError] = useState<string | null>(null)
  const isSubmitting = submittingAction !== null

  const activeProject = state.status === 'ready'
    ? state.details.find((detail) => detail.id === Number(taskProjectId))
    : undefined
  const pendingTasks = state.status === 'ready'
    ? state.details.flatMap((detail) => detail.tasks
      .filter((task) => pendingTaskIds.has(task.id))
      .map((task) => ({ task, projectName: detail.name })))
    : []

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

  const requestClose = () => {
    if (!isSubmitting) dialogRef.current?.close()
  }

  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const toggleTask = (taskId: number) => {
    setTaskSubmitError(null)
    setPendingTaskIds((current) => {
      const next = new Set(current)
      if (next.has(taskId)) next.delete(taskId)
      else next.add(taskId)
      return next
    })
  }

  const addSelectedTasks = async () => {
    if (state.status !== 'ready' || pendingTaskIds.size === 0 || isSubmitting) return
    const tasks = state.details
      .flatMap((detail) => detail.tasks)
      .filter((task) => pendingTaskIds.has(task.id))
    if (tasks.length === 0) return

    setSubmittingAction('tasks')
    setTaskSubmitError(null)
    try {
      await onAdd(tasks)
      dialogRef.current?.close()
    } catch {
      setTaskSubmitError('선택한 할 일을 추가하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setSubmittingAction(null)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby="task-picker-title"
      aria-busy={isSubmitting}
      onCancel={(event) => { if (isSubmitting) event.preventDefault() }}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <section className={styles.modal}>
        <header className={styles.header}>
          <div>
            <h2 id="task-picker-title">할 일 추가</h2>
            <p>새 할 일을 만들거나 프로젝트별 Task를 골라 주세요.</p>
          </div>
          <button type="button" aria-label="Task 선택 창 닫기" disabled={isSubmitting} onClick={requestClose}>
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
              setSubmittingAction('ad-hoc')
              setAdHocError(null)
              setAdHocNotice(null)
              void onAddAdHoc(trimmedTitle, projectId ? Number(projectId) : null)
                .then(() => {
                  setTitle('')
                  setProjectId('')
                  setAdHocNotice('할 일을 추가했습니다.')
                })
                .catch(() => setAdHocError('할 일을 추가하지 못했습니다. 다시 시도해 주세요.'))
                .finally(() => setSubmittingAction(null))
            }}
          >
            <label htmlFor="daily-plan-ad-hoc-title">할 일 직접 추가</label>
            <select
              aria-label="할 일을 추가할 프로젝트"
              value={projectId}
              disabled={isSubmitting}
              onChange={(event) => {
                setProjectId(event.target.value)
                setAdHocError(null)
                setAdHocNotice(null)
              }}
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
                onChange={(event) => {
                  setTitle(event.target.value)
                  setAdHocError(null)
                  setAdHocNotice(null)
                }}
              />
              <button type="submit" disabled={!title.trim() || isSubmitting}>
                {submittingAction === 'ad-hoc' && (
                  <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
                )}
                {submittingAction === 'ad-hoc' ? '추가 중…' : '추가'}
              </button>
            </div>
            <div className={styles.feedback} aria-live="polite">
              {adHocError
                ? <p className={styles.error} role="alert">{adHocError}</p>
                : adHocNotice && <p className={styles.notice}>{adHocNotice}</p>}
            </div>
          </form>
          <section className={styles['task-select']} aria-labelledby="task-select-label">
            <label id="task-select-label" htmlFor="daily-plan-task-project">할 일 선택</label>
            <select
              id="daily-plan-task-project"
              value={taskProjectId}
              disabled={isSubmitting || state.status !== 'ready'}
              onChange={(event) => setTaskProjectId(event.target.value)}
            >
              <option value="">프로젝트 선택</option>
              {state.status === 'ready' && state.details.map((detail) => (
                <option value={detail.id} key={detail.id}>{detail.name}</option>
              ))}
            </select>

            {state.status === 'loading' ? (
              <p className={styles.state} role="status">
                <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
                작업을 불러오는 중…
              </p>
            ) : state.status === 'error' ? (
              <p className={styles.state} role="alert">작업을 불러오지 못했습니다. 잠시 후 다시 열어 주세요.</p>
            ) : !taskProjectId ? (
              <p className={styles.state}>프로젝트를 선택하면 Task를 확인할 수 있습니다.</p>
            ) : !activeProject || activeProject.tasks.length === 0 ? (
              <p className={styles.state}>이 프로젝트에는 선택할 Task가 없습니다.</p>
            ) : (
              <div className={styles.group}>
                <ul>
                  {activeProject.tasks.map((task) => {
                    const alreadyAdded = selectedTaskIds.has(task.id)
                    const pending = pendingTaskIds.has(task.id)
                    return (
                      <li className={alreadyAdded || pending ? styles.selected : undefined} key={task.id}>
                        <span>{task.title}</span>
                        <button
                          type="button"
                          aria-pressed={pending}
                          disabled={alreadyAdded || isSubmitting}
                          onClick={() => toggleTask(task.id)}
                        >
                          {pending
                            ? <IconCheck size={16} aria-hidden="true" />
                            : !alreadyAdded && <IconPlus size={16} aria-hidden="true" />}
                          {alreadyAdded ? '추가됨' : pending ? '선택됨' : '선택'}
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </div>
            )}
          </section>
        </div>

        <footer className={styles.footer}>
          {pendingTasks.length > 0 && (
            <section className={styles['selected-tasks']} aria-labelledby="selected-tasks-title">
              <div className={styles['selected-tasks-heading']}>
                <h3 id="selected-tasks-title">선택한 할 일</h3>
                <span>{pendingTasks.length}개</span>
              </div>
              <ul>
                {pendingTasks.map(({ task, projectName }) => (
                  <li key={task.id}>
                    <span>
                      <small>{projectName}</small>
                      <strong>{task.title}</strong>
                    </span>
                    <button
                      type="button"
                      aria-label={`${projectName}의 ${task.title} 선택 해제`}
                      disabled={isSubmitting}
                      onClick={() => toggleTask(task.id)}
                    >
                      <IconX size={16} aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {taskSubmitError && <p className={styles.error} role="alert">{taskSubmitError}</p>}
          <button
            type="button"
            disabled={pendingTaskIds.size === 0 || isSubmitting}
            onClick={() => void addSelectedTasks()}
          >
            {submittingAction === 'tasks' && (
              <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
            )}
            {submittingAction === 'tasks' ? '추가 중…' : `선택한 할 일 ${pendingTaskIds.size}개 추가`}
          </button>
        </footer>
      </section>
    </dialog>
  )
}
