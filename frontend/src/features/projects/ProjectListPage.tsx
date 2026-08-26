import { IconFilter, IconFolders, IconPlus, IconTags } from '@tabler/icons-react'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import ProjectCard from './ProjectCard'
import type { Project } from './projectTypes'
import type { ProjectLoadStatus } from './projectTypes'
import styles from './ProjectListPage.module.css'

interface ProjectListPageProps {
  projects: Project[]
  status: ProjectLoadStatus
  onRetry: () => void
  onOpenCreate: () => void
  onOpenTagManage: () => void
}

export default function ProjectListPage({
  projects,
  status,
  onRetry,
  onOpenCreate,
  onOpenTagManage,
}: ProjectListPageProps) {
  return (
    <section className={styles.page} aria-labelledby="projects-page-title">
      <header className={styles.heading}>
        <div>
          <h1 id="projects-page-title">프로젝트</h1>
          <p>진행 중인 프로젝트를 한곳에서 확인합니다.</p>
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.secondary} disabled title="필터 · 준비 중">
            <IconFilter size={17} aria-hidden="true" />
            필터
          </button>
          <button type="button" className={styles.secondary} onClick={onOpenTagManage}>
            <IconTags size={17} aria-hidden="true" />
            태그 관리
          </button>
          <ModalTriggerButton
            dialogId="create-project-dialog"
            icon={<IconPlus size={18} aria-hidden="true" />}
            onClick={onOpenCreate}
          >
            새 프로젝트
          </ModalTriggerButton>
        </div>
      </header>

      {status === 'loading' || status === 'idle' ? (
        <div className={styles.state} role="status">
          <span className={styles['state-mark']} aria-hidden="true" />
          <p>프로젝트를 불러오고 있습니다.</p>
        </div>
      ) : status === 'error' ? (
        <div className={styles.state}>
          <p>프로젝트 목록을 불러오지 못했습니다.</p>
          <button type="button" onClick={onRetry}>다시 불러오기</button>
        </div>
      ) : (
        <>
          <div className={styles['section-heading']}>
            <h2>전체 프로젝트</h2>
            <span>{projects.length}개</span>
          </div>
          {projects.length === 0 ? (
            <div className={styles.empty}>
              <IconFolders size={28} stroke={1.5} aria-hidden="true" />
              <h3>프로젝트를 시작할 준비가 되었습니다.</h3>
              <p>새 프로젝트를 만들면 이곳에서 한눈에 확인할 수 있습니다.</p>
            </div>
          ) : (
            <div className={styles.grid}>
              {projects.map((project) => (
                <ProjectCard key={project.id} project={project} />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
