import { useEffect, useState } from 'react'
import { IconArrowLeft, IconMinus, IconPlus } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import { getSessionEvents, unlinkWorkItem } from './agentWorkApi'
import SessionWorkItemsModal from './SessionWorkItemsModal'
import AgentLabel from './AgentLabel'
import { sessionTitle } from './agentWorkBoard'
import { workItemKey } from './agentWorkKeys'
import { EVENT_LABELS, STATUS_LABELS, formatClockTime, formatRelativeTime } from './agentWorkLabels'
import type { AgentSession, AgentSessionEvent, AgentWorkItem } from './agentWorkTypes'
import styles from './Inspector.module.css'

type SessionTab = 'work' | 'activity'

type EventsState =
  | { status: 'loading' }
  | { status: 'ready'; events: AgentSessionEvent[] }
  | { status: 'error' }

interface SessionInspectorProps {
  session: AgentSession
  /** 보드의 모든 카드. 세션의 소속 목록과 붙일 후보를 여기서 만든다. 필터로 걸러지지 않은 목록이어야 한다. */
  cards: AgentWorkItem[]
  onSelect: (key: string) => void
  onClear: () => void
  onUpdated?: () => void
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

/** 세션이 주인공인 화면. 세션에 어떤 할 일이 붙어 있는지 보고 붙이거나 뗀다. */
export default function SessionInspector({
  session,
  cards,
  onSelect,
  onClear,
  onUpdated,
}: SessionInspectorProps) {
  const [tab, setTab] = useState<SessionTab>('work')
  const [eventsState, setEventsState] = useState<EventsState>({ status: 'loading' })
  const [isLinkModalOpen, setIsLinkModalOpen] = useState(false)
  const [isChanging, setIsChanging] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (tab !== 'activity') return
    let active = true
    getSessionEvents(session.id)
      .then((events) => { if (active) setEventsState({ status: 'ready', events }) })
      .catch(() => { if (active) setEventsState({ status: 'error' }) })
    return () => { active = false }
  }, [session.id, tab])

  // 세션의 소속 목록. 보드에 없는 할 일은 식별자만 보여준다.
  const members = session.workItemIds.map((workItemId) => ({
    workItemId,
    card: cards.find((card) => card.id === workItemId) ?? null,
  }))

  // 붙일 수 있는 할 일. 고르는 화면은 모달이 맡는다.
  const candidateCount = cards.filter((card) => !card.session || card.session.id !== session.id).length
  const isEnded = session.status === 'COMPLETED' || session.status === 'FAILED'

  const change = async (run: () => Promise<unknown>, message: string) => {
    if (isChanging) return
    setIsChanging(true)
    setError(null)
    try {
      await run()
      onUpdated?.()
    } catch {
      setError(message)
    } finally {
      setIsChanging(false)
    }
  }

  const unlink = (workItemId: number) => {
    void change(
      () => unlinkWorkItem(session.id, workItemId),
      '세션에서 떼지 못했어요. 잠시 후 다시 시도해 주세요.',
    )
  }

  return (
    <aside className={styles.inspector} aria-label="선택한 Agent 세션">
      <button type="button" className={styles.clear} onClick={onClear}>
        <IconArrowLeft size={16} aria-hidden="true" />
        <span>선택 해제</span>
      </button>

      <div className={styles.heading}>
        <div className={styles['session-heading']}>
          <AgentLabel className={styles['session-agent']} agentType={session.agentType} size={18} />
          <span className={styles['session-status']}>
            <span className={styles['status-dot']} data-status={session.status} aria-hidden="true" />
            {STATUS_LABELS[session.status]}
          </span>
        </div>
        <h2>{sessionTitle(session)}</h2>
      </div>

      <div className={styles.tabs}>
        <div className={styles.tablist} role="tablist" aria-label="세션 상세">
          <button
            type="button"
            role="tab"
            id="session-tab-work"
            aria-selected={tab === 'work'}
            aria-controls="session-panel-work"
            onClick={() => setTab('work')}
          >
            작업
          </button>
          <button
            type="button"
            role="tab"
            id="session-tab-activity"
            aria-selected={tab === 'activity'}
            aria-controls="session-panel-activity"
            onClick={() => setTab('activity')}
          >
            Agent 활동
          </button>
        </div>

        {tab === 'work' && (
          <div className={styles.panel} role="tabpanel" id="session-panel-work" aria-labelledby="session-tab-work">
            {session.summary && (
              <p className={styles['session-summary']}>
                {session.status === 'WAITING' ? `"${session.summary}"` : session.summary}
              </p>
            )}
            <span className={styles.meta}>
              시작 {formatClockTime(session.startedAt)} · 마지막 신호 {formatRelativeTime(session.lastSeenAt)} · 상태 수집 MCP
            </span>

            <div className={styles.sessions}>
              <span className={styles['member-label']}>함께 진행 중인 할 일 {members.length}개</span>
              <ul className={styles.members}>
                {members.map((member) => (
                  <li key={member.workItemId}>
                    {member.card ? (
                      <button
                        type="button"
                        className={styles['member-open']}
                        onClick={() => onSelect(workItemKey(member.card!))}
                      >
                        <span className={styles.truncate}>{member.card.workItem.title}</span>
                      </button>
                    ) : (
                      <span className={styles.truncate}>할 일 #{member.workItemId}</span>
                    )}
                    <button
                      type="button"
                      className={styles['link-action']}
                      aria-label={`${member.card?.workItem.title ?? `할 일 #${member.workItemId}`} 이 세션에서 떼기`}
                      title="이 세션에서 떼기"
                      disabled={isChanging}
                      onClick={() => unlink(member.workItemId)}
                    >
                      <IconMinus size={16} aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>

              {isEnded ? (
                <p className={styles.meta}>끝난 세션에는 할 일을 붙일 수 없어요. 다시 시작하면 함께 진행됩니다.</p>
              ) : candidateCount === 0 ? (
                <p className={styles.meta}>붙일 수 있는 할 일이 없어요.</p>
              ) : (
                <ActionButton
                  className={styles['link-open']}
                  variant="outline"
                  icon={<IconPlus size={16} aria-hidden="true" />}
                  disabled={isChanging}
                  onClick={() => setIsLinkModalOpen(true)}
                >
                  할 일 붙이기
                </ActionButton>
              )}
              {error && <p className={styles.meta} role="alert">{error}</p>}
            </div>
          </div>
        )}

        {tab === 'activity' && (
          <div className={styles.panel} role="tabpanel" id="session-panel-activity" aria-labelledby="session-tab-activity">
            {eventsState.status === 'loading' && <p className={styles.meta} role="status">활동을 불러오는 중…</p>}
            {eventsState.status === 'error' && <p className={styles.meta} role="alert">활동을 불러오지 못했어요.</p>}
            {eventsState.status === 'ready' && (eventsState.events.length === 0 ? (
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

      {isLinkModalOpen && (
        <SessionWorkItemsModal
          session={session}
          cards={cards}
          onClose={() => setIsLinkModalOpen(false)}
          onLinked={() => onUpdated?.()}
        />
      )}
    </aside>
  )
}
