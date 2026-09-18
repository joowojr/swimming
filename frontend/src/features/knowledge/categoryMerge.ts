import type { NodeRef } from './knowledgeTypes'

/**
 * 서버의 `NodeTitleNormalizer`와 같은 규칙으로 비교용 키를 만든다.
 *
 * `AWS-OIDC`, `aws_oidc`, `aws oidc`가 모두 `awsoidc`가 된다. 기호는 남긴다 —
 * `C#`과 `C`는 서로 다른 이름이다.
 *
 * 자바의 `\s`와 동일하게 ASCII 공백만 제거한다.
 */
export function normalizeNodeTitle(title: string): string {
  // eslint-disable-next-line no-control-regex -- Java의 ASCII 공백 규칙에 세로 탭이 포함된다.
  return title.normalize('NFKC').replace(/[ \t\n\x0B\f\r_-]+/g, '').toLowerCase()
}

/**
 * 이 이름으로 바꾸면 합쳐질 카테고리를 찾는다. 없으면 null이다.
 *
 * 폴더의 문서 목록을 페이지 단위로 조회하는 쪽에서 사용한다.
 */
export type FindCategoryMergeTarget =
  (nodeId: string, title: string) => Promise<NodeRef | null>

/**
 * 화면이 들고 있는 카테고리 목록에서 찾는다.
 *
 * 대상이 없으면 부르는 쪽이 다음 페이지도 확인한다.
 */
export function findMergeTargetIn(categories: NodeRef[]): FindCategoryMergeTarget {
  return (nodeId, title) => {
    const normalized = normalizeNodeTitle(title)
    const target = categories.find((category) => category.nodeId !== nodeId
      && normalizeNodeTitle(category.title) === normalized)
    return Promise.resolve(target ?? null)
  }
}

/** 합칠 대상을 찾지 않는다. 카테고리가 아닌 노드를 고칠 때 쓴다. */
export const neverMerges: FindCategoryMergeTarget = () => Promise.resolve(null)
