import { useMemo } from 'react'
import { BaseEdge, getSmoothStepPath } from '@xyflow/react'
import type { Edge, EdgeProps } from '@xyflow/react'
import styles from './GraphFlowEdge.module.css'

export type GraphFlowEdgeData = {
  active: boolean
  /** 점을 그린 방향과 반대로(target → source) 보낼 때 true. 점은 늘 출발 노드에서 멀어진다. */
  reverse: boolean
} & Record<string, unknown>

export type GraphFlowEdge = Edge<GraphFlowEdgeData, 'flow'>

/** 다른 관계선과 같은 모양을 쓴다. 강조할 때만 모양이 바뀌면 선이 튀어 보인다. */
export const GRAPH_EDGE_BORDER_RADIUS = 18

/** 모든 점이 같은 속도(px/s)로 움직이고 같은 주기로 함께 다시 출발한다. */
const DOT_SPEED = 160
const DOT_CYCLE_SECONDS = 3

let measuringPath: SVGPathElement | null = null

/** 선 길이. 측정할 수 없으면 꺾인 선의 가로·세로 거리 합으로 근사한다. */
function pathLength(path: string, fallback: number) {
  try {
    measuringPath ??= document.createElementNS('http://www.w3.org/2000/svg', 'path')
    measuringPath.setAttribute('d', path)
    const length = measuringPath.getTotalLength()
    return length > 0 ? length : fallback
  } catch {
    return fallback
  }
}

/**
 * 고른 노드에서 이어진 선. 카테고리는 갈래 전체, 문서·주제·키워드는 닿은 선이다.
 * 선은 실선 그대로 두고, 작은 빛 점 하나가 선을 따라 지나가며 흐름의 방향만 알린다.
 * React Flow의 애니메이션 간선 예제와 같은 SVG animateMotion 방식이다.
 *
 * 길이와 상관없이 같은 시간에 돌면 선마다 속도가 달라, 한 노드에서 함께 나가는 선들의 겹친 구간에서
 * 점이 흩어져 여러 개로 보인다. 그래서 속도를 맞추고 모두 문서 시계의 같은 순간에 출발시킨다.
 * 겹친 구간에서는 점이 하나로 겹쳐 보이고, 끝에 닿은 점은 다음 주기까지 감춘다.
 */
export default function GraphFlowEdgeView({
  id,
  sourceX,
  sourceY,
  sourcePosition,
  targetX,
  targetY,
  targetPosition,
  style,
  data,
}: EdgeProps<GraphFlowEdge>) {
  const [path] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: GRAPH_EDGE_BORDER_RADIUS,
  })

  const length = useMemo(
    () => pathLength(path, Math.abs(targetX - sourceX) + Math.abs(targetY - sourceY)),
    [path, sourceX, sourceY, targetX, targetY],
  )
  // 한 주기 중 움직이는 비율. 아주 긴 선은 주기 전체를 써서 조금 느리게 간다.
  const travel = Math.min(1, length / (DOT_SPEED * DOT_CYCLE_SECONDS)).toFixed(3)
  const [from, to] = data?.reverse ? ['1', '0'] : ['0', '1']

  return (
    <>
      <BaseEdge id={id} path={path} style={style} />
      <circle className={styles.dot} r={3.5} aria-hidden="true">
        <animateMotion
          begin="0s"
          dur={`${DOT_CYCLE_SECONDS}s`}
          repeatCount="indefinite"
          path={path}
          calcMode="linear"
          keyPoints={`${from};${to};${to}`}
          keyTimes={`0;${travel};1`}
        />
        <animate
          attributeName="opacity"
          begin="0s"
          dur={`${DOT_CYCLE_SECONDS}s`}
          repeatCount="indefinite"
          calcMode="discrete"
          values="1;0"
          keyTimes={`0;${travel}`}
        />
      </circle>
    </>
  )
}
