import { Handle, Position } from '@xyflow/react'
import type { Node, NodeProps } from '@xyflow/react'
import type { LayoutAxis } from './graphLayout'
import type { GraphNode } from './graphTypes'
import { sourceMark } from '../sourceIcon'
import type { SourceCard } from '../knowledgeTypes'
import styles from './GraphNodeCard.module.css'

export type KnowledgeNodeData = {
  node: GraphNode
  /** 1-hop 밖이라 흐리게 그린다. 선택이 없으면 모두 false다. */
  dimmed: boolean
  /** SOURCE 노드일 때만. sourceId와 nodeId가 같아 목록에서 받아 둔 카드를 붙일 수 있다. */
  sourceCard?: SourceCard
  /** 선이 붙는 자리는 배치 방향을 따라간다. */
  axis: LayoutAxis
  /** 축소 상태에서 숨긴 Subject 수를 대신 보여 주는 semantic zoom 요약 노드다. */
  subjectSummary?: boolean
  /** TOPIC 노드에만. 링크를 저장한 순서다(topicOrder). */
  order?: number
} & Record<string, unknown>

export type KnowledgeFlowNode = Node<KnowledgeNodeData, 'knowledge'>

/**
 * 노드 하나. 타입 구분은 인지를 돕는 정도로만 쓴다(UX §6.3).
 * Subject는 여러 문서가 함께 가리키는 키워드라 칩, Topic은 문서 하나가 만든 주제라 카드다.
 */
export default function GraphNodeCard({ data, selected }: NodeProps<KnowledgeFlowNode>) {
  const { node, dimmed, sourceCard, axis, subjectSummary, order } = data
  const incoming = axis === 'vertical' ? Position.Top : Position.Left
  const outgoing = axis === 'vertical' ? Position.Bottom : Position.Right

  return (
    <div
      className={styles.node}
      data-type={node.type}
      data-dimmed={dimmed || undefined}
      data-selected={selected || undefined}
      data-subject-summary={subjectSummary || undefined}
    >
      <Handle className={styles.handle} type="target" position={incoming} isConnectable={false} />
      <span className={styles.title}>
        {order !== undefined && (
          <span className={styles.order}>
            <span className="sr-only">저장 순서 </span>
            {order}
          </span>
        )}
        {node.title}
      </span>
      {node.type === 'SOURCE' && (
        <span className={styles.origin}>
          {sourceMark(sourceCard?.domain ?? null, 15, styles.mark)}
          {sourceCard?.domain && <span className={styles.domain}>{sourceCard.domain}</span>}
        </span>
      )}
      <Handle className={styles.handle} type="source" position={outgoing} isConnectable={false} />
    </div>
  )
}
