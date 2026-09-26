import { IconExternalLink, IconX } from '@tabler/icons-react'
import { neighborsOf } from './graphNeighbors'
import type { GraphNode, GraphNodeType, GraphResponse } from './graphTypes'
import { NODE_TYPE_LABEL } from './graphLabels'
import { sourceMark } from '../sourceIcon'
import { formatSavedAt } from '../sourceTime'
import type { SourceCard } from '../knowledgeTypes'
import styles from './NodeInspector.module.css'

interface NodeInspectorProps {
  node: GraphNode
  graph: GraphResponse
  sourcesById: Map<string, SourceCard>
  onSelect: (nodeId: string) => void
  onClose: () => void
}

/**
 * 고른 노드가 무엇과 이어져 있는지 읽는 자리(UX §6.4·§6.5).
 *
 * 이웃은 이미 받아 둔 edges에서 뽑는다. 노드를 누를 때마다 서버를 다시 부르지 않는다.
 */
const SOURCES_HEADING: Record<GraphNodeType, string> = {
  FOLDER: '포함된 문서',
  CATEGORY: '포함된 문서',
  SOURCE: '문서',
  SUBJECT: '연결된 문서',
  TOPIC: '참고 문서',
}

const SUBJECTS_HEADING: Record<GraphNodeType, string> = {
  FOLDER: '대표 키워드',
  CATEGORY: '연결된 키워드',
  SOURCE: '다루는 키워드',
  SUBJECT: '키워드',
  TOPIC: '연결된 키워드',
}

export default function NodeInspector({
  node,
  graph,
  sourcesById,
  onSelect,
  onClose,
}: NodeInspectorProps) {
  const sourceNodeIds = graph.nodes
    .filter((candidate) => candidate.type === 'SOURCE')
    .map((candidate) => candidate.nodeId)
  const neighborIds = neighborsOf(node.nodeId, graph.edges, sourceNodeIds)
  const neighbors = graph.nodes.filter((candidate) => neighborIds.has(candidate.nodeId))

  const sources = neighbors.filter((candidate) => candidate.type === 'SOURCE')
  const topics = neighbors.filter((candidate) => candidate.type === 'TOPIC')
  const subjects = neighbors.filter((candidate) => candidate.type === 'SUBJECT')
  const categories = (node.type === 'FOLDER' ? graph.nodes : neighbors)
    .filter((candidate) => candidate.type === 'CATEGORY')
  const selfSource = sourcesById.get(node.nodeId)
  const savedAt = selfSource ? formatSavedAt(selfSource.createdAt) : null

  return (
    <aside className={styles.inspector} aria-label={`${node.title} 상세`}>
      <header className={styles.header}>
        <div className={styles['header-top']}>
          <div className={styles['header-labels']}>
            <span className={styles.badge} data-type={node.type}>{NODE_TYPE_LABEL[node.type]}</span>
            {node.type === 'SOURCE' && categories.map((category) => (
              <button
                key={category.nodeId}
                type="button"
                className={styles['category-chip']}
                onClick={() => onSelect(category.nodeId)}
              >
                {category.title}
              </button>
            ))}
          </div>
          <button type="button" className={styles.close} aria-label="패널 닫기" onClick={onClose}>
            <IconX size={15} stroke={1.8} aria-hidden="true" />
          </button>
        </div>
        <h4 className={node.type === 'SOURCE' ? styles['source-title'] : undefined}>{node.title}</h4>
        {node.type === 'SOURCE' && (selfSource?.domain || savedAt) && (
          <p className={styles.domain}>
            {sourceMark(selfSource?.domain ?? null, 16, styles.mark)}
            {[selfSource?.domain, savedAt].filter(Boolean).join(' · ')}
          </p>
        )}
        {selfSource?.summary && <p className={styles.summary}>{selfSource.summary}</p>}
      </header>

      {node.type === 'SOURCE' && selfSource && (
        <a
          className={styles.origin}
          href={selfSource.url}
          target="_blank"
          rel="noreferrer noopener"
        >
          원문 열기
          <IconExternalLink size={14} stroke={1.8} aria-hidden="true" />
        </a>
      )}

      {sources.length > 0 && node.type !== 'SOURCE' && (
        <section className={styles.section}>
          <h5>{SOURCES_HEADING[node.type]}</h5>
          <ul>
            {sources.map((source) => {
              const card = sourcesById.get(source.nodeId)
              return (
                <li key={source.nodeId}>
                  <button type="button" onClick={() => onSelect(source.nodeId)}>
                    <span className={`${styles['item-title']} ${styles['source-title']}`}>
                      {source.title}
                    </span>
                    <span className={styles['item-meta']}>
                      {sourceMark(card?.domain ?? null, 15, styles.mark)}
                      {card?.domain}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      {topics.length > 0 && (
        <section className={styles.section}>
          <h5>연관된 주제</h5>
          <ul>
            {topics.map((topic) => (
              <li key={topic.nodeId}>
                <button type="button" onClick={() => onSelect(topic.nodeId)}>
                  <span className={styles['item-title']}>{topic.title}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {categories.length > 0 && node.type !== 'SOURCE' && (
        <section className={styles.section}>
          <h5>카테고리</h5>
          <ul>
            {categories.map((category) => (
              <li key={category.nodeId}>
                <button type="button" onClick={() => onSelect(category.nodeId)}>
                  <span className={styles['item-title']}>{category.title}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {subjects.length > 0 && (
        <section className={styles.section}>
          <h5>{SUBJECTS_HEADING[node.type]}</h5>
          <div className={styles.chips}>
            {subjects.map((subject) => (
              <button
                key={subject.nodeId}
                type="button"
                className={styles.chip}
                onClick={() => onSelect(subject.nodeId)}
              >
                {subject.title}
              </button>
            ))}
          </div>
        </section>
      )}

      {neighbors.length === 0 && (
        <p className={styles.empty}>
          아직 이어진 것이 없어요. 내용 정리가 끝나면 키워드와 주제가 붙습니다.
        </p>
      )}
    </aside>
  )
}
