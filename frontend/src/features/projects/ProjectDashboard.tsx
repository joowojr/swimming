import { useMemo } from 'react'
import {
  IconFilter,
  IconPlus,
} from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import DailyPlanSection from '../plans/DailyPlanSection'
import ContinueSessionWidget from '../sessions/ContinueSessionWidget'
import MemoCard from '../memo/MemoCard'
import type { Project } from './projectTypes'
import styles from './ProjectDashboard.module.css'

export type ProjectLoadStatus = 'idle' | 'loading' | 'ready' | 'error'

interface ProjectDashboardProps {
  projects: Project[]
  status: ProjectLoadStatus
  onRetry: () => void
  onOpenCreate: () => void
  onOrganizeMemo: (text: string) => Promise<void>;
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' })

function formatTargetDate(targetDate: string) {
  return dateFormatter.format(new Date(`${targetDate}T00:00:00`))
}

export default function ProjectDashboard({
  projects,
  status,
  onRetry,
  onOpenCreate,
  onOrganizeMemo,
}: ProjectDashboardProps) {
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
            <h2 id="project-dashboard-title">안녕하세요</h2>
            <p>현재 진행 중인 프로젝트 현황입니다.</p>
          </div>
          <div className={styles['dashboard-actions']}>
            <ActionButton
              icon={<IconPlus size={18} aria-hidden="true" />}
              onClick={onOpenCreate}
            >
              새 프로젝트
            </ActionButton>
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
              {/*프로젝트 정리 표*/}
            {/*<section className={styles['project-metrics']} aria-label="프로젝트 요약">*/}
            {/*  {metrics.map(({ label, value, icon: Icon, tone }) => (*/}
            {/*    <article className={styles['metric-card']} key={label}>*/}
            {/*      <span className={`${styles['metric-icon']} ${tone}`} aria-hidden="true">*/}
            {/*        <Icon size={24} stroke={1.7} />*/}
            {/*      </span>*/}
            {/*      <div>*/}
            {/*        <p>{label}</p>*/}
            {/*        <strong>{value}</strong>*/}
            {/*      </div>*/}
            {/*    </article>*/}
            {/*  ))}*/}
            {/*</section>*/}

              <div className={styles['home-grid']}>
                <div className={styles['home-main']}>
                  <ContinueSessionWidget/>
                  <DailyPlanSection projects={projects}/>
                </div>

                <MemoCard onOrganize={onOrganizeMemo} />
              </div>
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
