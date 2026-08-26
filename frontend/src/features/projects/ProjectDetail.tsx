import type {CSSProperties, KeyboardEvent} from 'react'
import {useCallback, useEffect, useRef, useState} from 'react'
import {IconChevronRight, IconFilter} from '@tabler/icons-react'
import {Link, useNavigate} from 'react-router-dom'
import type {ApiError} from '../../api/client'
import ActionButton from '../../components/ActionButton'
import DeleteIconButton from '../../components/DeleteIconButton'
import InlineEditableText from '../../components/InlineEditableText'
import CreateTaskComposer from '../tasks/CreateTaskComposer'
import {deleteTasks} from '../tasks/taskApi'
import {TASK_STATUS_LABEL, TASK_STATUS_VALUES} from '../tasks/taskLabels'
import type {TaskStatus} from '../tasks/taskTypes'
import {deleteProject, getProject, updateProject} from './projectApi'
import {useProjectStore} from '../../store/projectStore'
import type {ProjectDetail as ProjectDetailData, ProjectStatus} from './projectTypes'
import TaskList from './TaskList'
import NoteCard from '../note/NoteCard'
import styles from './ProjectDetail.module.css'

interface ProjectDetailProps {
  projectId: number | null
  onDeleted: (projectId: number) => void
}

type DetailState =
  | { status: 'loading' }
  | { status: 'ready'; project: ProjectDetailData }
  | { status: 'error'; notFound: boolean }

type TaskFilter = 'ALL' | TaskStatus
type EditableProjectTextField = 'name' | 'description'

// const taskFilters: Array<{ value: TaskFilter; label: string }> = [
//   { value: 'ALL', label: '전체' },
//   ...TASK_STATUS_VALUES.map((status) => ({
//     value: status,
//     label: TASK_STATUS_LABEL[status],
//   })),
// ]

const projectStatusLabel: Record<ProjectStatus, string> = {
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

function isNotFound(error: unknown) {
  return typeof error === 'object' && error !== null && (error as ApiError).status === 404
}

function openSelectPicker(select: HTMLSelectElement | null | undefined) {
  if (!select || select.disabled) return
  select.focus()
  try {
    select.showPicker()
  } catch {
    select.focus()
  }
}

export default function ProjectDetail({ projectId, onDeleted }: ProjectDetailProps) {
  const navigate = useNavigate()
  const applyProjectToStore = useProjectStore((state) => state.apply)
  const [requestKey, setRequestKey] = useState(0)
  const [state, setState] = useState<DetailState>(
    projectId === null ? { status: 'error', notFound: true } : { status: 'loading' },
  )
  const [statusFilter, setStatusFilter] = useState<TaskFilter>('ALL')
  const filterSelectRef = useRef<HTMLSelectElement>(null)
  const [isDeleteMode, setIsDeleteMode] = useState(false)
  const [selectedTaskIds, setSelectedTaskIds] = useState<Set<number>>(new Set())
  const [isDeletingTasks, setIsDeletingTasks] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [isConfirmingProjectDelete, setIsConfirmingProjectDelete] = useState(false)
  const [isDeletingProject, setIsDeletingProject] = useState(false)
  const [projectDeleteError, setProjectDeleteError] = useState<string | null>(null)
  const [isEditingTargetDate, setIsEditingTargetDate] = useState(false)
  const [editValue, setEditValue] = useState('')
  const [editError, setEditError] = useState<string | null>(null)
  const [isSavingProject, setIsSavingProject] = useState(false)
  const taskInputRef = useRef<HTMLInputElement>(null)
  const leaveDeleteMode = () => {
    setIsDeleteMode(false)
    setSelectedTaskIds(new Set())
    setDeleteError(null)
  }

  const toggleTaskSelection = (taskId: number) => {
    setSelectedTaskIds((current) => {
      const next = new Set(current)
      if (next.has(taskId)) next.delete(taskId)
      else next.add(taskId)
      return next
    })
    setDeleteError(null)
  }

  const removeSelectedTasks = async () => {
    if (selectedTaskIds.size === 0) return

    const taskIdsToDelete = new Set(selectedTaskIds)
    setIsDeletingTasks(true)
    setDeleteError(null)
    try {
      await deleteTasks({ taskIds: [...taskIdsToDelete] })
      setState((current) => {
        if (current.status !== 'ready') return current

        const tasks = current.project.tasks.filter((task) => !taskIdsToDelete.has(task.id))
        const completedTaskCount = tasks.filter((task) => task.status === 'DONE').length
        const totalTaskCount = tasks.length

        return {
          status: 'ready',
          project: {
            ...current.project,
            tasks,
            progress: {
              totalTaskCount,
              completedTaskCount,
              completionPct: totalTaskCount === 0
                ? 0
                : Math.floor(completedTaskCount * 100 / totalTaskCount),
            },
          },
        }
      })
      leaveDeleteMode()
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setDeleteError(apiMessage ?? '선택한 작업을 삭제하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsDeletingTasks(false)
    }
  }

  const removeProject = async (project: ProjectDetailData) => {
    if (isDeletingProject) return
    setIsDeletingProject(true)
    setProjectDeleteError(null)
    try {
      await deleteProject(project.id)
      onDeleted(project.id)
      navigate('/projects', { replace: true })
    } catch (error: unknown) {
      const apiMessage = typeof error === 'object' && error !== null
        ? (error as ApiError).message
        : undefined
      setProjectDeleteError(apiMessage ?? '프로젝트를 삭제하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsDeletingProject(false)
    }
  }

  const startEditingTargetDate = (value: string | null) => {
    if (isSavingProject) return
    setIsEditingTargetDate(true)
    setEditValue(value ?? '')
    setEditError(null)
  }

  const cancelEditingTargetDate = () => {
    if (isSavingProject) return
    setIsEditingTargetDate(false)
    setEditValue('')
    setEditError(null)
  }

  const applyUpdatedProject = (project: ProjectDetailData, updated: Awaited<ReturnType<typeof updateProject>>) => {
    applyProjectToStore(updated)
    setState({
      status: 'ready',
      project: {
        ...project,
        name: updated.name,
        description: updated.description,
        targetDate: updated.targetDate,
        status: updated.status,
        tag: updated.tag,
      },
    })
  }

  const saveProjectTextField = async (
    project: ProjectDetailData,
    field: EditableProjectTextField,
    value: string,
  ) => {
    setIsSavingProject(true)
    try {
      const updated = await updateProject(project.id, {
        name: field === 'name' ? value : project.name,
        description: field === 'description' ? value : project.description,
        targetDate: project.targetDate,
        status: project.status,
        tagId: project.tag?.id ?? null,
      })
      applyUpdatedProject(project, updated)
    } finally {
      setIsSavingProject(false)
    }
  }

  const getProjectFieldError = (error: unknown, field: EditableProjectTextField) => {
    const apiError = typeof error === 'object' && error !== null ? error as ApiError : undefined
    return apiError?.errors?.[field]
      ?? apiError?.message
      ?? '프로젝트 정보를 저장하지 못했습니다.'
  }

  const saveTargetDate = async (project: ProjectDetailData) => {
    if (!isEditingTargetDate || isSavingProject) return
    const targetDate = editValue || null
    if (targetDate === project.targetDate) {
      cancelEditingTargetDate()
      return
    }

    setIsSavingProject(true)
    setEditError(null)
    try {
      const updated = await updateProject(project.id, {
        name: project.name,
        description: project.description,
        targetDate,
        status: project.status,
        tagId: project.tag?.id ?? null,
      })
      applyUpdatedProject(project, updated)
      setIsEditingTargetDate(false)
      setEditValue('')
    } catch (error: unknown) {
      const apiError = typeof error === 'object' && error !== null ? error as ApiError : undefined
      setEditError(apiError?.errors?.targetDate ?? apiError?.message ?? '목표일을 저장하지 못했습니다.')
    } finally {
      setIsSavingProject(false)
    }
  }

  const handleTargetDateDisplayKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'Enter' && event.key !== 'F2') return
    event.preventDefault()
    startEditingTargetDate(state.status === 'ready' ? state.project.targetDate : null)
  }

  const handleTargetDateEditorKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
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

  const retry = useCallback(() => {
    setState({ status: 'loading' })
    setRequestKey((key) => key + 1)
  }, [])

  useEffect(() => {
    if (projectId === null) return

    let active = true

    void getProject(projectId)
      .then((project) => {
        if (active) setState({ status: 'ready', project })
      })
      .catch((error: unknown) => {
        if (active) setState({ status: 'error', notFound: isNotFound(error) })
      })

    return () => { active = false }
  }, [projectId, requestKey])

  if (state.status === 'loading') {
    return (
      <section className={styles.page} aria-busy="true" aria-labelledby="project-loading-title">
        <p className="sr-only" id="project-loading-title" role="status">프로젝트 상세를 불러오고 있습니다.</p>
        <div className={styles['skeleton-breadcrumb']} aria-hidden="true" />
        <div className={styles['skeleton-heading']} aria-hidden="true" />
        <div className={styles['skeleton-copy']} aria-hidden="true" />
        <div className={styles['skeleton-summary']} aria-hidden="true" />
        <div className={styles['skeleton-list']} aria-hidden="true" />
      </section>
    )
  }

  if (state.status === 'error') {
    return (
      <section className={`${styles.page} ${styles['state-page']}`} aria-labelledby="project-error-title">
        <p className={styles.eyebrow}>Project detail</p>
        <h1 id="project-error-title">
          {state.notFound ? '프로젝트를 찾을 수 없습니다.' : '프로젝트 상세를 불러오지 못했습니다.'}
        </h1>
        <p>
          {state.notFound
            ? '프로젝트 주소를 확인하거나 프로젝트 목록으로 돌아가 주세요.'
            : '연결 상태를 확인한 뒤 다시 불러와 주세요.'}
        </p>
        <div className={styles['state-actions']}>
          {!state.notFound && <button type="button" onClick={retry}>다시 불러오기</button>}
          <Link to="/projects">프로젝트 목록</Link>
        </div>
      </section>
    )
  }

  const { project } = state
  const completionPct = Math.min(100, Math.max(0, project.progress.completionPct))
  const progressStyle = {
    '--project-progress-scale': completionPct / 100,
  } as CSSProperties
  const visibleTasks = statusFilter === 'ALL'
    ? project.tasks
    : project.tasks.filter((task) => task.status === statusFilter)
  const emptyCopy = {
    ALL: {
      title: '등록된 할 일이 없어요.',
      description: '할 일이 추가되면 진행 순서대로 이곳에 표시됩니다.',
    },
    TODO: {
      title: '시작 전인 할 일이 없어요.',
      description: '새로운 작업을 추가하면 이곳에서 확인할 수 있습니다.',
    },
    DOING: {
      title: '등록된 할 일이 없어요.',
      description: '진행을 시작한 할 일이 생기면 이곳에 표시됩니다.',
    },
    DONE: {
      title: '끝낸 할 일이 없어요.',
      description: '완료한 할 일이 생기면 이곳에 차곡차곡 표시됩니다.',
    },
    HOLD: {
      title: '잠시 멈춘 할 일이 없어요.',
      description: '',
    },
  }[statusFilter]

  return (
    <article className={styles.page} aria-labelledby="project-detail-title">
      <nav className={styles.breadcrumb} aria-label="Breadcrumb">
        <Link to="/projects">프로젝트</Link>
        {project.tag && (
            <>
              <IconChevronRight size={14} aria-hidden="true" />
              <span aria-current="page">{project.tag.name}</span>
            </>
        )}
        <IconChevronRight size={14} aria-hidden="true"/>
        <span aria-current="page">{project.name}</span>
      </nav>

      <div className={styles['detail-layout']}>
        <div className={styles['detail-main']}>

      <header className={styles.header}>
      <div className={styles.badges} data-tone={project.id % 4}>
          {project.tag && <span className={styles.tag}>{project.tag.name}</span>}
          <span className={styles['project-status']} data-status={project.status}>{projectStatusLabel[project.status]}</span>
        </div>
        <div className={styles['editable-group']}>
          <h1 id="project-detail-title">
            <InlineEditableText
              value={project.name}
              ariaLabel="프로젝트 제목"
              maxLength={255}
              requiredMessage="프로젝트 이름을 입력해 주세요."
              disabled={isSavingProject}
              onSave={(value) => saveProjectTextField(project, 'name', value)}
              getErrorMessage={(error) => getProjectFieldError(error, 'name')}
            />
          </h1>
        </div>
        <div className={styles['editable-group']}>
          <p>
            <InlineEditableText
              value={project.description}
              emptyText="프로젝트 설명이 아직 없습니다."
              ariaLabel="프로젝트 설명"
              requiredMessage="프로젝트 설명을 입력해 주세요."
              disabled={isSavingProject}
              onSave={(value) => saveProjectTextField(project, 'description', value)}
              getErrorMessage={(error) => getProjectFieldError(error, 'description')}
            />
          </p>
        </div>
      </header>

      <div className={styles['project-delete-actions']}>
        <DeleteIconButton
          label="프로젝트 삭제"
          active={isConfirmingProjectDelete}
          disabled={isDeletingProject}
          onClick={() => {
            setIsConfirmingProjectDelete((current) => !current)
            setProjectDeleteError(null)
          }}
        >
          <span>{isConfirmingProjectDelete ? '취소' : '프로젝트 삭제'}</span>
        </DeleteIconButton>
      </div>
      {isConfirmingProjectDelete && (
        <section className={styles['project-delete-confirmation']} aria-label="프로젝트 삭제 확인">
          <p>프로젝트를 삭제할까요? 연결된 할 일과 메모는 유지됩니다.</p>
          <ActionButton variant="plain" onClick={() => setIsConfirmingProjectDelete(false)} disabled={isDeletingProject}>취소</ActionButton>
          <ActionButton isLoading={isDeletingProject} loadingLabel="삭제 중…" onClick={() => void removeProject(project)}>삭제</ActionButton>
        </section>
      )}
      {projectDeleteError && <p className={styles['delete-error']} role="alert">{projectDeleteError}</p>}

      <section className={styles.summary} aria-labelledby="project-progress-title">
        <span className={styles['journey-rail']} aria-hidden="true" style={progressStyle} />
        <div className={styles['progress-content']}>
          <div className={styles['progress-heading']}>
            <h2 id="project-progress-title">여정 진행</h2>
            <strong>{completionPct}<span>%</span></strong>
          </div>
          <div
            className={styles['progress-track']}
            role="progressbar"
            aria-label="프로젝트 진행률"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={completionPct}
          >
            <span className={styles['progress-fill']} style={progressStyle} />
          </div>
          <p>
            task {project.progress.completedTaskCount} / {project.progress.totalTaskCount} 완료
            <span> · 지금까지 잘 오고 있어요</span>
          </p>
        </div>
        <dl className={styles['target-date']}>
          <div>
            <dt>목표일</dt>
            <dd>
              {isEditingTargetDate ? (
                <div className={styles['date-editor']}>
                  <input
                    type="date"
                    value={editValue}
                    aria-label="프로젝트 목표일"
                    aria-invalid={Boolean(editError)}
                    disabled={isSavingProject}
                    autoFocus
                    onChange={(event) => { setEditValue(event.target.value); setEditError(null) }}
                    onBlur={() => void saveTargetDate(project)}
                    onKeyDown={handleTargetDateEditorKeyDown}
                  />
                  {editError && <p role="alert">{editError}</p>}
                </div>
              ) : (
                <button
                  type="button"
                  className={styles['editable-display']}
                  title="더블 클릭하여 목표일 수정"
                  onDoubleClick={() => startEditingTargetDate(project.targetDate)}
                  onKeyDown={handleTargetDateDisplayKeyDown}
                >
                  {formatTargetDate(project.targetDate)}
                </button>
              )}
            </dd>
          </div>
        </dl>
      </section>
      <section className={styles.tasks} aria-labelledby="project-tasks-title">
        <div className={styles['section-heading']}>
          <div className={styles['section-title']}>
            <h2 id="project-tasks-title">해야 할 일</h2>
            <span>총 {visibleTasks.length}개의 할 일이 있어요</span>
          </div>
          <div className={styles['task-actions']}>
            <button type="button" className={styles['task-filters']} title="필터 · 준비 중"
                    onClick={() => openSelectPicker(filterSelectRef.current)}>
              <IconFilter size={17} aria-hidden="true"/>
              필터
            </button>
            <span id="task-filter-current" className="sr-only">
          {statusFilter === 'ALL' ? '전체' : TASK_STATUS_LABEL[statusFilter]}
        </span>

            <select
                ref={filterSelectRef}
                className={styles['filter-select']}
                value={statusFilter}
                aria-label="task 상태로 필터"
                onChange={(event) => setStatusFilter(event.target.value as TaskFilter)}
            >
              <option value="ALL">전체</option>
              {TASK_STATUS_VALUES.map((status) => (
                  <option value={status} key={status}>{TASK_STATUS_LABEL[status]}</option>
              ))}
            </select>
            <DeleteIconButton
                label={isDeleteMode ? '할 일 삭제 선택 취소' : '할 일 삭제 선택'}
                active={isDeleteMode}
                disabled={isDeletingTasks}
                onClick={() => {
                  if (isDeleteMode) leaveDeleteMode()
                  else setIsDeleteMode(true)
                }}
            >
              {isDeleteMode ? '취소' : <span className="sr-only">Task 삭제 선택</span>}
            </DeleteIconButton>
            {isDeleteMode && (
                <DeleteIconButton
                    label="선택한 Task 삭제"
                    disabled={selectedTaskIds.size === 0 || isDeletingTasks}
                    onClick={() => void removeSelectedTasks()}
                >
                  {isDeletingTasks ? '삭제 중' : <span className="sr-only">선택한 Task 삭제</span>}
                </DeleteIconButton>
            )}
          </div>
        </div>
        {deleteError && <p className={styles['delete-error']} role="alert">{deleteError}</p>}
        <div className={styles['task-list-stack']}>
          <CreateTaskComposer
              projectId={project.id}
              inputRef={taskInputRef}
              onCreated={() => {
                setStatusFilter('ALL')
                setRequestKey((key) => key + 1)
              }}
          />
          <TaskList
            tasks={visibleTasks}
            emptyTitle={emptyCopy.title}
            emptyDescription={emptyCopy.description}
            connected
            isDeleteMode={isDeleteMode}
            selectedTaskIds={selectedTaskIds}
            isDeleting={isDeletingTasks}
            onTaskSelectionChange={toggleTaskSelection}
            onTaskUpdated={() => setRequestKey((key) => key + 1)}
          />
        </div>
      </section>
        </div>

        <aside className={styles['detail-aside']} aria-label="프로젝트 메모">
          <NoteCard key={project.id} projects={[project]} projectId={project.id} />
        </aside>
      </div>
    </article>
  )
}
