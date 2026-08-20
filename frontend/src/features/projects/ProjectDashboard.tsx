import { useMemo } from 'react'
import {
  IconCalendarDue,
  IconFilter,
  IconFolders,
  IconPlus,
  IconTags,
  IconTargetArrow,
} from '@tabler/icons-react'
import type { Project } from './projectTypes'
import styles from './ProjectDashboard.module.css'

export type ProjectLoadStatus = 'idle' | 'loading' | 'ready' | 'error'

interface ProjectDashboardProps {
  projects: Project[]
  status: ProjectLoadStatus
  onRetry: () => void
  onOpenCreate: () => void
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
})

function formatTargetDate(targetDate: string) {
  return dateFormatter.format(new Date(`${targetDate}T00:00:00`))
}

function ProjectCard({ project, index }: { project: Project; index: number }) {
  const routeTone = [styles['is-clay'], styles['is-sky'], styles['is-pale']][index % 3]

  return (
    <article className={styles['project-card']}>
      <span className={`${styles['project-route']} ${routeTone}`} aria-hidden="true" />
      <div className={styles['project-card-heading']}>
        <div className={styles['project-card-title-group']}>
          {project.tag && <span className={styles['project-tag']}>{project.tag.name}</span>}
          <h3>{project.name}</h3>
        </div>
        {project.targetDate && (
          <span className={styles['project-target-date']}>
            <IconCalendarDue size={15} stroke={1.8} aria-hidden="true" />
            <span>
              <span className="sr-only">목표일 </span>
              {formatTargetDate(project.targetDate)}
            </span>
          </span>
        )}
      </div>
      <p className={styles['project-description']}>
        {project.description || '프로젝트 설명이 아직 없습니다.'}
      </p>
      <div className={styles['project-card-footer']}>
        <span>목표일</span>
        <strong>
          {project.targetDate ? formatTargetDate(project.targetDate) : '설정하지 않음'}
        </strong>
      </div>
    </article>
  )
}

export default function ProjectDashboard({
  projects,
  status,
  onRetry,
  onOpenCreate,
}: ProjectDashboardProps) {
  const metrics = useMemo(
    () => [
      {
        label: '총 프로젝트',
        value: projects.length,
        icon: IconFolders,
        tone: styles['is-blue'],
      },
        {
            label: '진행 중',
            value: projects.filter((project) => project.tag !== null).length,
            icon: IconTags,
            tone: styles['is-green'],
        },
      {
        label: '완료',
        value: projects.filter((project) => project.targetDate !== null).length,
        icon: IconTargetArrow,
        tone: styles['is-orange'],
      }
    ],
    [projects],
  )

  const upcomingProjects = useMemo(
    () =>
      projects
        .filter((project): project is Project & { targetDate: string } => Boolean(project.targetDate))
        .sort((a, b) => a.targetDate.localeCompare(b.targetDate))
        .slice(0, 3),
    [projects],
  )

  return (
    <div className={styles['dashboard-layout']}>
      <section className={styles['project-dashboard']} aria-labelledby="project-dashboard-title">
        <header className={styles['dashboard-heading']}>
          <div>
            <h1 id="project-dashboard-title">내 프로젝트</h1>
            <p>현재 진행 중인 프로젝트 현황입니다.</p>
          </div>
          <div className={styles['dashboard-actions']}>
            <button type="button" className={styles['secondary-action']} disabled title="필터 · 준비 중">
              <IconFilter size={17} aria-hidden="true" />
              필터
            </button>
            <button type="button" className={styles['primary-action']} onClick={onOpenCreate}>
              <IconPlus size={18} aria-hidden="true" />
              새 프로젝트
            </button>
          </div>
        </header>

        {status === 'loading' || status === 'idle' ? (
          <div className={styles['dashboard-state']} role="status">
            <span className={styles['dashboard-state-mark']} aria-hidden="true" />
            <p>프로젝트를 불러오고 있습니다.</p>
          </div>
        ) : status === 'error' ? (
          <div className={styles['dashboard-state']}>
            <p>프로젝트 목록을 불러오지 못했습니다.</p>
            <button type="button" onClick={onRetry}>다시 불러오기</button>
          </div>
        ) : (
          <>
            <section className={styles['project-metrics']} aria-label="프로젝트 요약">
              {metrics.map(({ label, value, icon: Icon, tone }) => (
                <article className={styles['metric-card']} key={label}>
                  <span className={`${styles['metric-icon']} ${tone}`} aria-hidden="true">
                    <Icon size={24} stroke={1.7} />
                  </span>
                  <div>
                    <p>{label}</p>
                    <strong>{value}</strong>
                  </div>
                </article>
              ))}
            </section>

            <div className={styles['project-section-heading']}>
              <h2>최근 활동 프로젝트</h2>
              <span>{projects.length}개</span>
            </div>

            {projects.length === 0 ? (
              <div className={styles['projects-empty']}>
                <IconFolders size={28} stroke={1.5} aria-hidden="true" />
                <h3>프로젝트를 시작할 준비가 되었습니다.</h3>
                <p>새 프로젝트를 만들면 이곳에서 한눈에 확인할 수 있습니다.</p>
              </div>
            ) : (
              <div className={styles['project-grid']}>
                {projects.map((project, index) => (
                  <ProjectCard key={project.id} project={project} index={index} />
                ))}
              </div>
            )}
          </>
        )}
      </section>

      <aside className={styles['dashboard-aside']} aria-labelledby="upcoming-targets-title">
        <h2 id="upcoming-targets-title">다가오는 목표일</h2>
        {status === 'ready' && upcomingProjects.length > 0 ? (
          <ul className={styles['upcoming-list']}>
            {upcomingProjects.map((project) => (
              <li key={project.id}>
                <span className={styles['upcoming-date']} aria-hidden="true">
                  <strong>{new Date(`${project.targetDate}T00:00:00`).getDate()}</strong>
                  <span>
                    {new Intl.DateTimeFormat('ko-KR', { month: 'short' }).format(
                      new Date(`${project.targetDate}T00:00:00`),
                    )}
                  </span>
                </span>
                <div>
                  <strong>{project.name}</strong>
                  <span>목표일 {formatTargetDate(project.targetDate)}</span>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles['upcoming-empty']}>표시할 목표일이 아직 없습니다.</p>
        )}
      </aside>
    </div>
  )
}
