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

/** 카테고리를 골랐을 때 강조할 노드와 간선. */
export interface CategoryReach {
  nodeIds: Set<string>
  edges: Set<GraphEdge>
}

/**
 * 카테고리에서 category → source → topic → subject 방향으로만 따라간다.
 *
 * 간선 방향대로만 내려가므로 여러 문서가 함께 쓰는 subject·topic에서 거꾸로 올라가
 * 다른 카테고리의 문서로 번지지 않는다. 문서가 직접 다루는 subject(ABOUT)도 같은 갈래로 본다.
 */
export function categoryReachOf(categoryId: string, edges: GraphEdge[]): CategoryReach {
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
  }
}
