/** Knowledge Link API v0.4 §6의 응답 모양. */

/** Folder는 knowledge_node의 행이 아니지만 그래프에는 그려진다(정본 §3). */
export type GraphNodeType = 'FOLDER' | 'CATEGORY' | 'SOURCE' | 'SUBJECT' | 'TOPIC'

/** 서버의 관계 타입. CONTAINS는 카테고리 → 문서 소속을 나타낸다. */
export type GraphEdgeKind = 'ABOUT' | 'SUPPORTS' | 'INVOLVES' | 'CONTAINS'

export interface GraphNode {
  nodeId: string
  type: GraphNodeType
  title: string
  /**
   * 이 노드가 그래프에 처음 생긴 시각. SOURCE는 링크를 저장할 때 함께 생기므로 이 값이 곧
   * 링크 생성 시각이다. Folder 자리표처럼 화면 안에서만 만든 노드는 null이다.
   */
  createdAt: string | null
}

/** 진입점. Folder면 nodeId가 null이고 Folder의 id가 따로 온다. */
export interface GraphRoot {
  nodeId: string | null
  type: GraphNodeType
  id?: number
  title: string
}

export interface GraphEdge {
  from: string
  to: string
  kind: GraphEdgeKind
}

/** truncated면 limit에 걸려 잘렸다는 뜻이다. 초기 노드 수 제한을 사용자에게 알릴 때 쓴다. */
export interface GraphResponse {
  root: GraphRoot
  nodes: GraphNode[]
  edges: GraphEdge[]
  truncated: boolean
  /** 현재 Folder 그래프 범위에서 Category Preview에 넣을 수 있는 Source 수. */
  categorizableCount: number
}

export interface GraphQuery {
  /** 초기 Source 수 상한. 기본 20 */
  limit?: number
}
