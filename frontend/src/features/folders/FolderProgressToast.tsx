import { useEffect, useState } from 'react'
import type { AnimationEvent } from 'react'
import { IconProgressCheck, IconX } from '@tabler/icons-react'
import { getSources } from '../knowledge/knowledgeApi'
import type { SourceCard } from '../knowledge/knowledgeTypes'
import { getFolderTasks } from '../tasks/taskApi'
import type { TaskSummaryResponse } from '../tasks/taskTypes'
import { useSourceStore } from '../../store/sourceStore'
import { useTaskStore } from '../../store/taskStore'
import styles from './FolderProgressToast.module.css'

interface FolderProgressToastProps {
  folderId: number
  folderName: string
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
 * 역할: 폴더에 들어왔을 때 지금까지 쌓인 진척을 한 번 알린다.
 *
 * 쌓인 양으로 말한다. 남은 개수나 늦었다는 신호는 쓰지 않는다(프로젝트 UI 규칙).
 *
 * 세는 범위는 첫 페이지뿐이다. 폴더에 합계를 주는 API가 없어 더 있으면 '+'로만 알린다.
 * 정확한 합계가 필요해지면 세는 일은 서버로 옮긴다.
 *
 * 빈 항목은 표시하지 않고, 둘 다 비어 있으면 배너도 숨긴다. 요청 실패는 별도로 알린다.
 *
 * 닫을 때까지 떠 있으므로 그 사이의 완료·읽음·삭제가 숫자에 들어와야 한다. 받아 둔 페이지는
 * "무엇이 이 폴더에 있었는가"만 정하고, 지금 어떤 상태인지는 taskStore·sourceStore에서
 * 겹쳐 읽는다. 두 스토어가 각각 최신 값을 갖는 자리라 다시 받아올 필요가 없다.
 *
 * 받아 온 것을 먼저 스토어에 넣어 두는 이유가 여기 있다. 그래야 "스토어에 없다"가
 * "아직 모르는 항목"이 아니라 "지워진 항목"이라는 한 가지 뜻이 된다.
 */
export default function FolderProgressToast({ folderId, folderName }: FolderProgressToastProps) {
  const [taskPage, setTaskPage] = useState<Page<TaskSummaryResponse> | null>(null)
  const [sourcePage, setSourcePage] = useState<Page<SourceCard> | null>(null)
  const tasksById = useTaskStore((state) => state.byId)
  const upsertTasks = useTaskStore((state) => state.upsert)
  const readAtById = useSourceStore((state) => state.readAtById)
  const upsertSources = useSourceStore((state) => state.upsert)
  const [failed, setFailed] = useState<string[]>([])
  const [isOpen, setIsOpen] = useState(false)
  /** 사라지는 애니메이션이 끝날 때까지는 남아 있어야 해서 닫힘을 두 단계로 나눈다. */
  const [isLeaving, setIsLeaving] = useState(false)

  useEffect(() => {
    const controller = new AbortController()

    void Promise.allSettled([
      getFolderTasks(folderId, { size: COUNT_SIZE }),
      getSources(folderId, { size: COUNT_SIZE }),
    ]).then(([taskResult, sourceResult]) => {
      if (controller.signal.aborted) return

      const reasons: string[] = []

      if (taskResult.status === 'fulfilled') {
        const page = taskResult.value
        // 목록 응답에는 folderId가 없다. 어느 폴더를 불렀는지는 이 화면이 안다.
        upsertTasks(page.items.map((task) => ({ ...task, folderId })))
        setTaskPage({ items: page.items, hasMore: page.hasNext })
      } else {
        reasons.push('할 일을 불러오지 못했어요.')
      }

      if (sourceResult.status === 'fulfilled') {
        const page = sourceResult.value
        upsertSources(page.items)
        setSourcePage({ items: page.items, hasMore: page.hasNext })
      } else {
        reasons.push('링크를 불러오지 못했어요.')
      }

      setFailed(reasons)
      setIsOpen(true)
    })

    return () => {
      controller.abort()
      setIsOpen(false)
      setIsLeaving(false)
      setTaskPage(null)
      setSourcePage(null)
      setFailed([])
    }
  }, [folderId, upsertSources, upsertTasks])

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

  const livingSources = sourcePage?.items.filter((source) => source.sourceId in readAtById) ?? []
  const sources: Progress | null = sourcePage && {
    done: livingSources.filter((source) => readAtById[source.sourceId] !== null).length,
    total: livingSources.length,
    hasMore: sourcePage.hasMore,
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
  if (sources && sources.total > 0) parts.push({ name: '링크', count: label(sources), unit: '읽음' })

  // 표시할 항목과 오류가 모두 없으면 빈 폴더에는 플로팅 독을 띄우지 않는다.
  if (parts.length === 0 && failed.length === 0) return null
  const allTasksDone = isComplete(tasks)
  const allSourcesRead = isComplete(sources)
  const completionMessage = allTasksDone && allSourcesRead
    ? '할 일을 모두 완료하고 링크도 모두 읽었어요'
    : allTasksDone
      ? '할 일 모두 완료했어요'
      : allSourcesRead
        ? '링크 모두 읽었어요'
        : null
  const completionState = `${allTasksDone}-${allSourcesRead}`

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
