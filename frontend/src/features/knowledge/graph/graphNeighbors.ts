import type { GraphEdge } from './graphTypes'
import { rootNodeId } from './graphLayout'

/**
 * 선택한 노드의 1-hop 이웃(UX §6.5).
 *
 * 간선 방향은 Source→Subject 처럼 한쪽이지만 사용자에게는 양쪽 다 "연결된 것"이다.
 * 그래서 방향을 가리지 않고 모은다.
 *
 * Folder는 edges에 없다. root에 달린 SOURCE 노드가 곧 소속이므로(§6.1) Folder를 고르면
 * 모든 Source가 이웃이다.
 */
export function neighborsOf(
  nodeId: string,
  edges: GraphEdge[],
  sourceNodeIds: string[],
): Set<string> {
  if (nodeId === rootNodeId) return new Set(sourceNodeIds)

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
