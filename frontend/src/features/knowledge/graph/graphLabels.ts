import type { GraphNodeType } from './graphTypes'

/** 내부 타입 이름을 그대로 노출하지 않는다(UX §11). */
export const NODE_TYPE_LABEL: Record<GraphNodeType, string> = {
  FOLDER: '폴더',
  CATEGORY: '카테고리',
  SOURCE: '문서',
  SUBJECT: '키워드',
  TOPIC: '주제',
}
