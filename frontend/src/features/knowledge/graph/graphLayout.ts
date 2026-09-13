import type { GraphEdge, GraphNode, GraphNodeType, GraphResponse } from './graphTypes'

/**
 * 노드를 타입별 열에 놓는다.
 *
 * v0.4의 관계는 모두 1-hop 단방향이고(Source→Subject, Source→Topic, Topic→Subject) 키워드
 * 계층이 없다. 그래서 타입이 곧 열 순서다 — 배치를 계산으로 찾을 것이 없다.
 *
 * 힘 기반 배치를 쓰지 않는 이유는 이 화면이 시각화가 아니라 Navigation UI이기 때문이다
 * (UX §6). 볼 때마다 배치가 달라지면 지난번에 본 노드를 다시 찾지 못한다. 아래 두 선택지도
 * 같은 입력이면 같은 결과를 낸다.
 */

/** Folder는 knowledge_node의 행이 아니라 nodeId가 없다. 화면 안에서만 쓰는 자리표다. */
export const rootNodeId = '__folder__'

/**
 * 무엇에서 출발해 읽을 것인가.
 *
 * `source-first`는 폴더에 모은 문서에서 출발한다(§6.2의 진입 방식).
 * `subject-first`는 키워드에서 출발해 그 키워드를 다루는 문서로 내려간다.
 */
export type ColumnOrder = 'source-first' | 'subject-first'

/**
 * 한 열 안에서 무엇을 위에 둘 것인가.
 *
 * `linked`는 이어진 것끼리 가까이 붙인다. `degree`는 연결이 많은 것을 위에 둔다.
 */
export type NodeSort = 'linked' | 'degree'

/** 열을 좌우로 세울지 위아래로 쌓을지. */
export type LayoutAxis = 'horizontal' | 'vertical'

export interface LayoutOptions {
  axis?: LayoutAxis
  order?: ColumnOrder
  sort?: NodeSort
}

const SOURCE_FIRST: GraphNodeType[] = ['FOLDER', 'SOURCE', 'TOPIC', 'SUBJECT']
const SUBJECT_FIRST: GraphNodeType[] = ['SUBJECT', 'TOPIC', 'SOURCE', 'FOLDER']

/**
 * rank는 진행 방향(열과 열 사이), sibling은 같은 열 안의 간격이다.
 * 노드가 세로보다 가로로 길어 방향에 따라 값이 뒤집힌다.
 */
const GAPS: Record<LayoutAxis, { rank: number; sibling: number }> = {
  horizontal: { rank: 300, sibling: 96 },
  vertical: { rank: 150, sibling: 210 },
}

const START = 40

export interface PositionedNode {
  node: GraphNode
  x: number
  y: number
}

function degreeOf(nodeId: string, edges: GraphEdge[]) {
  return edges.filter((edge) => edge.from === nodeId || edge.to === nodeId).length
}

/**
 * 앞 열에서 자기와 이어진 노드들의 평균 자리로 보낸다(barycenter).
 *
 * 한 번만 훑어도 선의 교차가 눈에 띄게 준다. 앞 열에 이어진 것이 없는 노드는 원래 자리를
 * 그대로 쓴다 — 근거 없이 위로 끌어올리지 않는다.
 */
function sortByLinked(column: GraphNode[], previous: GraphNode[], edges: GraphEdge[]) {
  const rowOf = new Map(previous.map((node, index) => [node.nodeId, index]))

  const withKey = column.map((node, index) => {
    const rows = edges
      .filter((edge) => edge.from === node.nodeId || edge.to === node.nodeId)
      .map((edge) => rowOf.get(edge.from === node.nodeId ? edge.to : edge.from))
      .filter((row): row is number => row !== undefined)

    const key = rows.length === 0
      ? index
      : rows.reduce((sum, row) => sum + row, 0) / rows.length

    return { node, key, index }
  })

  return withKey
    .sort((a, b) => a.key - b.key || a.index - b.index)
    .map((entry) => entry.node)
}

function sortByDegree(column: GraphNode[], edges: GraphEdge[]) {
  return column
    .map((node, index) => ({ node, degree: degreeOf(node.nodeId, edges), index }))
    .sort((a, b) => b.degree - a.degree || a.index - b.index)
    .map((entry) => entry.node)
}

/**
 * 열마다 세로로 고르게 펴고, 짧은 열은 가장 긴 열의 가운데에 맞춘다.
 * 한 열이 셋이고 옆 열이 열둘일 때 셋이 위쪽에 몰리지 않게 한다.
 */
export function layoutGraph(
  graph: GraphResponse,
  { axis = 'horizontal', order = 'source-first', sort = 'linked' }: LayoutOptions = {},
): PositionedNode[] {
  const columnOrder = order === 'subject-first' ? SUBJECT_FIRST : SOURCE_FIRST
  const columns = columnOrder.map(() => [] as GraphNode[])

  // root가 Folder면 노드 목록에 없으므로 Folder 열에 직접 세운다.
  if (graph.root.type === 'FOLDER') {
    columns[columnOrder.indexOf('FOLDER')].push({
      nodeId: rootNodeId,
      type: 'FOLDER',
      title: graph.root.title,
      createdAt: null,
    })
  }

  for (const node of graph.nodes) {
    const column = columns[columnOrder.indexOf(node.type)]
    if (column) column.push(node)
  }

  // 앞 열이 이미 정렬된 상태를 기준으로 삼아야 하므로 왼쪽부터 차례로 채운다.
  const sorted: GraphNode[][] = []
  for (const [index, column] of columns.entries()) {
    if (sort === 'degree') {
      sorted.push(sortByDegree(column, graph.edges))
      continue
    }
    // 첫 열은 기준 삼을 앞 열이 없어 받은 순서를 지킨다. 문서는 최근 순으로 온다.
    sorted.push(index === 0 ? column : sortByLinked(column, sorted[index - 1], graph.edges))
  }

  const { rank, sibling } = GAPS[axis]
  const longest = Math.max(...sorted.map((column) => column.length), 1)

  return sorted.flatMap((column, columnIndex) => {
    const offset = ((longest - column.length) * sibling) / 2
    return column.map((node, rowIndex) => {
      const alongRank = START + columnIndex * rank
      const alongColumn = offset + rowIndex * sibling

      return axis === 'vertical'
        ? { node, x: alongColumn, y: alongRank }
        : { node, x: alongRank, y: alongColumn }
    })
  })
}
