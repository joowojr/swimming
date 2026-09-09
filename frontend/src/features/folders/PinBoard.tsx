import ModeToggle from '../../components/ModeToggle'
import TaskFilterMenu from '../../components/TaskFilterMenu'
import { useAuthStore } from '../../store/authStore'
import { usePinboardViewStore } from '../../store/pinboardViewStore'
import DailyPlanner from '../plans/DailyPlanner.tsx'
import ContinueSessionWidget from '../sessions/ContinueSessionWidget'
import NoteCard from '../note/NoteCard.tsx'
import type { Folder, FolderLoadStatus } from './folderTypes.ts'
import TaskMatrix from '../tasks/TaskMatrix'
import styles from './PinBoard.module.css'

interface PinBoardProps {
  folders: Folder[]
  status: FolderLoadStatus
  onRetry: () => void
}

const PLANNER_VIEW_OPTIONS = [
  { value: 'daily', label: '캘린더' },
  { value: 'matrix', label: '매트릭스' },
] as const

/*
const dateFormatter = new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' })

function formatTargetDate(targetDate: string) {
  return dateFormatter.format(new Date(`${targetDate}T00:00:00`))
}
*/

export default function PinBoard({
  folders,
  status,
  onRetry,
}: PinBoardProps) {
  const { user } = useAuthStore()
  // 닉네임이 비어 있으면 이메일 아이디를 대신 부른다.
  const displayName = user?.nickname || user?.email?.split('@')[0]
  const plannerView = usePinboardViewStore((state) => state.plannerView)
  const setPlannerView = usePinboardViewStore((state) => state.setPlannerView)
  const matrixFilter = usePinboardViewStore((state) => state.matrixFilter)
  const setMatrixFilter = usePinboardViewStore((state) => state.setMatrixFilter)
  /*
  const upcomingProjects = useMemo(
    () =>
      folders
        .filter((folder): folder is Project & { targetDate: string } => Boolean(folder.targetDate))
        .sort((a, b) => a.targetDate.localeCompare(b.targetDate))
        .slice(0, 3),
    [folders],
  )
  */

  return (
    <div className={styles['dashboard-layout']}>
      <section className={styles['folder-dashboard']} aria-labelledby="folder-dashboard-title">
        <header className={styles['dashboard-heading']}>
          <div>
            <h2 id="folder-dashboard-title">
              {displayName ? `안녕하세요 ${displayName}님` : '안녕하세요'}
            </h2>
            <p>오늘은 무엇부터 시작해볼까요?</p>
          </div>
          <div className={styles['dashboard-actions']}>
            {/*<div className={styles['mode-toggle']} aria-label="핀보드 보기 모드">*/}
            {/*  <button type="button" className={styles['mode-toggle-active']} aria-pressed="true">루틴</button>*/}
            {/*  <button type="button" aria-pressed="false" disabled>가볍게</button>*/}
            {/*</div>*/}
          </div>
        </header>

        {status === 'loading' || status === 'idle' ? (
          <div className={styles['dashboard-state']} role="status">
            <span className={styles['dashboard-state-mark']} aria-hidden="true" />
            <p>폴더를 불러오고 있습니다.</p>
          </div>
        ) : status === 'error' ? (
          <div className={styles['dashboard-state']}>
            <p>폴더 목록을 불러오지 못했습니다.</p>
            <button type="button" onClick={onRetry}>다시 불러오기</button>
          </div>
        ) : (
            <>
              {/*폴더 정리 표*/}
            {/*<section className={styles['folder-metrics']} aria-label="폴더 요약">*/}
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
                  <div className={styles['planner-area']}>
                    <div className={styles['planner-controls']}>
                      <ModeToggle
                        className={styles['planner-toggle']}
                        ariaLabel="Task 보기 방식"
                        options={PLANNER_VIEW_OPTIONS}
                        value={plannerView}
                        onChange={setPlannerView}
                      />
                      {plannerView === 'matrix' && (
                        <TaskFilterMenu
                          value={matrixFilter}
                          onChange={setMatrixFilter}
                          showFlags={false}
                          triggerClassName={styles['planner-filter']}
                        />
                      )}
                    </div>
                    {plannerView === 'daily'
                      ? <DailyPlanner/>
                      : <TaskMatrix statusFilter={matrixFilter.status}/>}
                  </div>
                </div>

                <NoteCard folders={folders} />
              </div>
            </>
        )}
      </section>

      {/*
      <aside className={styles['dashboard-aside']} aria-labelledby="upcoming-targets-title">
        <h2 id="upcoming-targets-title">다가오는 목표일</h2>
        {status === 'ready' && upcomingProjects.length > 0 ? (
            <ul className={styles['upcoming-list']}>
              {upcomingProjects.map((folder) => (
                  <li key={folder.id}>
                <span className={styles['upcoming-date']} aria-hidden="true">
                  <strong>{new Date(`${folder.targetDate}T00:00:00`).getDate()}</strong>
                  <span>
                    {new Intl.DateTimeFormat('ko-KR', { month: 'short' }).format(
                      new Date(`${folder.targetDate}T00:00:00`),
                    )}
                  </span>
                </span>
                <div>
                  <strong>{folder.name}</strong>
                  <span>목표일 {formatTargetDate(folder.targetDate)}</span>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className={styles['upcoming-empty']}>표시할 목표일이 아직 없습니다.</p>
        )}
      </aside>
      */}
    </div>
  )
}
