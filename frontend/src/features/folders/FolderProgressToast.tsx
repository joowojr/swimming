import { useEffect, useState } from 'react'
import type { AnimationEvent } from 'react'
import { IconProgressCheck, IconX } from '@tabler/icons-react'
import { getFolderTasks } from '../tasks/taskApi'
import type { TaskSummaryResponse } from '../tasks/taskTypes'
import { useTaskStore } from '../../store/taskStore'
import styles from './FolderProgressToast.module.css'

interface FolderProgressToastProps {
  folderId: number
  folderName: string
  sourceCount: number
}

/**
 * 한 번에 세어 볼 분량. 서버의 커서 페이지 상한과 같다.
 * 이보다 많으면 total에 '+'를 붙여 더 있다는 것만 알린다.
 */
const COUNT_SIZE = 50

/** 폴더에 들어온 순간 받아 둔 한 페이지. 어떤 항목이 이 폴더에 속하는지를 정한다. */
interface Page<T> {
  items: T[]
  hasMore: boolean
}

interface Progress {
  done: number
  total: number
  hasMore: boolean
}

function label(progress: Progress) {
  return `${progress.done}/${progress.total}${progress.hasMore ? '+' : ''}`
}

function isComplete(progress: Progress | null) {
  return progress !== null && progress.total > 0 && progress.done === progress.total && !progress.hasMore
}

/**
 * 폴더에 쌓인 할 일 진척과 저장된 링크 수를 알린다.
 * 할 일은 첫 페이지를 스토어의 최신 상태와 겹쳐 읽고, 링크 수는 폴더 상세 응답을 사용한다.
 */
export default function FolderProgressToast({ folderId, folderName, sourceCount }: FolderProgressToastProps) {
  const [taskPage, setTaskPage] = useState<Page<TaskSummaryResponse> | null>(null)
  const tasksById = useTaskStore((state) => state.byId)
  const upsertTasks = useTaskStore((state) => state.upsert)
  const [failed, setFailed] = useState<string[]>([])
  const [isOpen, setIsOpen] = useState(false)
  /** 사라지는 애니메이션이 끝날 때까지는 남아 있어야 해서 닫힘을 두 단계로 나눈다. */
  const [isLeaving, setIsLeaving] = useState(false)

  useEffect(() => {
    const controller = new AbortController()

    void getFolderTasks(folderId, { size: COUNT_SIZE }).then((page) => {
      if (controller.signal.aborted) return
      upsertTasks(page.items.map((task) => ({ ...task, folderId })))
      setTaskPage({ items: page.items, hasMore: page.hasNext })
      setIsOpen(true)
    }).catch(() => {
      if (controller.signal.aborted) return
      setFailed(['할 일을 불러오지 못했어요.'])
      setIsOpen(true)
    })

    return () => {
      controller.abort()
      setIsOpen(false)
      setIsLeaving(false)
      setTaskPage(null)
      setFailed([])
    }
  }, [folderId, upsertTasks])

  // 스토어에서 빠진 것은 지워진 항목이라 분모에서도 뺀다. 남은 것의 상태는 스토어가 정한다.
  // 다른 폴더로 옮긴 task도 여기서 빠진다. 이 폴더의 진척이 아니게 됐기 때문이다.
  const livingTasks = taskPage?.items.filter((task) => (
    task.id in tasksById && tasksById[task.id].folderId === folderId
  )) ?? []
  const tasks: Progress | null = taskPage && {
    done: livingTasks.filter((task) => tasksById[task.id].status === 'DONE').length,
    total: livingTasks.length,
    hasMore: taskPage.hasMore,
  }

  if (!isOpen) return null

  const close = () => {
    // 모션을 끈 사용자는 애니메이션이 없어 animationend도 오지 않는다. 그 자리에서 닫는다.
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setIsOpen(false)
      return
    }
    setIsLeaving(true)
  }

  const handleAnimationEnd = (event: AnimationEvent<HTMLDivElement>) => {
    // 아이콘과 글줄의 애니메이션도 여기로 올라온다. 배너 자신의 것만 본다.
    if (!isLeaving || event.target !== event.currentTarget) return
    setIsOpen(false)
    setIsLeaving(false)
  }

  // 한 줄로 잇는다. 줄을 나누면 어느 쪽이 제목인지 흐려진다.
  // 문자열로 합치지 않고 조각으로 두어 개수만 굵게 세울 수 있게 한다.
  const parts: Array<{ name: string; count: string; unit: string }> = []
  if (tasks && tasks.total > 0) parts.push({ name: '할 일', count: label(tasks), unit: '완료' })
  if (sourceCount > 0) parts.push({ name: '링크', count: `${sourceCount}개`, unit: '저장' })

  // 표시할 항목과 오류가 모두 없으면 빈 폴더에는 플로팅 독을 띄우지 않는다.
  if (parts.length === 0 && failed.length === 0) return null
  const allTasksDone = isComplete(tasks)
  const completionMessage = allTasksDone ? '할 일 모두 완료했어요' : null
  const completionState = `${allTasksDone}`

  return (
    <div
      className={`${styles.toast} ${isLeaving ? styles.leaving : ''}`}
      data-complete={completionMessage ? 'true' : undefined}
      role="status"
      onAnimationEnd={handleAnimationEnd}
    >
      <span key={completionState} className={styles.icon} aria-hidden="true">
        {completionMessage ? (
          <svg className={styles['complete-check']} width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M5 12l4 4L19 6" pathLength="1" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ) : <IconProgressCheck size={24} stroke={1.8} />}
      </span>

      <div className={styles.lines}>
        <p className={styles.name}>{folderName}</p>
        {parts.length > 0 && (
          <p className={styles.progress}>
            {parts.map((part, index) => (
              <span key={part.name}>
                {index > 0 && <span className={styles.separator}> · </span>}
                {part.name}{' '}
                <strong className={styles.count}>{part.count}</strong>{' '}
                {part.unit}
              </span>
            ))}
          </p>
        )}
        {completionMessage && (
          <p key={completionState} className={styles['complete-message']}>{completionMessage}</p>
        )}
        {failed.map((reason) => <p className={styles.failure} key={reason}>{reason}</p>)}
      </div>

      <button type="button" className={styles.close} aria-label="알림 닫기" onClick={close}>
        <IconX size={16} stroke={1.8} aria-hidden="true" />
      </button>
    </div>
  )
}
