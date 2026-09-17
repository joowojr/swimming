/** Category는 언제나 Folder에 속하므로 경로가 Folder 아래에 있다. */

export interface CategoryPreviewRequest {
  /** 묶을 대상. 6~50개. 폴더 전체를 서버가 알아서 읽지 않는다. */
  sourceIds: string[]
}

/**
 * 초안의 묶음에는 id가 없다. 아직 저장하지 않았기 때문이다.
 * 확정(PUT) 때 전부 새로 만들어지고, 응답의 nodeId로 화면을 갱신한다.
 */
export interface CategoryDraft {
  title: string
  sourceIds: string[]
}

export interface CategoryPreviewResponse {
  categories: CategoryDraft[]
}

export interface CategoryReplaceRequest {
  categories: CategoryDraft[]
}

export interface CategoryReplaceResponse {
  categories: Array<{
    nodeId: string
    title: string
    sourceIds: string[]
  }>
}
