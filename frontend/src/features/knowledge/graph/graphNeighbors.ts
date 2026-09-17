import type { GraphEdge } from './graphTypes'
import { rootNodeId } from './graphLayout'

/**
 * 선택한 노드의 1-hop 이웃(UX §6.5).
 *
 * 간선 방향은 Source→Subject 처럼 한쪽이지만 사용자에게는 양쪽 다 "연결된 것"이다.
 * 그래서 방향을 가리지 않고 모은다.
 *
 * Folder는 edges에 없다. Folder를 고르면 조회된 문서와 카테고리를 이웃으로 본다.
 */
export function neighborsOf(
  nodeId: string,
  edges: GraphEdge[],
  folderMemberIds: string[],
): Set<string> {
  if (nodeId === rootNodeId) return new Set(folderMemberIds)

  const neighbors = new Set<string>()
  for (const edge of edges) {
    if (edge.from === nodeId) neighbors.add(edge.to)
    else if (edge.to === nodeId) neighbors.add(edge.from)
  }

  return neighbors
}

/** 선택한 노드에 닿는 간선인지. 캔버스에서 강조할 선을 고르는 데 쓴다. */
export function touchesNode(edge: GraphEdge, nodeId: string) {
  return edge.from === nodeId || edge.to === nodeId
}

/** 카테고리·키워드를 골랐을 때 강조할 노드와 간선. */
export interface NodeReach {
  nodeIds: Set<string>
  edges: Set<GraphEdge>
  /** 빛 점이 출발하는 간선 끝. 카테고리는 아래로(from), 키워드는 거슬러 위로(to) 간다. */
  flowFrom: 'from' | 'to'
}

/**
 * 카테고리에서 category → source → topic → subject 방향으로만 따라간다.
 *
 * 간선 방향대로만 내려가므로 여러 문서가 함께 쓰는 subject·topic에서 거꾸로 올라가
 * 다른 카테고리의 문서로 번지지 않는다. 문서가 직접 다루는 subject(ABOUT)도 같은 갈래로 본다.
 */
export function categoryReachOf(categoryId: string, edges: GraphEdge[]): NodeReach {
  const reachedEdges = new Set<GraphEdge>()
  const follow = (fromIds: Set<string>, kinds: GraphEdge['kind'][]) => {
    const next = new Set<string>()
    for (const edge of edges) {
      if (fromIds.has(edge.from) && kinds.includes(edge.kind)) {
        reachedEdges.add(edge)
        next.add(edge.to)
      }
    }
    return next
  }

  const sourceIds = follow(new Set([categoryId]), ['CONTAINS'])
  const topicIds = follow(sourceIds, ['SUPPORTS'])
  const subjectIds = new Set([
    ...follow(sourceIds, ['ABOUT']),
    ...follow(topicIds, ['INVOLVES']),
  ])

  return {
    nodeIds: new Set([categoryId, ...sourceIds, ...topicIds, ...subjectIds]),
    edges: reachedEdges,
    flowFrom: 'from',
  }
}

/**
 * 키워드에서 subject → topic → source로 거슬러 올라간다.
 *
 * 화면에 그린 선을 받는다. 주제를 거쳐 닿는 문서는 주제를 통해, 주제 없이 직접 다루는 문서는
 * ABOUT 선으로 닿는다. 문서에서 멈추므로 카테고리나 다른 키워드로 번지지 않는다.
 */
export function subjectReachOf(subjectId: string, edges: GraphEdge[]): NodeReach {
  const reachedEdges = new Set<GraphEdge>()
  const followBack = (toIds: Set<string>, kinds: GraphEdge['kind'][]) => {
    const previous = new Set<string>()
    for (const edge of edges) {
      if (toIds.has(edge.to) && kinds.includes(edge.kind)) {
        reachedEdges.add(edge)
        previous.add(edge.from)
      }
    }
    return previous
  }

  const subjectIds = new Set([subjectId])
  const topicIds = followBack(subjectIds, ['INVOLVES'])
  const sourceIds = new Set([
    ...followBack(topicIds, ['SUPPORTS']),
    ...followBack(subjectIds, ['ABOUT']),
  ])

  return {
    nodeIds: new Set([subjectId, ...topicIds, ...sourceIds]),
    edges: reachedEdges,
    flowFrom: 'to',
  }
}

/**
 * 그래프에 그릴 관계선. 주제를 거쳐 이미 닿는 키워드로 가는 문서 → 키워드(ABOUT) 선을 뺀다.
 *
 * 문서 → 주제 → 키워드 경로가 있으면 직접 선은 같은 사실을 한 번 더 말할 뿐이고, 주제 열을
 * 건너뛰며 주제 카드 근처에서 꺾여 주제에서 나온 선처럼 겹쳐 보인다(transitive reduction).
 * 주제를 거치지 않는 키워드의 ABOUT 선은 남는다. 상세 패널은 이 목록이 아니라 전체 관계를 쓴다.
 */
export function withoutRedundantAbout(edges: GraphEdge[]): GraphEdge[] {
  const topicIdsBySource = new Map<string, string[]>()
  const subjectIdsByTopic = new Map<string, Set<string>>()
  for (const edge of edges) {
    if (edge.kind === 'SUPPORTS') {
      topicIdsBySource.set(edge.from, [...(topicIdsBySource.get(edge.from) ?? []), edge.to])
    } else if (edge.kind === 'INVOLVES') {
      subjectIdsByTopic.set(edge.from, (subjectIdsByTopic.get(edge.from) ?? new Set()).add(edge.to))
    }
  }

  return edges.filter((edge) => edge.kind !== 'ABOUT'
    || !(topicIdsBySource.get(edge.from) ?? []).some((topicId) => subjectIdsByTopic.get(topicId)?.has(edge.to)))
}
