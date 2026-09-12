/** Knowledge Link API v0.4 §6의 응답 모양. */

/** Folder는 knowledge_node의 행이 아니지만 그래프에는 그려진다(정본 §3). */
export type GraphNodeType = 'FOLDER' | 'SOURCE' | 'SUBJECT' | 'TOPIC'

/**
 * 내부 관계 enum이 아니다. ABOUT과 INVOLVES는 사용자에게 같은 말이라 COVERS 하나로 합치고,
 * SUPPORTS를 USED_FOR로 준다. 화면에 필요한 구분은 이 둘뿐이다.
 */
export type GraphEdgeKind = 'COVERS' | 'USED_FOR'

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
}

export interface GraphQuery {
  /** 초기 Source 수 상한. 기본 20 */
  limit?: number
}
