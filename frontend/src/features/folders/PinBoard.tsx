import { useState } from 'react'
import { IconPlus } from '@tabler/icons-react'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import ModeToggle from '../../components/ModeToggle'
import TaskFilterMenu from '../../components/TaskFilterMenu'
import { useAuthStore } from '../../store/authStore'
import { usePinboardViewStore } from '../../store/pinboardViewStore'
import DailyPlanner from '../calendar/DailyPlanner.tsx'
import ContinueSessionWidget from '../sessions/ContinueSessionWidget'
import type { FolderLoadStatus } from './folderTypes.ts'
import TaskMatrix from '../tasks/TaskMatrix'
import UpdateNoticeCard from '../updates/UpdateNoticeCard'
import styles from './PinBoard.module.css'

interface PinBoardProps {
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
  // 여는 버튼이 보기 전환 줄에 있어 열림 상태만 여기 둔다. 무엇을 담을지는 날짜를 아는
  // DailyPlanner가 정한다.
  const [isTaskPickerOpen, setIsTaskPickerOpen] = useState(false)
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
            <ContinueSessionWidget/>
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
          <div className={styles['home-main']}>
            <div className={styles['planner-area']}>
              <div className={styles['planner-controls']}>
                <ModeToggle
                  className={styles['planner-toggle']}
                  ariaLabel="Task 보기 방식"
                  options={PLANNER_VIEW_OPTIONS}
                  value={plannerView}
                  onChange={setPlannerView}
                />
                {plannerView === 'matrix' ? (
                  <TaskFilterMenu
                    value={matrixFilter}
                    onChange={setMatrixFilter}
                    showFlags={false}
                    triggerClassName={styles['planner-filter']}
                  />
                ) : (
                  <ModalTriggerButton
                    dialogId="task-picker-dialog"
                    icon={<IconPlus size={15} aria-hidden="true" />}
                    isOpen={isTaskPickerOpen}
                    aria-label="할 일 추가"
                    onClick={() => setIsTaskPickerOpen(true)}
                  >
                    <span>할 일 추가</span>
                  </ModalTriggerButton>
                )}
              </div>
              {plannerView === 'daily'
                ? (
                  <DailyPlanner
                    isPickerOpen={isTaskPickerOpen}
                    onPickerClose={() => setIsTaskPickerOpen(false)}
                  />
                )
                : <TaskMatrix statusFilter={matrixFilter.status}/>}
            </div>
          </div>
        )}
      </section>

      {/* 배포마다 새 기능을 잠시 알린다. 왼쪽 아래 플로팅 독 옆에 떠 있어 자리를 차지하지 않는다. */}
      <UpdateNoticeCard />

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
