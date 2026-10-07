import { useEffect, useState } from 'react'
import { IconArrowLeft, IconCheck, IconCopy, IconFolder, IconPencil, IconPlus } from '@tabler/icons-react'
import { getSessionEvents, linkWorkItems } from './agentWorkApi'
import AgentLabel from './AgentLabel'
import {
  EVENT_LABELS,
  LANES,
  STATUS_LABELS,
  UNCATEGORIZED_LABEL,
  formatClockTime,
} from './agentWorkLabels'
import type { AgentSessionEvent, AgentWorkItem } from './agentWorkTypes'
import { sessionKey, sessionTitle } from './agentWorkBoard'
import TaskInfoModal from '../tasks/TaskInfoModal'
import styles from './Inspector.module.css'

type InspectorTab = 'work' | 'knowledge' | 'activity' | 'proposal'

/** 활동 조회·연결된 지식·제안은 백엔드 구현 후 사용할 수 있다. */
const TABS: Array<{ value: InspectorTab; label: string; disabled: boolean }> = [
  { value: 'work', label: '작업', disabled: false },
  { value: 'knowledge', label: '연결된 지식', disabled: true },
  { value: 'activity', label: 'Agent 활동', disabled: false },
  { value: 'proposal', label: '제안', disabled: true },
]

type EventsState =
  | { status: 'loading' }
  | { status: 'ready'; events: AgentSessionEvent[] }
  | { status: 'error' }

interface WorkItemInspectorProps {
  item: AgentWorkItem
  onClear: () => void
  onUpdated?: () => void
  /** 보드의 모든 카드. 붙일 수 있는 세션을 여기서 고른다. 필터로 걸러지지 않은 목록이어야 한다. */
  cards?: AgentWorkItem[]
  /** 속한 세션 카드로 건너간다. */
  onSelect?: (key: string) => void
}

/** 에이전트는 아이콘으로 보이므로 문구에는 이름을 넣지 않는다. */
function describeEvent(event: AgentSessionEvent) {
  switch (event.eventType) {
    case 'STARTED':
      return '작업 시작'
    case 'WAITING_FOR_USER':
      return '사용자 판단 대기'
    default:
      return event.summary ?? EVENT_LABELS[event.eventType]
  }
}

/** 임시: find_tasks 전까지 에이전트에게 Task 식별자를 넘기는 문구. */
function agentPrompt(workItem: AgentWorkItem['workItem']) {
  return [
    `Swimming 할 일 "${workItem.title}"을 진행해 줘.`,
    `swimming MCP의 start_work를 workItems: [{ resourceType: "${workItem.type}", resourceId: "${workItem.id}" }]로 호출해 시작하고, 끝나면 complete_work로 보고해 줘.`,
  ].join('\n')
}

/** 카드가 바뀌면 부모가 key로 새로 마운트하므로 탭과 이벤트 상태는 카드마다 처음부터 시작한다. */
export default function WorkItemInspector({
  item,
  onClear,
  onUpdated,
  cards = [],
  onSelect,
}: WorkItemInspectorProps) {
  const [tab, setTab] = useState<InspectorTab>('work')
  const [eventsState, setEventsState] = useState<EventsState>({ status: 'loading' })
  const [isEditOpen, setIsEditOpen] = useState(false)
  const [copied, setCopied] = useState(false)
  const [linkTarget, setLinkTarget] = useState('')
  const [isLinking, setIsLinking] = useState(false)
  const [linkError, setLinkError] = useState<string | null>(null)
  const sessionId = item?.session?.id ?? null

  useEffect(() => {
    if (sessionId === null || tab !== 'activity') return
    let active = true
    getSessionEvents(sessionId)
      .then((events) => { if (active) setEventsState({ status: 'ready', events }) })
      .catch(() => { if (active) setEventsState({ status: 'error' }) })
    return () => { active = false }
  }, [sessionId, tab])

  const { workItem, session } = item
  const laneTitle = LANES.find((definition) => definition.lane === item.lane)?.title

  // 세션이 없는 카드는 진행 중인 세션을 골라 붙인다. 세션마다 카드 하나로 중복을 없앤다.
  const runningSessions = (() => {
    const bySessionId = new Map<number, AgentWorkItem>()
    cards.forEach((card) => {
      if (!card.session || card.session.status === 'COMPLETED' || card.session.status === 'FAILED') return
      if (!bySessionId.has(card.session.id)) bySessionId.set(card.session.id, card)
    })
    return [...bySessionId.values()]
  })()

  /** 세션이 없는 카드를 고른 세션에 붙인다. */
  const linkToSelectedSession = async () => {
    const targetSessionId = Number(linkTarget)
    if (!Number.isSafeInteger(targetSessionId) || targetSessionId <= 0 || isLinking) return

    setIsLinking(true)
    setLinkError(null)
    try {
      await linkWorkItems(targetSessionId, [{ resourceType: workItem.type, resourceId: workItem.id }])
      onUpdated?.()
    } catch {
      setLinkError('세션에 붙이지 못했어요. 세션이 끝났을 수 있어요.')
    } finally {
      setIsLinking(false)
    }
  }

  const copyPrompt = async () => {
    await navigator.clipboard.writeText(agentPrompt(workItem))
    setCopied(true)
  }

  return (
    <aside className={styles.inspector} aria-label="선택한 할 일">
      <button type="button" className={styles.clear} onClick={onClear}>
        <IconArrowLeft size={16} aria-hidden="true" />
        <span>선택 해제</span>
      </button>

      <div className={styles.heading}>
        {(workItem.important || workItem.urgent) && (
          <div className={styles.pills}>
            {workItem.urgent && <span className={styles.urgent}>⚡️ 즉시</span>}
            {workItem.important && <span className={styles.important}>📌 중요</span>}
          </div>
        )}
        <div className={styles['heading-row']}>
          <h2>{workItem.title}</h2>
          <div className={styles['heading-actions']}>
            <button
              type="button"
              className={styles.edit}
              aria-label={copied ? '에이전트 요청 문구 복사됨' : '에이전트 요청 문구 복사'}
              title={copied ? '복사됨' : '에이전트 요청 문구 복사'}
              onClick={() => void copyPrompt()}
            >
              {copied ? <IconCheck size={16} aria-hidden="true" /> : <IconCopy size={16} aria-hidden="true" />}
            </button>
            {workItem.type === 'SWIMMING_TASK' && (
              <button
                type="button"
                className={styles.edit}
                aria-label="할 일 수정"
                onClick={() => setIsEditOpen(true)}
              >
                <IconPencil size={16} aria-hidden="true" />
              </button>
            )}
          </div>
        </div>
      </div>

      <div className={styles.tabs}>
        <div className={styles.tablist} role="tablist" aria-label="할 일 상세">
          {TABS.map((option) => (
            <button
              key={option.value}
              type="button"
              role="tab"
              id={`inspector-tab-${option.value}`}
              aria-selected={tab === option.value}
              aria-controls={`inspector-panel-${option.value}`}
              disabled={option.disabled}
              title={option.disabled ? `${option.label} · 준비 중` : undefined}
              onClick={() => setTab(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>

        {tab === 'work' && (
          <div className={styles.panel} role="tabpanel" id="inspector-panel-work" aria-labelledby="inspector-tab-work">
            <div className={styles.facts}>
              <div className={styles.fact}>
                <span className={styles['fact-label']}>상태</span>
                <span className={styles['fact-value']}>
                  <span className={styles['lane-dot']} data-lane={item.lane} aria-hidden="true" />
                  {laneTitle}
                </span>
              </div>
              <div className={styles.fact}>
                <span className={styles['fact-label']}>폴더</span>
                <span className={styles['fact-value']}>
                  <IconFolder className={styles['fact-icon']} size={14} aria-hidden="true" />
                  <span className={styles.truncate}>{workItem.containerName ?? UNCATEGORIZED_LABEL}</span>
                </span>
              </div>
            </div>

            <div className={styles.sessions}>
              <span className={styles['section-label']}>Agent 세션</span>
              {!session ? (
                <>
                  <p className={styles.meta}>아직 이 할 일로 작업을 시작한 Agent가 없어요.</p>
                  {runningSessions.length === 0 ? (
                    <p className={styles.meta}>진행 중인 세션이 없어요. 에이전트가 작업을 시작하면 여기에서 붙일 수 있어요.</p>
                  ) : (
                    <div className={styles['link-row']}>
                      <select
                        aria-label="붙일 세션"
                        value={linkTarget}
                        disabled={isLinking}
                        onChange={(event) => setLinkTarget(event.target.value)}
                      >
                        <option value="">진행 중인 세션 고르기</option>
                        {runningSessions.map((candidate) => (
                          <option key={candidate.session!.id} value={candidate.session!.id}>
                            {sessionTitle(candidate.session!)}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className={styles['link-action']}
                        aria-label="고른 세션에 이 할 일 붙이기"
                        title="이 세션에 붙이기"
                        disabled={linkTarget === '' || isLinking}
                        onClick={() => void linkToSelectedSession()}
                      >
                        <IconPlus size={16} aria-hidden="true" />
                      </button>
                    </div>
                  )}
                </>
              ) : (
                // 세션이 주인공인 화면은 세션 카드에 있다. 여기서는 어느 세션에 속했는지만 알리고 건너간다.
                <button
                  type="button"
                  className={styles['session-link']}
                  onClick={() => onSelect?.(sessionKey(session.id))}
                >
                  <span className={styles['session-heading']}>
                    <AgentLabel className={styles['session-agent']} agentType={session.agentType} size={18} />
                    <span className={styles['session-status']}>
                      <span className={styles['status-dot']} data-status={session.status} aria-hidden="true" />
                      {STATUS_LABELS[session.status]}
                    </span>
                  </span>
                  <span className={styles.truncate}>{sessionTitle(session)}</span>
                  <span className={styles.meta}>세션 카드에서 함께 진행 중인 할 일을 다룹니다</span>
                </button>
              )}
              {linkError && <p className={styles.meta} role="alert">{linkError}</p>}
            </div>
          </div>
        )}

        {tab === 'activity' && (
          <div className={styles.panel} role="tabpanel" id="inspector-panel-activity" aria-labelledby="inspector-tab-activity">
            {sessionId === null && <p className={styles.meta}>아직 기록된 활동이 없어요.</p>}
            {sessionId !== null && eventsState.status === 'loading' && <p className={styles.meta} role="status">활동을 불러오는 중…</p>}
            {sessionId !== null && eventsState.status === 'error' && <p className={styles.meta} role="alert">활동을 불러오지 못했어요.</p>}
            {sessionId !== null && eventsState.status === 'ready' && (eventsState.events.length === 0 ? (
              <p className={styles.meta}>아직 기록된 활동이 없어요.</p>
            ) : (
              <ol className={styles.timeline}>
                {eventsState.events.map((event) => (
                  <li key={event.id}>
                    <time dateTime={event.createdAt}>{formatClockTime(event.createdAt)}</time>
                    <span className={styles.event}>
                      <AgentLabel className={styles['event-agent']} agentType={event.agentType} />
                      <span>{describeEvent(event)}</span>
                    </span>
                  </li>
                ))}
              </ol>
            ))}
          </div>
        )}
      </div>

      {isEditOpen && workItem.type === 'SWIMMING_TASK' && (
        <TaskInfoModal
          taskId={Number(workItem.id)}
          taskTitle={workItem.title}
          currentFolderId={workItem.containerId}
          currentPriority={workItem.important}
          currentUrgent={workItem.urgent}
          currentPlanDate={workItem.planDate}
          onSaved={() => { setIsEditOpen(false); onUpdated?.() }}
          onClose={() => setIsEditOpen(false)}
        />
      )}
    </aside>
  )
}
