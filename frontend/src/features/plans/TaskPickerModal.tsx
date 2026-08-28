import { useEffect, useRef, useState } from 'react'
import { IconCheck, IconLoader2, IconPlus, IconX } from '@tabler/icons-react'
import type { MouseEvent } from 'react'
import { getProject } from '../projects/projectApi'
import type { Project, ProjectDetail } from '../projects/projectTypes'
import styles from './TaskPickerModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'

interface TaskPickerModalProps {
  projects: Project[]
  selectedTaskIds: ReadonlySet<number>
  onAdd: (tasks: ProjectDetail['tasks']) => Promise<void>
  onAddTask: (title: string, projectId: number | null) => Promise<void>
  onClose: () => void
}

type LoadState =
  | { status: 'loading' }
  | { status: 'ready'; details: ProjectDetail[] }
  | { status: 'error' }

type AddMode = 'direct' | 'folder'

// const TIME_PERIOD_CHIPS = [
//   { label: '아침', emoji: '🌅' },
//   { label: '오후', emoji: '🌤️' },
//   { label: '밤', emoji: '🌙' },
//   { label: '새벽', emoji: '🌌' },
//   { label: '직접 설정', emoji: '🕰️' },
// ]

const PRIORITY_CHIPS = [
  { label: '먼저', emoji: '📌' },
  { label: '보통', emoji: '☀️' },
  { label: '여유', emoji: '🌿' },
]

export default function TaskPickerModal({
  projects,
  selectedTaskIds,
  onAdd,
  onAddTask,
  onClose,
}: TaskPickerModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [addMode, setAddMode] = useState<AddMode>('direct')
  const [title, setTitle] = useState('')
  const [projectId, setProjectId] = useState('')
  const [taskProjectId, setTaskProjectId] = useState('')
  const [pendingTaskIds, setPendingTaskIds] = useState<Set<number>>(new Set())
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [taskSubmitError, setTaskSubmitError] = useState<string | null>(null)
  const [selectedPriority, setSelectedPriority] = useState<string | null>(null)

  const activeProject = state.status === 'ready'
    ? state.details.find((detail) => detail.id === Number(taskProjectId))
    : undefined
  const pendingTasks = state.status === 'ready'
    ? state.details.flatMap((detail) => detail.tasks
      .filter((task) => pendingTaskIds.has(task.id))
      .map((task) => ({ task, projectName: detail.name })))
    : []
  const totalTaskCount = addMode === 'direct'
    ? (title.trim() ? 1 : 0)
    : pendingTaskIds.size

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

  const selectAddMode = (mode: AddMode) => {
    setAddMode(mode)
    setTaskSubmitError(null)
  }

  const addAllTasks = async () => {
    const trimmedTitle = title.trim()
    if (totalTaskCount === 0 || isSubmitting) return

    setIsSubmitting(true)
    setTaskSubmitError(null)

    try {
      if (addMode === 'direct') {
        await onAddTask(trimmedTitle, projectId ? Number(projectId) : null)
      } else {
        await onAdd(pendingTasks.map(({ task }) => task))
      }
      dialogRef.current?.close()
    } catch {
      setTaskSubmitError('할 일을 추가하지 못했습니다. 입력과 선택 내용을 확인해 주세요.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <dialog
      id="task-picker-dialog"
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      aria-labelledby="task-picker-title"
      aria-busy={isSubmitting}
      onCancel={(event) => { if (isSubmitting) event.preventDefault() }}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <form
        className={`${styles.modal} ${modalStyles.surface}`}
        onSubmit={(event) => {
          event.preventDefault()
          void addAllTasks()
        }}
      >
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="task-picker-title">할 일 추가</h2>
            <p>새 할 일을 만들거나 폴더별 할 일을 골라 주세요.</p>
          </div>
          <button type="button" aria-label="Task 선택 창 닫기" disabled={isSubmitting} onClick={requestClose}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <div
            className={styles['add-mode-tabs']}
            role="tablist"
            aria-label="할 일 추가 방식"
            onKeyDown={(event) => {
              if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
              event.preventDefault()
              const nextMode = event.key === 'ArrowLeft' || event.key === 'Home'
                ? 'direct'
                : 'folder'
              selectAddMode(nextMode)
              window.requestAnimationFrame(() => {
                document.getElementById(`${nextMode}-add-tab`)?.focus()
              })
            }}
          >
            <button
              id="direct-add-tab"
              type="button"
              role="tab"
              aria-selected={addMode === 'direct'}
              aria-controls="direct-add-panel"
              tabIndex={addMode === 'direct' ? 0 : -1}
              disabled={isSubmitting}
              onClick={() => selectAddMode('direct')}
            >
              직접 추가
            </button>
            <button
              id="folder-add-tab"
              type="button"
              role="tab"
              aria-selected={addMode === 'folder'}
              aria-controls="folder-add-panel"
              tabIndex={addMode === 'folder' ? 0 : -1}
              disabled={isSubmitting}
              onClick={() => selectAddMode('folder')}
            >
              폴더에서 선택
            </button>
          </div>
          <section className={styles['planning-option-field']} aria-label="할 일 계획 옵션">
            <div className={styles['planning-option-group']}>
              <div className={styles['planning-option-heading']}>
                <span className={styles['planning-option-label']}>우선순위</span>
                <span className={styles['planning-option-help']}>선택</span>
              </div>
              <div className={styles['planning-option-chips']} role="list">
                {PRIORITY_CHIPS.map((option) => (
                  <span role="listitem" key={option.label}>
                    <button
                      type="button"
                      aria-pressed={selectedPriority === option.label}
                      disabled={isSubmitting}
                      onClick={() => setSelectedPriority((current) => (
                        current === option.label ? null : option.label
                      ))}
                    >
                      <span aria-hidden="true">{option.emoji}</span>
                      {option.label}
                    </button>
                  </span>
                ))}
              </div>
            </div>
            {/*<div className={styles['planning-option-group']}>*/}
            {/*  <span className={styles['planning-option-label']}>시간대</span>*/}
            {/*  <div className={styles['planning-option-chips']} role="list">*/}
            {/*    {TIME_PERIOD_CHIPS.map((option) => (*/}
            {/*      <span role="listitem" key={option.label}>*/}
            {/*        <button*/}
            {/*          type="button"*/}
            {/*          aria-pressed={selectedTimePeriod === option.label}*/}
            {/*          onClick={() => setSelectedTimePeriod((current) => (*/}
            {/*            current === option.label ? null : option.label*/}
            {/*          ))}*/}
            {/*        >*/}
            {/*          <span aria-hidden="true">{option.emoji}</span>*/}
            {/*          {option.label}*/}
            {/*        </button>*/}
            {/*      </span>*/}
            {/*    ))}*/}
            {/*  </div>*/}
            {/*</div>*/}
            {/*{selectedTimePeriod === '직접 설정' && (*/}
            {/*  <label className={styles['custom-time']} htmlFor="daily-plan-custom-time">*/}
            {/*    <span>시각 선택</span>*/}
            {/*    <input*/}
            {/*      id="daily-plan-custom-time"*/}
            {/*      type="time"*/}
            {/*      value={customTime}*/}
            {/*      onChange={(event) => setCustomTime(event.target.value)}*/}
            {/*    />*/}
            {/*  </label>*/}
            {/*)}*/}
          </section>
          <section
            id="direct-add-panel"
            className={`${styles['mode-panel']} ${styles['quick-add']}`}
            role="tabpanel"
            aria-labelledby="direct-add-tab"
            hidden={addMode !== 'direct'}
          >
            <label htmlFor="daily-plan-ad-hoc-title">새 할 일</label>
            <select
              aria-label="할 일을 추가할 폴더"
              value={projectId}
              disabled={isSubmitting}
              onChange={(event) => {
                setProjectId(event.target.value)
                setTaskSubmitError(null)
              }}
            >
              <option value="">미분류</option>
              {projects.map((project) => (
                <option value={project.id} key={project.id}>{project.name}</option>
              ))}
            </select>
            <input
              id="daily-plan-ad-hoc-title"
              value={title}
              maxLength={255}
              placeholder="할 일을 입력해 주세요"
              disabled={isSubmitting}
              onChange={(event) => {
                setTitle(event.target.value)
                setTaskSubmitError(null)
              }}
            />
          </section>
          <section
            id="folder-add-panel"
            className={`${styles['mode-panel']} ${styles['folder-add']}`}
            role="tabpanel"
            aria-labelledby="folder-add-tab"
            hidden={addMode !== 'folder'}
          >
            <section className={styles['task-select']} aria-labelledby="task-select-label">
              <label id="task-select-label" htmlFor="daily-plan-task-project">폴더</label>
              <select
                id="daily-plan-task-project"
                value={taskProjectId}
                disabled={isSubmitting || state.status !== 'ready'}
                onChange={(event) => setTaskProjectId(event.target.value)}
              >
                <option value="">폴더</option>
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
                <p className={styles.state}>폴더를 선택하면 할 일을 확인할 수 있습니다.</p>
              ) : !activeProject || activeProject.tasks.length === 0 ? (
                <p className={styles.state}>이 폴더에는 선택할 Task가 없습니다.</p>
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
                            {alreadyAdded ? '추가됨' : pending ? '' : ''}
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )}
            </section>
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
          </section>
        </div>

        <footer className={styles.footer}>
          {taskSubmitError && <p className={styles.error} role="alert">{taskSubmitError}</p>}
          <button
            type="submit"
            disabled={totalTaskCount === 0 || isSubmitting}
          >
            {isSubmitting && (
              <IconLoader2 className={styles.spinner} size={18} aria-hidden="true" />
            )}
            {isSubmitting
              ? '추가 중…'
              : addMode === 'direct'
                ? '할 일 추가'
                : `선택한 할 일 ${pendingTaskIds.size}개 추가`}
          </button>
        </footer>
      </form>
    </dialog>
  )
}
