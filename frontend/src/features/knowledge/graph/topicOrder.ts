import type { GraphNode, GraphResponse } from './graphTypes'

/**
 * Topic 노드에 붙일 번호. 먼저 저장한 링크가 1번이다.
 *
 * 기준은 그 Topic을 만든 문서를 저장한 시각이다. Topic 자신의 createdAt은 쓰지 않는다 —
 * Topic은 소화가 끝날 때 생겨서, 분석이 오래 걸리거나 다시 시도한 링크는 저장 순서와
 * 어긋난다. SOURCE 노드는 링크를 저장할 때 함께 만들어져 그 시각이 곧 링크 생성 시각이다.
 */
export function numberTopics(graph: GraphResponse): Map<string, number> {
  const savedAtBySource = new Map<string, number>()
  for (const node of graph.nodes) {
    if (node.type !== 'SOURCE' || node.createdAt === null) continue
    const savedAt = Date.parse(node.createdAt)
    if (!Number.isNaN(savedAt)) savedAtBySource.set(node.nodeId, savedAt)
  }

  const ordered = graph.nodes
    .filter((node) => node.type === 'TOPIC')
    .map((topic) => ({ topic, savedAt: savedAtOf(topic, graph, savedAtBySource) }))
    .sort(byOldestFirst)

  return new Map(ordered.map(({ topic }, index) => [topic.nodeId, index + 1]))
}

/**
 * 이 Topic을 만든 문서를 저장한 시각. Topic은 문서 하나가 만들지만, 시각을 알 수 없는
 * 문서가 섞일 수 있으므로 이어진 문서 중 가장 이른 시각을 쓴다.
 */
function savedAtOf(
  topic: GraphNode,
  graph: GraphResponse,
  savedAtBySource: Map<string, number>,
): number | null {
  let earliest: number | null = null

  for (const edge of graph.edges) {
    const neighborId = edge.from === topic.nodeId
      ? edge.to
      : edge.to === topic.nodeId
        ? edge.from
        : null
    if (neighborId === null) continue

    const savedAt = savedAtBySource.get(neighborId)
    if (savedAt !== undefined && (earliest === null || savedAt < earliest)) {
      earliest = savedAt
    }
  }

  return earliest
}

/**
 * 시각을 모르는 Topic은 뒤로 보낸다. 같은 시각이면 nodeId로 갈라, 같은 그래프를 다시 열어도
 * 같은 번호가 나오게 한다.
 */
function byOldestFirst(
  left: { topic: GraphNode; savedAt: number | null },
  right: { topic: GraphNode; savedAt: number | null },
) {
  if (left.savedAt === null || right.savedAt === null) {
    if (left.savedAt !== right.savedAt) return left.savedAt === null ? 1 : -1
  } else if (left.savedAt !== right.savedAt) {
    return left.savedAt - right.savedAt
  }

  return left.topic.nodeId.localeCompare(right.topic.nodeId)
}
