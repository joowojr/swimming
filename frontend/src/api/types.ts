/**
 * 커서로 이어 읽는 목록의 응답 모양. 백엔드의 CursorPage와 짝이다.
 *
 * hasNext는 nextCursor가 있는지와 같다. 더 있는지를 커서의 존재로 유추하지 않아도 되게
 * 함께 온다.
 */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
  hasNext: boolean
}

/** 커서 목록 API가 공통으로 받는 조건. */
export interface CursorPageQuery {
  size?: number
  cursor?: string
}
