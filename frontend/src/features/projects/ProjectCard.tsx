import { IconCalendarDue } from '@tabler/icons-react'
import { Link } from 'react-router-dom'
import type { Project } from './projectTypes'
import styles from './ProjectCard.module.css'

interface ProjectCardProps {
  project: Project
  index: number
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
})

function formatTargetDate(targetDate: string) {
  return dateFormatter.format(new Date(`${targetDate}T00:00:00`))
}

export default function ProjectCard({ project, index }: ProjectCardProps) {
  const routeTone = [styles['is-clay'], styles['is-sky'], styles['is-pale']][index % 3]

  return (
    <Link
      className={styles.card}
      to={`/projects/${project.id}`}
      aria-label={`${project.name} 상세 보기`}
    >
      <span className={`${styles.route} ${routeTone}`} aria-hidden="true" />
      <div className={styles.heading}>
        <div className={styles['title-group']}>
          {project.tag && <span className={styles.tag}>{project.tag.name}</span>}
          <h3>{project.name}</h3>
        </div>
        {project.targetDate && (
          <span className={styles['target-date']}>
            <IconCalendarDue size={15} stroke={1.8} aria-hidden="true" />
            <span>
              <span className="sr-only">목표일 </span>
              {formatTargetDate(project.targetDate)}
            </span>
          </span>
        )}
      </div>
      <p className={styles.description}>
        {project.description || '프로젝트 설명이 아직 없습니다.'}
      </p>
      <div className={styles.footer}>
        <span>목표일</span>
        <strong>
          {project.targetDate ? formatTargetDate(project.targetDate) : '설정하지 않음'}
        </strong>
      </div>
    </Link>
  )
}
