import type { GraphNode, GraphResponse } from './graphTypes'
import type { SourceCard } from '../knowledgeTypes'
import { sourceMark } from '../sourceIcon'
import styles from './SubjectDocumentGroups.module.css'

interface SubjectDocumentGroupsProps {
  graph: GraphResponse
  sourcesById: Map<string, SourceCard>
  selectedNodeId: string | null
  onSelect: (nodeId: string) => void
}

interface SubjectGroup {
  subject: GraphNode
  sources: GraphNode[]
}

/** Subject마다 같은 키워드를 가진 문서를 한 줄에 모아 비교하는 보기다. */
export default function SubjectDocumentGroups({
  graph,
  sourcesById,
  selectedNodeId,
  onSelect,
}: SubjectDocumentGroupsProps) {
  const nodesById = new Map(graph.nodes.map((node) => [node.nodeId, node]))
  const sourceOrder = new Map(
    graph.nodes
      .filter((node) => node.type === 'SOURCE')
      .map((node, index) => [node.nodeId, index]),
  )
  const subjectOrder = new Map(
    graph.nodes
      .filter((node) => node.type === 'SUBJECT')
      .map((node, index) => [node.nodeId, index]),
  )
  const subjectToSourceIds = new Map<string, Set<string>>()

  for (const edge of graph.edges) {
    const from = nodesById.get(edge.from)
    const to = nodesById.get(edge.to)
    const source = from?.type === 'SOURCE' ? from : to?.type === 'SOURCE' ? to : null
    const subject = from?.type === 'SUBJECT' ? from : to?.type === 'SUBJECT' ? to : null
    if (!source || !subject) continue

    const sourceIds = subjectToSourceIds.get(subject.nodeId) ?? new Set<string>()
    sourceIds.add(source.nodeId)
    subjectToSourceIds.set(subject.nodeId, sourceIds)
  }

  const groups: SubjectGroup[] = graph.nodes
    .filter((node) => node.type === 'SUBJECT' && subjectToSourceIds.has(node.nodeId))
    .map((subject) => ({
      subject,
      sources: [...(subjectToSourceIds.get(subject.nodeId) ?? [])]
        .map((sourceId) => nodesById.get(sourceId))
        .filter((node): node is GraphNode => node?.type === 'SOURCE')
        .sort((a, b) => (sourceOrder.get(a.nodeId) ?? 0) - (sourceOrder.get(b.nodeId) ?? 0)),
    }))
    .sort((a, b) => b.sources.length - a.sources.length
      || (subjectOrder.get(a.subject.nodeId) ?? 0) - (subjectOrder.get(b.subject.nodeId) ?? 0))

  if (groups.length === 0) {
    return (
      <div className={styles.empty}>
        <p>아직 함께 묶어 볼 키워드가 없어요.</p>
        <p>문서 분석이 끝나면 같은 키워드를 가진 문서가 여기에 모입니다.</p>
      </div>
    )
  }

  const selectedType = selectedNodeId ? nodesById.get(selectedNodeId)?.type : null
  const selectedSourceSubjects = selectedType === 'SOURCE'
    ? new Set(groups
      .filter((group) => group.sources.some((source) => source.nodeId === selectedNodeId))
      .map((group) => group.subject.nodeId))
    : new Set<string>()

  return (
    <div className={styles.view}>
      <header className={styles.intro}>
        <p>같은 키워드를 가진 문서를 모아봤어요.</p>
        <span>문서는 가진 키워드마다 여러 줄에 나타날 수 있어요.</span>
      </header>

      <div className={styles.groups}>
        {groups.map(({ subject, sources }) => {
          const related = selectedType === 'SUBJECT'
            ? subject.nodeId === selectedNodeId
            : selectedType === 'SOURCE' && selectedSourceSubjects.has(subject.nodeId)
          const dimmed = selectedNodeId !== null
            && (selectedType === 'SOURCE' || selectedType === 'SUBJECT')
            && !related

          return (
            <section
              className={styles.group}
              key={subject.nodeId}
              data-related={related || undefined}
              data-dimmed={dimmed || undefined}
              aria-labelledby={`subject-group-${subject.nodeId}`}
            >
              <button
                type="button"
                className={styles.subject}
                id={`subject-group-${subject.nodeId}`}
                data-selected={subject.nodeId === selectedNodeId || undefined}
                onClick={() => onSelect(subject.nodeId)}
              >
                <span>{subject.title}</span>
                <small>문서 {sources.length}</small>
              </button>

              <div className={styles.documents}>
                {sources.map((source) => {
                  const card = sourcesById.get(source.nodeId)
                  const subjects = card?.subjects?.slice(0, 4) ?? []
                  const selected = source.nodeId === selectedNodeId
                  return (
                    <button
                      type="button"
                      className={styles.document}
                      key={`${subject.nodeId}-${source.nodeId}`}
                      data-selected={selected || undefined}
                      data-dimmed={selectedType === 'SOURCE' && !selected || undefined}
                      onClick={() => onSelect(source.nodeId)}
                    >
                      <strong>{source.title}</strong>
                      {card?.domain && (
                        <span className={styles.domain}>
                          {sourceMark(card.domain, 14, styles.mark)}
                          {card.domain}
                        </span>
                      )}
                      {subjects.length > 0 && (
                        <span className={styles.keywords} aria-label={`키워드 ${subjects.length}개`}>
                          {subjects.map((item) => (
                            <span
                              key={item.nodeId}
                              data-current={item.nodeId === subject.nodeId || undefined}
                            >
                              {item.title}
                            </span>
                          ))}
                        </span>
                      )}
                    </button>
                  )
                })}
              </div>
            </section>
          )
        })}
      </div>
    </div>
  )
}
