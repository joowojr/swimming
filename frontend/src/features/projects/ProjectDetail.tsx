import { useCallback, useEffect, useRef, useState } from 'react'
import { IconChevronRight, IconPlus } from '@tabler/icons-react'
import { Link } from 'react-router-dom'
import type { CSSProperties } from 'react'
import type { ApiError } from '../../api/client'
import CreateTaskComposer from '../tasks/CreateTaskComposer'
import { TASK_STATUS_LABEL, TASK_STATUS_VALUES } from '../tasks/taskLabels'
import type { TaskStatus } from '../tasks/taskTypes'
import { getProject } from './projectApi'
import type { ProjectDetail as ProjectDetailData, ProjectStatus } from './projectTypes'
import TaskList from './TaskList'
import styles from './ProjectDetail.module.css'

interface ProjectDetailProps {
  projectId: number | null
}

type DetailState =
  | { status: 'loading' }
  | { status: 'ready'; project: ProjectDetailData }
  | { status: 'error'; notFound: boolean }

type TaskFilter = 'ALL' | TaskStatus

const taskFilters: Array<{ value: TaskFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  ...TASK_STATUS_VALUES.map((status) => ({
    value: status,
    label: TASK_STATUS_LABEL[status],
  })),
]

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

export default function ProjectDetail({ projectId }: ProjectDetailProps) {
  const [requestKey, setRequestKey] = useState(0)
  const [state, setState] = useState<DetailState>(
    projectId === null ? { status: 'error', notFound: true } : { status: 'loading' },
  )
  const [taskFilter, setTaskFilter] = useState<TaskFilter>('ALL')
  const taskInputRef = useRef<HTMLInputElement>(null)

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
  const visibleTasks = taskFilter === 'ALL'
    ? project.tasks
    : project.tasks.filter((task) => task.status === taskFilter)
  const emptyCopy = {
    ALL: {
      title: '등록된 task가 없습니다.',
      description: 'task가 추가되면 진행 순서대로 이곳에 표시됩니다.',
    },
    TODO: {
      title: '시작 전인 task가 없습니다.',
      description: '새로운 task를 추가하면 이곳에서 확인할 수 있습니다.',
    },
    DOING: {
      title: '하는 중인 task가 없습니다.',
      description: '진행을 시작한 task가 생기면 이곳에 표시됩니다.',
    },
    DONE: {
      title: '끝낸 task가 없습니다.',
      description: '완료한 task가 생기면 이곳에 차곡차곡 표시됩니다.',
    },
    HOLD: {
      title: '잠시 멈춘 task가 없습니다.',
      description: '잠시 멈춘 task가 생기면 이곳에서 다시 확인할 수 있습니다.',
    },
  }[taskFilter]

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

      <header className={styles.header}>
      <div className={styles.badges}>
          {project.tag && <span className={styles.tag}>{project.tag.name}</span>}
          <span className={styles['project-status']}>{projectStatusLabel[project.status]}</span>
        </div>
        <h1 id="project-detail-title">{project.name}</h1>
        <p>{project.description || '프로젝트 설명이 아직 없습니다.'}</p>
      </header>

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
            <dd>{formatTargetDate(project.targetDate)}</dd>
          </div>
        </dl>
      </section>
      <section className={styles.tasks} aria-labelledby="project-tasks-title">
        <div className={styles['section-heading']}>
          <div className={styles['section-title']}>
            <h2 id="project-tasks-title">해야 할 일</h2>
            <span>task {visibleTasks.length}개 표시</span>
          </div>
          <div className={styles['task-actions']}>
            <div className={styles['task-filters']} role="group" aria-label="Task 상태 필터">
              {taskFilters.map((filter) => (
                <button
                  type="button"
                  key={filter.value}
                  aria-pressed={taskFilter === filter.value}
                  onClick={() => setTaskFilter(filter.value)}
                >
                  {filter.label}
                </button>
              ))}
            </div>
            <button
              type="button"
              className={styles['create-task-button']}
              onClick={() => taskInputRef.current?.focus()}
            >
              <IconPlus size={16} aria-hidden="true" />
              task
            </button>
          </div>
        </div>
        <div className={styles['task-list-stack']}>
          <CreateTaskComposer
            projectId={project.id}
            inputRef={taskInputRef}
            onCreated={() => {
              setTaskFilter('ALL')
              setRequestKey((key) => key + 1)
            }}
          />
          <TaskList
            tasks={visibleTasks}
            emptyTitle={emptyCopy.title}
            emptyDescription={emptyCopy.description}
            connected
            onTaskUpdated={() => setRequestKey((key) => key + 1)}
          />
        </div>
      </section>
    </article>
  )
}
