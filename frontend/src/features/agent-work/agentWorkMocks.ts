import type {
  AddWorkItemRequest,
  AgentBoard,
  AgentSession,
  AgentSessionEvent,
  AgentSessionEventType,
  AgentType,
  AgentWorkItem,
  AgentWorkStatus,
  BoardLane,
  NewWorkItemDraft,
  WorkItem,
} from './agentWorkTypes'
import { useFolderStore } from '../../store/folderStore'

/*
 * 화면 초안의 메모리 목업. 실제 화면의 API 경로에서는 사용하지 않는다.
 * 폴더는 실제 폴더 API로 불러온 목록에서 순서(slot)로 빌려 쓴다. 폴더가 모자라면 미분류가 된다.
 * Lane 결정·정렬은 백엔드 AgentBoardUseCase의 의사 코드와 같은 규칙을 따른다.
 */

const MINUTE = 60_000
const DAY = 24 * 60

function minutesAgo(minutes: number) {
  return new Date(Date.now() - minutes * MINUTE).toISOString()
}

let nextId = 1000

function task(id: string, title: string, important = false, urgent = false): WorkItem {
  return { type: 'SWIMMING_TASK', id, title, containerId: null, containerName: null, status: 0, important, urgent }
}

/** [이벤트, 몇 분 전, 요약, 기록 당시 Agent(생략하면 세션의 Agent)] */
type EventSeed = [AgentSessionEventType, number, string | null, AgentType?]

interface SessionSeed {
  agentType: AgentType
  status: AgentWorkStatus
  summary: string
  startedMinutesAgo: number
  lastSeenMinutesAgo: number
  events: EventSeed[]
}

interface MockItem {
  id: number
  workItem: WorkItem
  /** 실제 폴더 목록에서 빌려 쓸 순서. null이면 workItem의 폴더를 그대로 쓴다. */
  folderSlot: number | null
  createdAt: string
  session: AgentSession | null
  events: AgentSessionEvent[]
}

function mockItem(
  id: number,
  workItem: WorkItem,
  folderSlot: number | null,
  createdMinutesAgo: number,
  seed?: SessionSeed,
): MockItem {
  if (!seed) {
    return { id, workItem, folderSlot, createdAt: minutesAgo(createdMinutesAgo), session: null, events: [] }
  }

  const sessionId = id * 10
  const ended = seed.status === 'COMPLETED' || seed.status === 'FAILED'
  const session: AgentSession = {
    id: sessionId,
    workItemIds: [id],
    agentType: seed.agentType,
    status: seed.status,
    statusSource: 'MCP_REPORT',
    instruction: null,
    summary: seed.summary,
    startedAt: minutesAgo(seed.startedMinutesAgo),
    lastSeenAt: minutesAgo(seed.lastSeenMinutesAgo),
    completedAt: ended ? minutesAgo(seed.lastSeenMinutesAgo) : null,
  }
  const events = seed.events.map(([eventType, eventMinutesAgo, summary, agentType]): AgentSessionEvent => ({
    id: nextId++,
    sessionId,
    agentType: agentType ?? seed.agentType,
    eventType,
    summary,
    source: 'MCP_REPORT',
    createdAt: minutesAgo(eventMinutesAgo),
  }))
  return { id, workItem, folderSlot, createdAt: minutesAgo(createdMinutesAgo), session, events }
}

function initialItems(): MockItem[] {
  return [
    mockItem(1, task('101', 'README 문서 구조 개선', true), 0, DAY),
    mockItem(2, task('102', '요금제 정책 및 구독 모델 정리'), 1, 1),
    mockItem(3, task('103', '기술 문서 초안 작성', false, true), 0, 40, {
      agentType: 'CLAUDE_CODE', status: 'WORKING', summary: '소화 카드 4건 종합 중',
      startedMinutesAgo: 30, lastSeenMinutesAgo: 0,
      events: [['STARTED', 30, null], ['PROGRESS_REPORTED', 12, '자료 수집 완료'], ['PROGRESS_REPORTED', 0, '소화 카드 4건 종합 중']],
    }),
    mockItem(4, task('104', '경쟁 서비스 UX 비교 리서치'), 1, 20, {
      agentType: 'CODEX', status: 'WORKING', summary: '아티클 2건의 차별화 요소 정리 중',
      startedMinutesAgo: 15, lastSeenMinutesAgo: 3,
      events: [['STARTED', 15, null], ['PROGRESS_REPORTED', 3, '아티클 2건의 차별화 요소 정리 중']],
    }),
    mockItem(5, task('105', 'MCP 서버 구현하기', true, true), 0, 60, {
      agentType: 'CLAUDE_CODE', status: 'WAITING', summary: 'Migration까지 적용할까요?',
      startedMinutesAgo: 45, lastSeenMinutesAgo: 35,
      events: [
        ['STARTED', 45, null],
        ['PROGRESS_REPORTED', 39, 'Controller 구현 중'],
        ['WAITING_FOR_USER', 35, 'Migration까지 적용할까요?'],
      ],
    }),
    mockItem(6, task('106', '면접 답변 STAR 구조로 정리'), 2, 90, {
      agentType: 'CLAUDE_CODE', status: 'WAITING', summary: '프로젝트 5개 중 어떤 것부터 정리할까요?',
      startedMinutesAgo: 20, lastSeenMinutesAgo: 8,
      events: [['STARTED', 20, null], ['WAITING_FOR_USER', 8, '프로젝트 5개 중 어떤 것부터 정리할까요?']],
    }),
    // 어제 Claude Code가 실패한 세션을 오늘 Codex가 다시 열어 완료했다. 이전 기록은 이벤트에만 남는다.
    mockItem(7, task('107', 'Spring Boot & Ollama 로컬 환경 세팅'), 0, 2 * DAY, {
      agentType: 'CODEX', status: 'COMPLETED', summary: '테스트 14개 통과',
      startedMinutesAgo: 120, lastSeenMinutesAgo: 60,
      events: [
        ['STARTED', DAY + 60, null, 'CLAUDE_CODE'],
        ['FAILED', DAY + 40, 'Ollama 모델을 받지 못함', 'CLAUDE_CODE'],
        ['STARTED', 120, null],
        ['COMPLETED', 60, '테스트 14개 통과'],
      ],
    }),
    mockItem(8, task('108', '소개 페이지 랜딩 카피 초안'), 1, 3 * DAY, {
      agentType: 'CLAUDE_CODE', status: 'COMPLETED', summary: '카피 3안 작성',
      startedMinutesAgo: DAY + 200, lastSeenMinutesAgo: DAY + 150,
      events: [['STARTED', DAY + 200, null], ['COMPLETED', DAY + 150, '카피 3안 작성']],
    }),
    mockItem(9, task('109', '로그인 API 리팩터링'), null, 60, {
      agentType: 'CODEX', status: 'FAILED', summary: '테스트 실행 중 오류',
      startedMinutesAgo: 50, lastSeenMinutesAgo: 20,
      events: [['STARTED', 50, null], ['FAILED', 20, '테스트 실행 중 오류']],
    }),
    mockItem(10, task('110', '검색 인덱스 재구성'), 0, 80, {
      agentType: 'CLAUDE_CODE', status: 'UNKNOWN', summary: '인덱스 다시 만드는 중',
      startedMinutesAgo: 75, lastSeenMinutesAgo: 42,
      events: [['STARTED', 75, null], ['PROGRESS_REPORTED', 72, '인덱스 다시 만드는 중'], ['SIGNAL_LOST', 42, null]],
    }),
  ]
}

let items = initialItems()

const LANE_BY_STATUS: Record<AgentWorkStatus, BoardLane> = {
  WORKING: 'WORKING',
  WAITING: 'WAITING',
  COMPLETED: 'COMPLETED',
  FAILED: 'ATTENTION',
  UNKNOWN: 'ATTENTION',
}

function resolveWorkItem(item: MockItem): WorkItem {
  if (item.folderSlot === null) return item.workItem
  const folder = useFolderStore.getState().folders[item.folderSlot]
  return {
    ...item.workItem,
    containerId: folder?.id ?? null,
    containerName: folder?.name ?? null,
  }
}

function toCard(item: MockItem): AgentWorkItem {
  return {
    id: item.id,
    lane: item.session ? LANE_BY_STATUS[item.session.status] : 'NOT_STARTED',
    workItem: resolveWorkItem(item),
    session: item.session,
    lastActivityAt: item.session?.lastSeenAt ?? item.createdAt,
  }
}

function flagRank({ workItem }: AgentWorkItem) {
  if (workItem.important && workItem.urgent) return 0
  if (workItem.urgent) return 1
  return workItem.important ? 2 : 3
}

function inLane(cards: AgentWorkItem[], lane: BoardLane) {
  return cards
      .filter((card) => card.lane === lane)
    .sort((a, b) => flagRank(a) - flagRank(b)
      || b.lastActivityAt.localeCompare(a.lastActivityAt)
      || (b.id ?? -1) - (a.id ?? -1))
}

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => { setTimeout(() => resolve(structuredClone(value)), 250) })
}

export function getBoard(): Promise<AgentBoard> {
  const cards = items.map(toCard)
  return delay({
    notStarted: inLane(cards, 'NOT_STARTED'),
    working: inLane(cards, 'WORKING'),
    waiting: inLane(cards, 'WAITING'),
    completed: inLane(cards, 'COMPLETED'),
    attention: inLane(cards, 'ATTENTION'),
  })
}

export function getEvents(workItemId: number): Promise<AgentSessionEvent[]> {
  const item = items.find((candidate) => candidate.id === workItemId)
  const events = item ? [...item.events].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id) : []
  return delay(events)
}

/** 목업은 기존 할 일의 제목을 모르므로 id로 이름을 붙인다. */
export function addWorkItem(request: AddWorkItemRequest): Promise<AgentWorkItem> {
  const existing = items.find((item) => item.workItem.type === request.resourceType && item.workItem.id === request.resourceId)
  if (existing) return delay(toCard(existing))

  const item = mockItem(nextId++, task(request.resourceId, `할 일 #${request.resourceId}`), null, 0)
  items = [...items, item]
  return delay(toCard(item))
}

export function addNewWorkItem(draft: NewWorkItemDraft): Promise<AgentWorkItem> {
  const id = nextId++
  const workItem = {
    ...task(`new-${id}`, draft.title, draft.important, draft.urgent),
    containerId: draft.containerId,
    containerName: draft.containerName,
  }
  const item = mockItem(id, workItem, null, 0)
  items = [...items, item]
  return delay(toCard(item))
}
