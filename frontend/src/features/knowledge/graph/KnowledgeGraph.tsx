import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
} from '@xyflow/react'
import type { Edge, ReactFlowInstance } from '@xyflow/react'
import { IconSparkles, IconX } from '@tabler/icons-react'
import '@xyflow/react/dist/style.css'
import type { ApiError } from '../../../api/client'
import ActionButton from '../../../components/ActionButton'
import GraphLayoutMenu from './GraphLayoutMenu'
import GraphFlowEdgeView, { GRAPH_EDGE_BORDER_RADIUS } from './GraphFlowEdge'
import GraphNodeCard from './GraphNodeCard'
import type { KnowledgeFlowNode } from './GraphNodeCard'
import NodeInspector from './NodeInspector'
import CategoryOrganizer from './CategoryOrganizer'
import { getFolderGraph } from './graphApi'
import { layoutGraph, rootNodeId } from './graphLayout'
import type { LayoutOptions } from './graphLayout'
import { categoryReachOf, neighborsOf, subjectReachOf, touchesNode, withoutRedundantAbout } from './graphNeighbors'
import { numberTopics } from './topicOrder'
import type { GraphResponse } from './graphTypes'
import type { NodeRef, SourceCard } from '../knowledgeTypes'
import { updateNodeTitle } from '../knowledgeApi'
import type { CategoryReplaceResponse } from './categoryTypes'
import styles from './KnowledgeGraph.module.css'

interface KnowledgeGraphProps {
  folderId: number
  /** 목록에서 이미 받아 둔 카드. sourceId와 nodeId가 같아 SOURCE 노드에 붙는다. */
  sources: SourceCard[]
  onCategoriesReplaced: (response: CategoryReplaceResponse) => void
  onNodeTitleChanged: (node: NodeRef) => void
}

type GraphState =
  | { status: 'loading' }
  | { status: 'ready'; graph: GraphResponse }
  | { status: 'error'; message: string }

const nodeTypes = { knowledge: GraphNodeCard }
const edgeTypes = { flow: GraphFlowEdgeView }
const CATEGORY_PREVIEW_MIN_SOURCES = 6
const CATEGORY_PREVIEW_MAX_SOURCES = 50
const CATEGORY_HINT_STORAGE_PREFIX = 'knowledge-category-hint-dismissed:'
const SUBJECT_SUMMARY_NODE_PREFIX = '__subject-summary__:'
const MAX_SOURCES_WITH_VISIBLE_SUBJECTS = 4
const SUBJECT_DETAIL_ZOOM = 0.7
const SUBJECT_DETAIL_TARGET_ZOOM = 0.78
const SUBJECT_SUMMARY_POSITION_RATIO = 0.62

function errorMessage(error: unknown) {
  const apiMessage = typeof error === 'object' && error !== null
    ? (error as ApiError).message
    : undefined
  return apiMessage ?? '지식 그래프를 불러오지 못했습니다.'
}

/**
 * Folder를 진입점으로 하는 지식 그래프.
 *
 * 그래프 자체를 보여 주는 화면이 아니라 관계를 따라 문서를 소화하는 Navigation UI다(UX §6).
 * 그래서 배치는 매번 같고(graphLayout), 노드를 고르면 1-hop만 남기고 흐려진다.
 */
export default function KnowledgeGraph({ folderId, sources, onCategoriesReplaced, onNodeTitleChanged }: KnowledgeGraphProps) {
  const [state, setState] = useState<GraphState>({ status: 'loading' })
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [layout, setLayout] = useState<Required<LayoutOptions>>({
    axis: 'horizontal',
    order: 'source-first',
    sort: 'linked',
  })
  const [showSubjectDetails, setShowSubjectDetails] = useState(true)
  const [isCategoryHintDismissed, setIsCategoryHintDismissed] = useState(
    () => localStorage.getItem(`${CATEGORY_HINT_STORAGE_PREFIX}${folderId}`) === 'true',
  )
  const [hideCategoryHintAgain, setHideCategoryHintAgain] = useState(false)
  const [isOrganizingCategories, setIsOrganizingCategories] = useState(false)
  const [requestKey, setRequestKey] = useState(0)
  const flowInstanceRef = useRef<ReactFlowInstance<KnowledgeFlowNode, Edge> | null>(null)

  useEffect(() => {
    let active = true

    void getFolderGraph(folderId)
      .then((graph) => {
        if (active) setState({ status: 'ready', graph })
      })
      .catch((error: unknown) => {
        if (active) setState({ status: 'error', message: errorMessage(error) })
      })

    return () => { active = false }
  }, [folderId, requestKey])

  const sourcesById = useMemo(
    () => new Map(sources.map((source) => [source.sourceId, source])),
    [sources],
  )

  const graph = state.status === 'ready' ? state.graph : null

  const saveNodeTitle = useCallback(async (nodeId: string, title: string) => {
    const updated = await updateNodeTitle(nodeId, title)
    setState((current) => current.status === 'ready' ? {
      ...current,
      graph: {
        ...current.graph,
        nodes: current.graph.nodes.map((node) => node.nodeId === updated.nodeId
          ? { ...node, title: updated.title }
          : node),
      },
    } : current)
    onNodeTitleChanged(updated)
  }, [onNodeTitleChanged])

  // 선택은 강조만 바꾼다. 전체 배치와 관계 전처리는 그래프·배치 설정이 바뀔 때만 계산한다.
  const drawnEdges = useMemo(() => graph ? withoutRedundantAbout(graph.edges) : [], [graph])
  const topicNumbers = useMemo(() => graph ? numberTopics(graph) : new Map<string, number>(), [graph])
  const positionedNodes = useMemo(() => graph ? layoutGraph(graph, layout) : [], [graph, layout])
  const positionedById = useMemo(
    () => new Map(positionedNodes.map((entry) => [entry.node.nodeId, entry])),
    [positionedNodes],
  )
  const nodeById = useMemo(() => new Map(graph?.nodes.map((node) => [node.nodeId, node]) ?? []), [graph])
  const subjectIdsByTopic = useMemo(() => {
    const result = new Map<string, Set<string>>()
    for (const edge of graph?.edges ?? []) {
      for (const [topicId, subjectId] of [[edge.from, edge.to], [edge.to, edge.from]]) {
        if (nodeById.get(topicId)?.type !== 'TOPIC' || nodeById.get(subjectId)?.type !== 'SUBJECT') continue
        const ids = result.get(topicId) ?? new Set<string>()
        ids.add(subjectId)
        result.set(topicId, ids)
      }
    }
    return result
  }, [graph, nodeById])

  const { nodes, edges } = useMemo(() => {
    if (!graph) return { nodes: [] as KnowledgeFlowNode[], edges: [] as Edge[] }

    const sourceNodeIds = graph.nodes
      .filter((node) => node.type === 'SOURCE')
      .map((node) => node.nodeId)
    const showAllSubjects = sourceNodeIds.length <= MAX_SOURCES_WITH_VISIBLE_SUBJECTS
    // 강조도 화면에 그린 선을 따른다. 선이 없는 노드가 밝게 남으면 왜 이어졌는지 보이지 않는다.
    const selectedType = graph.nodes.find((node) => node.nodeId === selectedNodeId)?.type
    // 문서·주제를 고르면 닿은 선에 빛 점을 보낸다. 폴더는 소속선뿐이라 제외한다.
    const flowsFromSelected = selectedType === 'SOURCE' || selectedType === 'TOPIC'
    // 카테고리는 문서 → 주제 → 키워드로 내려가고, 키워드는 주제 → 문서로 거슬러 올라가 갈래 전체를 강조한다.
    const reach = selectedNodeId === null
      ? null
      : selectedType === 'CATEGORY'
        ? categoryReachOf(selectedNodeId, drawnEdges)
        : selectedType === 'SUBJECT'
          ? subjectReachOf(selectedNodeId, drawnEdges)
          : null
    const highlighted = reach?.nodeIds ?? (selectedNodeId
      ? neighborsOf(selectedNodeId, drawnEdges, graph.nodes
          .filter((node) => node.type === 'SOURCE' || node.type === 'CATEGORY')
          .map((node) => node.nodeId))
      : null)

    const selectedSubjectId = graph.nodes.some(
      (node) => node.nodeId === selectedNodeId && node.type === 'SUBJECT',
    ) ? selectedNodeId : null
    // 고른 카테고리·키워드의 갈래에 든 키워드는 축소 중이어도 펼쳐 둔다. 강조한 갈래가 끝까지 보여야 한다.
    const hiddenSubjects = showAllSubjects || showSubjectDetails
      ? []
      : positionedNodes.filter(
        ({ node }) => node.type === 'SUBJECT'
          && node.nodeId !== selectedSubjectId
          && !reach?.nodeIds.has(node.nodeId),
      )
    const hiddenSubjectIds = new Set(hiddenSubjects.map(({ node }) => node.nodeId))

    const visiblePositionedNodes = positionedNodes.filter(
      ({ node }) => !hiddenSubjectIds.has(node.nodeId),
    )

    // 축소 상태에서는 전체 Subject를 한 덩어리로 만들지 않는다. Topic마다 직접 이어진
    // Subject만 세어 요약해야 어떤 주제에 딸린 키워드인지 공간적으로 남는다.
    const subjectSummaries = showAllSubjects || showSubjectDetails
      ? []
      : positionedNodes
        .filter(({ node }) => node.type === 'TOPIC')
        .flatMap((topicEntry) => {
          const subjects = [...(subjectIdsByTopic.get(topicEntry.node.nodeId) ?? [])]
            .filter((subjectId) => hiddenSubjectIds.has(subjectId))
            .map((subjectId) => positionedById.get(subjectId))
            .filter((entry) => entry !== undefined)
          if (subjects.length === 0) return []

          const subjectCenter = subjects.reduce(
            (sum, entry) => ({ x: sum.x + entry.x, y: sum.y + entry.y }),
            { x: 0, y: 0 },
          )
          subjectCenter.x /= subjects.length
          subjectCenter.y /= subjects.length

          const id = `${SUBJECT_SUMMARY_NODE_PREFIX}${topicEntry.node.nodeId}`
          return [{
            id,
            topicId: topicEntry.node.nodeId,
            topicTitle: topicEntry.node.title,
            count: subjects.length,
            x: topicEntry.x
              + (subjectCenter.x - topicEntry.x) * SUBJECT_SUMMARY_POSITION_RATIO,
            y: topicEntry.y
              + (subjectCenter.y - topicEntry.y) * SUBJECT_SUMMARY_POSITION_RATIO,
          }]
        })
    const subjectSummaryById = new Map(subjectSummaries.map((summary) => [summary.id, summary]))
    visiblePositionedNodes.push(...subjectSummaries.map((summary) => ({
      node: {
        nodeId: summary.id,
        type: 'SUBJECT' as const,
        title: `+${summary.count}`,
        createdAt: null,
      },
      x: summary.x,
      y: summary.y,
    })))

    const flowNodes: KnowledgeFlowNode[] = visiblePositionedNodes.map(({ node, x, y }) => {
      const subjectSummary = subjectSummaryById.get(node.nodeId)
      const summaryIsHighlighted = subjectSummary !== undefined
        && (subjectSummary.topicId === selectedNodeId || highlighted?.has(subjectSummary.topicId))

      return {
        id: node.nodeId,
        type: 'knowledge',
        position: { x, y },
        selected: node.nodeId === selectedNodeId,
        data: {
          node,
          dimmed: highlighted !== null
            && node.nodeId !== selectedNodeId
            && !highlighted.has(node.nodeId)
            && !summaryIsHighlighted,
          sourceCard: sourcesById.get(node.nodeId),
          order: topicNumbers.get(node.nodeId),
          axis: layout.axis,
          subjectSummary: subjectSummary !== undefined,
          onSaveTitle: node.type === 'CATEGORY' || node.type === 'TOPIC' ? saveNodeTitle : undefined,
        },
        ariaLabel: subjectSummary
          ? `${subjectSummary.topicTitle}의 키워드 ${subjectSummary.count}개 펼쳐 보기`
          : undefined,
      }
    })

    // 폴더 소속 간선은 화면에서만 만든다. 분류된 문서는 카테고리를 통해 잇고,
    // 아직 카테고리가 없는 문서는 폴더에 직접 잇는다.
    // 연결점은 노드의 왼·오른쪽에 있다. 열 순서를 뒤집으면 선이 뒤로 돌아 나가므로 양 끝을
    // 맞바꿔 그린다. 화살표를 그리지 않으므로 선의 모양만 달라진다.
    const flip = layout.order === 'subject-first'

    // 열 배치라 선이 꺾이는 지점이 정해져 있다. 곡선보다 직각으로 꺾는 편이 겹칠 때 서로를
    // 덜 가리고, 같은 노드로 들어가는 선들이 한 줄기로 모여 보인다.
    const shape = { type: 'smoothstep', pathOptions: { borderRadius: GRAPH_EDGE_BORDER_RADIUS } } as const

    const categorizedSourceIds = new Set(graph.edges
      .filter((edge) => edge.kind === 'CONTAINS')
      .map((edge) => edge.to))
    const folderChildIds = [
      ...graph.nodes.filter((node) => node.type === 'CATEGORY').map((node) => node.nodeId),
      ...sourceNodeIds.filter((id) => !categorizedSourceIds.has(id)),
    ]
    const belongsEdges: Edge[] = graph.root.type === 'FOLDER'
      ? folderChildIds.map((childId) => ({
        ...shape,
        id: `belongs-${childId}`,
        source: flip ? childId : rootNodeId,
        target: flip ? rootNodeId : childId,
        className: `${styles.edge} ${styles['edge-belongs']}`,
        data: { active: selectedNodeId === rootNodeId },
      }))
      : []

    const relationEdges: Edge[] = drawnEdges
      .filter((edge) => !hiddenSubjectIds.has(edge.from) && !hiddenSubjectIds.has(edge.to))
      .map((edge) => {
        // 빛 점이 출발하는 노드. 카테고리·키워드는 갈래를 따라 고른 노드에서 멀어지고, 문서·주제는 이웃으로 나간다.
        const flowStart = reach
          ? (reach.edges.has(edge) ? edge[reach.flowFrom] : null)
          : flowsFromSelected && selectedNodeId !== null && touchesNode(edge, selectedNodeId)
            ? selectedNodeId
            : null
        const source = flip ? edge.to : edge.from
        return {
          ...shape,
          ...(flowStart ? { type: 'flow' } : {}),
          id: `${edge.from}-${edge.to}-${edge.kind}`,
          source,
          target: flip ? edge.from : edge.to,
          className: `${styles.edge} ${edge.kind === 'SUPPORTS' ? styles['edge-used-for'] : ''} ${flowStart ? styles['edge-flow'] : ''}`,
          data: {
            active: reach
              ? flowStart !== null
              : selectedNodeId !== null && touchesNode(edge, selectedNodeId),
            // 점은 그린 방향(source → target)으로 간다. 출발 노드가 target 쪽이면 거꾸로 보낸다.
            reverse: flowStart !== null && source !== flowStart,
          },
        }
      })

    const summaryEdges: Edge[] = subjectSummaries.map((summary) => ({
      ...shape,
      ...(selectedNodeId === summary.topicId ? { type: 'flow' } : {}),
      id: `summary-${summary.topicId}`,
      source: flip ? summary.id : summary.topicId,
      target: flip ? summary.topicId : summary.id,
      className: `${styles.edge} ${styles['edge-summary']}`,
      data: { active: selectedNodeId === summary.topicId, reverse: flip },
    }))

    // 고른 노드에 닿는 선만 진하게 두고 나머지는 뒤로 물린다. 고르기 전에는 모두 같은 무게다.
    return {
      nodes: flowNodes,
      edges: [...belongsEdges, ...relationEdges, ...summaryEdges].map((edge) => ({
        ...edge,
        className: edge.data?.active ? `${edge.className} ${styles['edge-active']}` : edge.className,
        style: selectedNodeId !== null && !edge.data?.active ? { opacity: 0.12 } : undefined,
      })),
    }
  }, [graph, layout, selectedNodeId, showSubjectDetails, sourcesById, saveNodeTitle, drawnEdges, topicNumbers, positionedNodes, positionedById, subjectIdsByTopic])

  const selectedNode = graph?.nodes.find((node) => node.nodeId === selectedNodeId)
    ?? (selectedNodeId === rootNodeId && graph
      ? { nodeId: rootNodeId, type: 'FOLDER' as const, title: graph.root.title, createdAt: null }
      : undefined)
  // 묶을 대상은 그래프에 그려진 문서다. 화면에서 보고 있는 것과 모델이 보는 것을 맞춘다.
  // 서버가 한 번에 받는 상한이 50이라 여기서 맞춰 자른다.
  const graphSourceIds = useMemo(
    () => (graph?.nodes ?? [])
      .filter((node) => node.type === 'SOURCE')
      .map((node) => node.nodeId)
      .slice(0, CATEGORY_PREVIEW_MAX_SOURCES),
    [graph],
  )
  const sourceCount = graphSourceIds.length
  // 테스트 중에는 카테고리가 있어도 안내를 표시한다. 테스트 후 아래 두 조건을 복원한다.
  // const hasCategories = graph?.nodes.some((node) => node.type === 'CATEGORY') === true
  const showCategoryHint = !isCategoryHintDismissed
    // && !hasCategories
    && sourceCount >= CATEGORY_PREVIEW_MIN_SOURCES

  if (state.status === 'loading') {
    return (
      <div className={styles.state} role="status">
        <p>지식 그래프를 불러오고 있습니다.</p>
      </div>
    )
  }

  if (state.status === 'error') {
    return (
      <div className={styles.state}>
        <p>{state.message}</p>
        <button
          type="button"
          onClick={() => {
            setState({ status: 'loading' })
            setRequestKey((key) => key + 1)
          }}
        >
          다시 불러오기
        </button>
      </div>
    )
  }

  if (nodes.length === 0) {
    return (
      <div className={styles.state}>
        <p>아직 이어 볼 것이 없어요.</p>
        <p className={styles.hint}>링크를 저장하면 문서와 키워드가 여기에 이어집니다.</p>
      </div>
    )
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.toolbar}>
        <GraphLayoutMenu value={layout} onChange={setLayout} />
      </div>

      <div className={styles.board}>
        <div className={styles.canvas}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            nodesDraggable={false}
            nodesConnectable={false}
            edgesFocusable={false}
            fitView
            fitViewOptions={{ padding: 0.2 }}
            minZoom={0.4}
            maxZoom={1.6}
            proOptions={{ hideAttribution: false }}
            onInit={(instance) => { flowInstanceRef.current = instance }}
            onViewportChange={({ zoom }) => {
              const next = zoom >= SUBJECT_DETAIL_ZOOM
              setShowSubjectDetails((current) => current === next ? current : next)
            }}
            onNodeClick={(_, node) => {
              if (node.id.startsWith(SUBJECT_SUMMARY_NODE_PREFIX)) {
                void flowInstanceRef.current?.zoomTo(SUBJECT_DETAIL_TARGET_ZOOM, { duration: 240 })
                return
              }
              setSelectedNodeId(node.id)
            }}
            onPaneClick={() => setSelectedNodeId(null)}
          >
            <Background
              variant={BackgroundVariant.Dots}
              gap={20}
              size={1.3}
              color="var(--color-ash)"
            />
            <Controls showInteractive={false} />
          </ReactFlow>

          <div className={styles.legend}>
            <span data-type="FOLDER">폴더</span>
            {state.graph.nodes.some((node) => node.type === 'CATEGORY') && <span data-type="CATEGORY">카테고리</span>}
            <span data-type="SOURCE">문서</span>
            <span data-type="TOPIC">주제</span>
            <span data-type="SUBJECT">키워드</span>
          </div>
          {showCategoryHint && (
            <aside className={styles['category-hint']} aria-label="카테고리 정리 안내">
              <div className={styles['category-hint-header']}>
                <strong>링크가 많이 쌓였어요.</strong>
                <button
                  type="button"
                  className={styles['category-hint-close']}
                  aria-label="카테고리 정리 안내 닫기"
                  onClick={() => {
                    if (hideCategoryHintAgain) {
                      localStorage.setItem(`${CATEGORY_HINT_STORAGE_PREFIX}${folderId}`, 'true')
                    }
                    setIsCategoryHintDismissed(true)
                  }}
                >
                  <IconX size={15} stroke={1.8} aria-hidden="true" />
                </button>
              </div>
              <p>카테고리로 묶으면 더 쉽게 탐색할 수 있어요.</p>
              <div className={styles['category-hint-actions']}>
                <label className={styles['category-hint-preference']}>
                  <input
                    type="checkbox"
                    checked={hideCategoryHintAgain}
                    onChange={(event) => setHideCategoryHintAgain(event.target.checked)}
                  />
                  다시 보지 않기
                </label>
                <ActionButton
                  className={styles['category-hint-setup']}
                  icon={<IconSparkles size={16} stroke={1.8} aria-hidden="true" />}
                  onClick={() => {
                    if (hideCategoryHintAgain) {
                      localStorage.setItem(`${CATEGORY_HINT_STORAGE_PREFIX}${folderId}`, 'true')
                    }
                    setIsCategoryHintDismissed(true)
                    setSelectedNodeId(null)
                    setIsOrganizingCategories(true)
                  }}
                >
                  AI 카테고리 정리
                </ActionButton>
              </div>
            </aside>
          )}

          {state.graph.truncated && (
            <p className={styles.truncated}>
              문서가 많아 일부만 먼저 그렸어요.
            </p>
          )}
        </div>

        {/* 묶기는 상세를 보던 자리를 그대로 쓴다. 둘을 나란히 두면 같은 폭을 다투고,
            문서를 옮기는 동안 상세를 읽을 일도 없다. */}
        {isOrganizingCategories ? (
          <CategoryOrganizer
            folderId={folderId}
            sourceIds={graphSourceIds}
            sourcesById={sourcesById}
            onClose={() => setIsOrganizingCategories(false)}
            onReplaced={(response) => {
              onCategoriesReplaced(response)
              setIsOrganizingCategories(false)
              setSelectedNodeId(null)
              setRequestKey((current) => current + 1)
            }}
          />
        ) : selectedNode && (
          <NodeInspector
            node={selectedNode}
            graph={state.graph}
            sourcesById={sourcesById}
            onSelect={setSelectedNodeId}
            onClose={() => setSelectedNodeId(null)}
          />
        )}
      </div>
    </div>
  )
}
