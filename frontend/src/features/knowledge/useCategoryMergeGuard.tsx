import { useCallback, useEffect, useRef, useState } from 'react'
import { IconX } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import modalStyles from '../../components/ModalShell.module.css'
import type { ApiError } from '../../api/client'
import { mergeCategory, updateNodeTitle } from './knowledgeApi'
import type { NodeRef } from './knowledgeTypes'
import styles from './useCategoryMergeGuard.module.css'

interface Pending {
  target: NodeRef
  decide: (merge: boolean) => void
}

/** PATCH 중복 응답에서 받은 대상으로 문서 한 건 이동 또는 전체 병합을 확인한다. */
export function useCategoryMergeGuard(scope: 'category' | 'source' = 'category') {
  const [pending, setPending] = useState<Pending | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    // 이미 열려 있는데 다시 열면 예외가 난다.
    if (pending && !dialogRef.current?.open) dialogRef.current?.showModal()
  }, [pending])

  /**
   * 물어볼 것이 생겼을 때만 답을 기다린다. 답은 모달의 버튼이 resolve로 넣어 준다.
   *
   * 신원을 고정해 둔다. 이것을 쓰는 저장 함수가 useCallback으로 묶여 있고, 그 함수가 매
   * 렌더 새로 만들어지면 그래프가 ReactFlow에 매번 새 노드 배열을 넘겨 렌더가 멈추지 않는다.
   */
  const ask = useCallback((target: NodeRef): Promise<boolean> => {
    return new Promise<boolean>((resolve) => {
      setPending({
        target,
        decide: (merge) => {
          dialogRef.current?.close()
          setPending(null)
          resolve(merge)
        },
      })
    })
  }, [])

  const saveTitle = useCallback(async (nodeId: string, title: string, sourceId?: string): Promise<NodeRef | null> => {
    try {
      return await updateNodeTitle(nodeId, title)
    } catch (error: unknown) {
      const apiError = error as ApiError | null
      if (apiError?.status !== 409 || apiError.code !== 'KNOWLEDGE_CATEGORY_TITLE_DUPLICATE'
          || !apiError.targetCategory) throw error
      if (!await ask(apiError.targetCategory)) return null
      return mergeCategory(nodeId, apiError.targetCategory.nodeId, sourceId)
    }
  }, [ask])

  const dialog = pending && (
    <dialog
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      aria-labelledby="category-merge-title"
      aria-describedby="category-merge-description"
      onCancel={(event) => {
        event.preventDefault()
        pending.decide(false)
      }}
    >
      <div className={`${styles.surface} ${modalStyles.surface}`}>
        <header className={`${styles.header} ${modalStyles.header}`}>
          <h2 id="category-merge-title">{scope === 'source' ? '카테고리 옮기기' : '카테고리 합치기'}</h2>
          <button
            type="button"
            className={styles.close}
            aria-label={scope === 'source' ? '카테고리 옮기기 닫기' : '카테고리 합치기 닫기'}
            onClick={() => pending.decide(false)}
          >
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>
        <div className={styles.body} id="category-merge-description">
          <p className={styles.question}>
            ‘<strong>{pending.target.title}</strong>’으로 {scope === 'source' ? '이 문서를 옮길까요?' : '문서를 모을까요?'}
          </p>
          <p className={styles.description}>
            {scope === 'source'
              ? '이 문서만 옮겨지고, 다른 문서의 카테고리는 유지돼요.'
              : '문서는 해당 카테고리로 옮겨지고, 기존 카테고리는 삭제돼요.'}
          </p>
        </div>
        <footer className={styles.actions}>
          <ActionButton className={styles.confirm} onClick={() => pending.decide(true)}>{scope === 'source' ? '옮기기' : '합치기'}</ActionButton>
          <ActionButton className={styles.cancel} variant="plain" onClick={() => pending.decide(false)}>취소</ActionButton>
        </footer>
      </div>
    </dialog>
  )

  return { saveTitle, dialog }
}
