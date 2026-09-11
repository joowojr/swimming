import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
} from '@xyflow/react'
import type { Edge, ReactFlowInstance } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import type { ApiError } from '../../../api/client'
import GraphLayoutMenu from './GraphLayoutMenu'
import GraphNodeCard from './GraphNodeCard'
import type { KnowledgeFlowNode } from './GraphNodeCard'
import NodeInspector from './NodeInspector'
import { getFolderGraph } from './graphApi'
import { layoutGraph, rootNodeId } from './graphLayout'
import type { LayoutOptions } from './graphLayout'
import { neighborsOf, touchesNode } from './graphNeighbors'
import { numberTopics } from './topicOrder'
import type { GraphResponse } from './graphTypes'
import type { SourceCard } from '../knowledgeTypes'
import styles from './KnowledgeGraph.module.css'

interface KnowledgeGraphProps {
  folderId: number
  /** 목록에서 이미 받아 둔 카드. sourceId와 nodeId가 같아 SOURCE 노드에 붙는다. */
  sources: SourceCard[]
}

type GraphState =
  | { status: 'loading' }
  | { status: 'ready'; graph: GraphResponse }
  | { status: 'error'; message: string }

const nodeTypes = { knowledge: GraphNodeCard }
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
export default function KnowledgeGraph({ folderId, sources }: KnowledgeGraphProps) {
  const [state, setState] = useState<GraphState>({ status: 'loading' })
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [layout, setLayout] = useState<Required<LayoutOptions>>({
    axis: 'horizontal',
    order: 'source-first',
    sort: 'linked',
  })
  const [showSubjectDetails, setShowSubjectDetails] = useState(true)
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

  const { nodes, edges } = useMemo(() => {
    if (!graph) return { nodes: [] as KnowledgeFlowNode[], edges: [] as Edge[] }

    const sourceNodeIds = graph.nodes
      .filter((node) => node.type === 'SOURCE')
      .map((node) => node.nodeId)
    const showAllSubjects = sourceNodeIds.length <= MAX_SOURCES_WITH_VISIBLE_SUBJECTS
    const highlighted = selectedNodeId
      ? neighborsOf(selectedNodeId, graph.edges, sourceNodeIds)
      : null

    const topicNumbers = numberTopics(graph)
    const positionedNodes = layoutGraph(graph, layout)
    const selectedSubjectId = graph.nodes.some(
      (node) => node.nodeId === selectedNodeId && node.type === 'SUBJECT',
    ) ? selectedNodeId : null
    const hiddenSubjects = showAllSubjects || showSubjectDetails
      ? []
      : positionedNodes.filter(
        ({ node }) => node.type === 'SUBJECT' && node.nodeId !== selectedSubjectId,
      )
    const hiddenSubjectIds = new Set(hiddenSubjects.map(({ node }) => node.nodeId))

    const visiblePositionedNodes = positionedNodes.filter(
      ({ node }) => !hiddenSubjectIds.has(node.nodeId),
    )
    const positionedById = new Map(positionedNodes.map((entry) => [entry.node.nodeId, entry]))
    const nodeById = new Map(graph.nodes.map((node) => [node.nodeId, node]))

    // 축소 상태에서는 전체 Subject를 한 덩어리로 만들지 않는다. Topic마다 직접 이어진
    // Subject만 세어 요약해야 어떤 목적에 딸린 태그인지 공간적으로 남는다.
    const subjectSummaries = showAllSubjects || showSubjectDetails
      ? []
      : positionedNodes
        .filter(({ node }) => node.type === 'TOPIC')
        .flatMap((topicEntry) => {
          const subjectIds = new Set(
            graph.edges.flatMap((edge) => {
              const neighborId = edge.from === topicEntry.node.nodeId
                ? edge.to
                : edge.to === topicEntry.node.nodeId
                  ? edge.from
                  : null
              return neighborId
                && hiddenSubjectIds.has(neighborId)
                && nodeById.get(neighborId)?.type === 'SUBJECT'
                ? [neighborId]
                : []
            }),
          )
          const subjects = [...subjectIds]
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
        },
        ariaLabel: subjectSummary
          ? `${subjectSummary.topicTitle}의 태그 ${subjectSummary.count}개 펼쳐 보기`
          : undefined,
      }
    })

    // Folder→Source 간선은 응답에 없다. root에 달린 SOURCE가 곧 소속이라(§6.1) 그 소속을
    // 화면에서만 선으로 잇는다.
    // 연결점은 노드의 왼·오른쪽에 있다. 열 순서를 뒤집으면 선이 뒤로 돌아 나가므로 양 끝을
    // 맞바꿔 그린다. 화살표를 그리지 않으므로 선의 모양만 달라진다.
    const flip = layout.order === 'subject-first'

    // 열 배치라 선이 꺾이는 지점이 정해져 있다. 곡선보다 직각으로 꺾는 편이 겹칠 때 서로를
    // 덜 가리고, 같은 노드로 들어가는 선들이 한 줄기로 모여 보인다.
    const shape = { type: 'smoothstep', pathOptions: { borderRadius: 18 } } as const

    const belongsEdges: Edge[] = graph.root.type === 'FOLDER'
      ? sourceNodeIds.map((sourceNodeId) => ({
        ...shape,
        id: `belongs-${sourceNodeId}`,
        source: flip ? sourceNodeId : rootNodeId,
        target: flip ? rootNodeId : sourceNodeId,
        className: `${styles.edge} ${styles['edge-belongs']}`,
        data: { active: selectedNodeId === rootNodeId },
      }))
      : []

    const relationEdges: Edge[] = graph.edges
      .filter((edge) => !hiddenSubjectIds.has(edge.from) && !hiddenSubjectIds.has(edge.to))
      .map((edge) => ({
        ...shape,
        id: `${edge.from}-${edge.to}-${edge.kind}`,
        source: flip ? edge.to : edge.from,
        target: flip ? edge.from : edge.to,
        className: `${styles.edge} ${edge.kind === 'USED_FOR' ? styles['edge-used-for'] : ''}`,
        data: { active: selectedNodeId !== null && touchesNode(edge, selectedNodeId) },
      }))

    const summaryEdges: Edge[] = subjectSummaries.map((summary) => ({
      ...shape,
      id: `summary-${summary.topicId}`,
      source: flip ? summary.id : summary.topicId,
      target: flip ? summary.topicId : summary.id,
      className: `${styles.edge} ${styles['edge-summary']}`,
      data: { active: selectedNodeId === summary.topicId },
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
  }, [graph, layout, selectedNodeId, showSubjectDetails, sourcesById])

  const selectedNode = graph?.nodes.find((node) => node.nodeId === selectedNodeId)
    ?? (selectedNodeId === rootNodeId && graph
      ? { nodeId: rootNodeId, type: 'FOLDER' as const, title: graph.root.title, createdAt: null }
      : undefined)

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
        <p className={styles.hint}>링크를 저장하면 문서와 개념이 여기에 이어집니다.</p>
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
            <span data-type="SOURCE">문서</span>
            <span data-type="TOPIC">목적</span>
            <span data-type="SUBJECT">태그</span>
          </div>

          {state.graph.truncated && (
            <p className={styles.truncated}>
              문서가 많아 일부만 먼저 그렸어요.
            </p>
          )}
        </div>

        {selectedNode && (
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
