import { useEffect, useMemo, useState } from 'react'
import { flushSync } from 'react-dom'
import { EventSource } from 'eventsource'
import { IconFolder, IconKey, IconPlus, IconSearch } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import TaskPickerModal from '../calendar/TaskPickerModal'
import type { TaskPickerSubmission } from '../calendar/TaskPickerModal'
import { useFolderStore } from '../../store/folderStore'
import { ACCESS_TOKEN_KEY, AUTH_SESSION_EXPIRED_EVENT, refreshAccessToken } from '../../api/client'
import { addNewWorkItem, addWorkItem, getBoard, openAgentWorkEvents } from './agentWorkApi'
import { workItemKey } from './agentWorkKeys'
import { LANES, UNCATEGORIZED_LABEL } from './agentWorkLabels'
import type { AgentBoard, AgentBoardSort, AgentWorkItem } from './agentWorkTypes'
import BoardLaneSection from './BoardLaneSection'
import WorkItemInspector from './WorkItemInspector'
import styles from './CoworkBoardPage.module.css'
import AgentAccessTokenModal from './AgentAccessTokenModal'

type LoadStatus = 'loading' | 'ready' | 'error'

/** 보드 변경 알림 스트림 상태. 끊겨 있는 동안에도 마지막 보드는 그대로 보여 준다. */
type ConnectionState = 'connecting' | 'open' | 'reconnecting'

/** 200이 아닌 응답(프록시 오류 등)은 표준 재연결 대상이 아니라서 이 간격으로 다시 연결한다. */
const RECONNECT_DELAY_MS = 3000

/** 카드 Lane 이동을 View Transition으로 보여 준다. 지원하지 않거나 동작 줄이기 설정이면 바로 바꾼다. */
function withLaneTransition(update: () => void) {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!('startViewTransition' in document) || reduceMotion || document.visibilityState !== 'visible') {
    update()
    return
  }
  document.startViewTransition(() => flushSync(update))
}

/** 폴더 필터 값. 'all'은 전체, 'none'은 미분류, 그 밖에는 폴더 id다. */
type ContainerFilter = 'all' | 'none' | `${number}`

function allCards(board: AgentBoard | null) {
  return board ? LANES.flatMap(({ key }) => board[key]) : []
}

/** 현재 화면에서 표시하는 시작 전 카드를 먼저 연다. */
function initialSelection(board: AgentBoard) {
  const item = board.notStarted[0]
  return item ? workItemKey(item) : null
}

export default function CoworkBoardPage() {
  const folders = useFolderStore((state) => state.folders)
  const [board, setBoard] = useState<AgentBoard | null>(null)
  const [status, setStatus] = useState<LoadStatus>('loading')
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [containerFilter, setContainerFilter] = useState<ContainerFilter>('all')
  const [boardSort, setBoardSort] = useState<AgentBoardSort>('PRIORITY')
  const [isPickerOpen, setIsPickerOpen] = useState(false)
  const [isTokenModalOpen, setIsTokenModalOpen] = useState(false)
  const [connection, setConnection] = useState<ConnectionState>('connecting')

  useEffect(() => {
    let active = true
    let source: EventSource | null = null
    let retryTimer: number | undefined
    let latestRequest = 0

    // 알림이 연달아 와서 조회가 겹치면 마지막 요청의 결과만 반영한다. 과거 응답이 최신 보드를 덮지 않는다.
    const refresh = () => {
      const request = ++latestRequest
      getBoard(boardSort)
        .then((next) => {
          if (active && request === latestRequest) withLaneTransition(() => setBoard(next))
        })
        // 실패하면 현재 보드를 유지하고 다음 알림이나 재연결 때 다시 맞춘다.
        .catch(() => {})
    }

    const connect = () => {
      const events = openAgentWorkEvents()
      source = events
      // 처음 연결과 재연결 모두 snapshot을 다시 조회해 끊긴 동안의 변경을 맞춘다.
      events.addEventListener('open', () => {
        setConnection('open')
        refresh()
      })
      events.addEventListener('agent-work', refresh)
      events.addEventListener('error', (event) => {
        if (!active) return
        setConnection('reconnecting')
        // 연결 중이면 EventSource가 표준 방식으로 다시 연결한다.
        if (events.readyState !== EventSource.CLOSED) return
        if (event.code === 401) {
          refreshAccessToken()
            .then(() => { if (active) connect() })
            .catch(() => {
              localStorage.removeItem(ACCESS_TOKEN_KEY)
              window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT))
            })
          return
        }
        retryTimer = window.setTimeout(connect, RECONNECT_DELAY_MS)
      })
    }

    getBoard(boardSort)
      .then((loaded) => {
        if (!active) return
        setBoard(loaded)
        setSelectedKey(initialSelection(loaded))
        setStatus('ready')
        connect()
      })
      .catch(() => {
        if (active) setStatus('error')
      })
    return () => {
      active = false
      source?.close()
      window.clearTimeout(retryTimer)
    }
  }, [boardSort])

  const reload = async () => {
    setStatus('loading')
    try {
      const loaded = await getBoard(boardSort)
      setBoard(loaded)
      setSelectedKey((current) => allCards(loaded).some((card) => workItemKey(card) === current)
        ? current : initialSelection(loaded))
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }

  const cards = useMemo(() => allCards(board), [board])
  const selected = cards.find((card) => workItemKey(card) === selectedKey) ?? null

  const normalizedQuery = query.trim().toLowerCase()
  const matches = (card: AgentWorkItem) => {
    const { title, containerId } = card.workItem
    if (normalizedQuery && !title.toLowerCase().includes(normalizedQuery)) return false
    if (containerFilter === 'all') return true
    if (containerFilter === 'none') return containerId === null
    return containerId === Number(containerFilter)
  }

  // 보드에 이미 있는 Swimming 할 일은 모달에서 "추가됨"으로 보인다.
  const boardTaskIds = useMemo(() => new Set(
    cards.flatMap(({ workItem }) => (
      workItem.type === 'SWIMMING_TASK' && /^\d+$/.test(workItem.id) ? [Number(workItem.id)] : []
    )),
  ), [cards])

  const handleAddTasks = async ({ existingTaskIds, newTasks, planDate }: TaskPickerSubmission) => {
    const folderNameById = new Map(folders.map((folder) => [folder.id, folder.name]))
    const added = await Promise.all([
      ...existingTaskIds.map((taskId) => addWorkItem({ resourceType: 'SWIMMING_TASK', resourceId: String(taskId) })),
      ...newTasks.map((draft) => addNewWorkItem({
        title: draft.title,
        containerId: draft.folderId,
        containerName: draft.folderId === null ? null : folderNameById.get(draft.folderId) ?? null,
        important: draft.priority,
        urgent: draft.urgent,
        planDate,
      })),
    ])
    setBoard(await getBoard(boardSort))
    if (added.length > 0) setSelectedKey(workItemKey(added[added.length - 1]))
  }

  return (
    <div className={styles.page}>
      <header className={styles.subheader}>
        <div className={styles.heading}>
          <span className={styles.badge}>
            <span className={styles.sparkle} aria-hidden="true">✦</span>
            Cowork Board
          </span>
          <span className={styles.separator} aria-hidden="true">·</span>
          <p className={styles.subtitle}>Claude Code · Codex와 함께 진행 중인 할 일</p>
        </div>
        <div className={styles.actions}>
          <ActionButton
            className={styles['add-button']}
            icon={<IconPlus size={16} aria-hidden="true" />}
            onClick={() => setIsPickerOpen(true)}
          >
            할 일 추가
          </ActionButton>
          <ActionButton
            className={styles['connector-button']}
            variant="outline"
            icon={<IconKey size={16} aria-hidden="true" />}
            onClick={() => setIsTokenModalOpen(true)}
          >
            에이전트 연결
          </ActionButton>
        </div>
      </header>

      {status === 'loading' && !board && (
        <p className={styles.state} role="status">보드를 불러오는 중…</p>
      )}

      {status === 'error' && (
        <div className={styles.state} role="alert">
          <p>보드를 불러오지 못했어요.</p>
          <button type="button" className={styles.retry} onClick={() => void reload()}>다시 시도</button>
        </div>
      )}

      {board && status !== 'error' && (cards.length === 0 ? (
        <div className={styles.empty}>
          <h2>아직 함께 진행 중인 작업이 없어요.</h2>
          <p>할 일을 만들면 이곳에서 Agent 작업 현황과 함께 확인할 수 있어요.</p>
          <ActionButton
            className={styles['add-button']}
            icon={<IconPlus size={16} aria-hidden="true" />}
            onClick={() => setIsPickerOpen(true)}
          >
            할 일 추가
          </ActionButton>
        </div>
      ) : (
        <div className={styles.container}>
          <WorkItemInspector
            key={selected ? workItemKey(selected) : 'none'}
            item={selected}
            onClear={() => setSelectedKey(null)}
            onUpdated={() => void reload()}
          />

          <section className={styles.board} aria-label="Cowork Board Lane">
            <div className={styles.toolbar}>
              <label className={styles.search}>
                <IconSearch className={styles['search-icon']} size={18} aria-hidden="true" />
                <input
                  type="search"
                  value={query}
                  placeholder="할 일 제목 검색"
                  aria-label="할 일 제목 검색"
                  onChange={(event) => setQuery(event.target.value)}
                />
              </label>
              <label className={styles.filter}>
                <IconFolder size={16} aria-hidden="true" />
                <select
                  aria-label="폴더 필터"
                  value={containerFilter}
                  onChange={(event) => setContainerFilter(event.target.value as ContainerFilter)}
                >
                  <option value="all">모든 폴더</option>
                  {folders.map((folder) => (
                    <option key={folder.id} value={folder.id}>{folder.name}</option>
                  ))}
                  <option value="none">{UNCATEGORIZED_LABEL}</option>
                </select>
              </label>
              <label className={styles.filter}>
                <span className={styles['filter-label']}>정렬</span>
                <select
                  aria-label="보드 정렬"
                  value={boardSort}
                  onChange={(event) => setBoardSort(event.target.value as AgentBoardSort)}
                >
                  <option value="PRIORITY">중요·즉시 우선</option>
                  <option value="ASC">과거순</option>
                  <option value="DESC">최신순</option>
                </select>
              </label>
              {connection === 'reconnecting' && (
                <p className={styles.connection} role="status">연결이 잠시 끊겼어요. 다시 연결하는 중이에요.</p>
              )}
            </div>

            <div className={styles.lanes}>
              {LANES.map((definition) => (
                <BoardLaneSection
                  key={definition.lane}
                  definition={definition}
                  items={board[definition.key].filter(matches)}
                  selectedKey={selectedKey}
                  onSelect={setSelectedKey}
                />
              ))}
            </div>
          </section>
        </div>
      ))}

      {isPickerOpen && (
        <TaskPickerModal
          selectedTaskIds={boardTaskIds}
          onAddTasks={handleAddTasks}
          onClose={() => setIsPickerOpen(false)}
        />
      )}
      {isTokenModalOpen && <AgentAccessTokenModal onClose={() => setIsTokenModalOpen(false)} />}
    </div>
  )
}
