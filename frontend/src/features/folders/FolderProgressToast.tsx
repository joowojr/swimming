import { useEffect, useState } from 'react'
import type { AnimationEvent } from 'react'
import { IconProgressCheck, IconX } from '@tabler/icons-react'
import { getSources } from '../knowledge/knowledgeApi'
import { getFolderTasks } from '../tasks/taskApi'
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

interface Progress {
  done: number
  total: number
  hasMore: boolean
}

function label(progress: Progress) {
  return `${progress.done}/${progress.total}${progress.hasMore ? '+' : ''}`
}

/**
 * 역할: 폴더에 들어왔을 때 지금까지 쌓인 진척을 한 번 알린다.
 *
 * 쌓인 양으로 말한다. 남은 개수나 늦었다는 신호는 쓰지 않는다(프로젝트 UI 규칙).
 *
 * 세는 범위는 첫 페이지뿐이다. 폴더에 합계를 주는 API가 없어 더 있으면 '+'로만 알린다.
 * 정확한 합계가 필요해지면 세는 일은 서버로 옮긴다.
 *
 * 아직 확인 중이라 스스로 사라지지 않고, 셀 것이 없거나 요청이 실패해도 그 사실을 띄운다.
 * 조용히 사라지면 "안 뜬다"와 "띄울 것이 없다"를 구분할 수 없다.
 */
export default function FolderProgressToast({ folderId, folderName }: FolderProgressToastProps) {
  const [tasks, setTasks] = useState<Progress | null>(null)
  const [sources, setSources] = useState<Progress | null>(null)
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
        setTasks({
          done: page.items.filter((task) => task.status === 'DONE').length,
          total: page.items.length,
          hasMore: page.hasNext,
        })
      } else {
        reasons.push('할 일을 불러오지 못했어요.')
      }

      if (sourceResult.status === 'fulfilled') {
        const page = sourceResult.value
        setSources({
          done: page.items.filter((source) => source.readAt !== null).length,
          total: page.items.length,
          hasMore: page.hasNext,
        })
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
      setTasks(null)
      setSources(null)
      setFailed([])
    }
  }, [folderId])

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
  if (tasks) parts.push({ name: '할 일', count: label(tasks), unit: '완료' })
  if (sources) parts.push({ name: '링크', count: label(sources), unit: '읽음' })

  return (
    <div
      className={`${styles.toast} ${isLeaving ? styles.leaving : ''}`}
      role="status"
      onAnimationEnd={handleAnimationEnd}
    >
      <span className={styles.icon} aria-hidden="true">
        <IconProgressCheck size={24} stroke={1.8} />
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
        {failed.map((reason) => <p className={styles.failure} key={reason}>{reason}</p>)}
      </div>

      <button type="button" className={styles.close} aria-label="알림 닫기" onClick={close}>
        <IconX size={16} stroke={1.8} aria-hidden="true" />
      </button>
    </div>
  )
}
