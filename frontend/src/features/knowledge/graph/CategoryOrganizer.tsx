import { useEffect, useRef, useState } from 'react'
import { IconArrowRight, IconPlus, IconSparkles, IconTrash, IconX } from '@tabler/icons-react'
import ActionButton from '../../../components/ActionButton'
import InlineEditableText from '../../../components/InlineEditableText'
import type { ApiError } from '../../../api/client'
import { sourceMark } from '../sourceIcon'
import type { SourceCard } from '../knowledgeTypes'
import { previewCategories, replaceCategories } from './categoryApi'
import type { CategoryDraft, CategoryReplaceResponse } from './categoryTypes'
import styles from './CategoryOrganizer.module.css'

interface CategoryOrganizerProps {
  folderId: number
  /** 묶을 대상. 그래프에 그려진 SOURCE 노드다. */
  sourceIds: string[]
  sourcesById: Map<string, SourceCard>
  onClose: () => void
  onReplaced: (response: CategoryReplaceResponse) => void
}

/** 화면 안에서만 쓰는 카테고리. 서버가 준 초안에는 아직 id가 없어 자리 번호로 구분한다. */
interface DraftGroup {
  key: string
  title: string
  sourceIds: string[]
}

type OrganizerState =
  | { kind: 'loading'; messageIndex: number }
  | { kind: 'error'; message: string }
  | { kind: 'review'; groups: DraftGroup[]; movingSourceId: string | null }

const LOADING_MESSAGE_INTERVAL_MS = 4000
const LOADING_MESSAGES = [
  '문서의 요약과 개념을 바탕으로 카테고리 초안을 준비해요',
  '함께 읽기 좋은 문서들을 카테고리로 제안해요',
  '카테고리 초안이 나오면 이름과 문서 구성을 자유롭게 바꿀 수 있어요',
] as const

function toGroups(categories: CategoryDraft[]): DraftGroup[] {
  return categories.map((category, index) => ({
    key: `draft-${index}`,
    title: category.title,
    sourceIds: category.sourceIds,
  }))
}

function errorMessage(error: unknown, fallback = '카테고리를 만들지 못했어요.') {
  const apiMessage = typeof error === 'object' && error !== null
    ? (error as ApiError).message
    : undefined
  return apiMessage ?? fallback
}

/**
 * 링크를 카테고리로 나눈 초안을 검토하는 자리(F18).
 *
 * Task Organizer와 같은 Preview → 수정 → 확정 흐름이다. AI가 만든 초안은 시작점이고,
 * 무엇이 남을지는 사용자가 정한다. 여기서 고친 것만 확정으로 넘어간다.
 */
export default function CategoryOrganizer({
  folderId,
  sourceIds,
  sourcesById,
  onClose,
  onReplaced,
}: CategoryOrganizerProps) {
  const [state, setState] = useState<OrganizerState>({ kind: 'loading', messageIndex: 0 })
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const savingRef = useRef(false)

  useEffect(() => {
    let cancelled = false

    void previewCategories(folderId, { sourceIds })
      .then((preview) => {
        if (!cancelled) {
          setState({
            kind: 'review',
            groups: toGroups(preview.categories),
            movingSourceId: null,
          })
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ kind: 'error', message: errorMessage(error) })
      })

    return () => {
      cancelled = true
    }
  }, [folderId, sourceIds])

  useEffect(() => {
    if (state.kind !== 'loading') return

    const timer = window.setInterval(() => {
      setState((current) => current.kind === 'loading'
        ? {
            ...current,
            messageIndex: Math.min(current.messageIndex + 1, LOADING_MESSAGES.length - 1),
          }
        : current)
    }, LOADING_MESSAGE_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [state.kind])

  const updateGroups = (update: (groups: DraftGroup[]) => DraftGroup[]) => {
    if (savingRef.current) return
    setSaveError(null)
    setState((current) => current.kind === 'review'
      ? { ...current, groups: update(current.groups) }
      : current)
  }

  const renameGroup = (key: string, title: string) => {
    updateGroups((groups) => groups.map((group) => group.key === key
      ? { ...group, title }
      : group))
  }

  const removeGroup = (key: string) => {
    updateGroups((groups) => groups.filter((group) => group.key !== key))
  }

  const addGroup = () => {
    updateGroups((groups) => [
      ...groups,
      { key: `new-${Date.now()}`, title: '새 카테고리', sourceIds: [] },
    ])
  }

  /** 한 문서는 한 카테고리에만 속한다. 옮기면 원래 있던 자리에서 빠진다. */
  const moveSource = (sourceId: string, toKey: string) => {
    updateGroups((groups) => groups.map((group) => {
      if (group.key === toKey) {
        return group.sourceIds.includes(sourceId)
          ? group
          : { ...group, sourceIds: [...group.sourceIds, sourceId] }
      }
      return { ...group, sourceIds: group.sourceIds.filter((id) => id !== sourceId) }
    }))
    setState((current) => current.kind === 'review'
      ? { ...current, movingSourceId: null }
      : current)
  }

  const removeSource = (sourceId: string) => {
    updateGroups((groups) => groups.map((group) => ({
      ...group,
      sourceIds: group.sourceIds.filter((id) => id !== sourceId),
    })))
  }

  const startMoving = (sourceId: string) => {
    setState((current) => current.kind === 'review'
      ? { ...current, movingSourceId: current.movingSourceId === sourceId ? null : sourceId }
      : current)
  }

  const confirm = async () => {
    if (state.kind !== 'review' || savingRef.current) return
    const categories = state.groups
      .filter((group) => group.sourceIds.length > 0)
      .map((group) => ({ title: group.title.trim(), sourceIds: group.sourceIds }))
    const titles = new Set<string>()
    const assigned = new Set<string>()
    for (const category of categories) {
      if (!category.title || category.title.length > 500) {
        setSaveError('카테고리 이름을 1~500자로 입력해 주세요.')
        return
      }
      const normalized = category.title.normalize('NFKC').replace(/[\s_-]+/g, '').toLowerCase()
      if (titles.has(normalized)) {
        setSaveError('같은 이름의 카테고리가 있어요. 이름을 구분해 주세요.')
        return
      }
      titles.add(normalized)
      for (const id of category.sourceIds) {
        if (assigned.has(id) || !sourceIds.includes(id)) {
          setSaveError('문서는 대상 목록에서 한 카테고리에만 담아 주세요.')
          return
        }
        assigned.add(id)
      }
    }
    savingRef.current = true
    setIsSaving(true)
    setSaveError(null)
    let response: CategoryReplaceResponse
    try {
      response = await replaceCategories(folderId, { categories })
    } catch (error: unknown) {
      setSaveError(errorMessage(error, '저장하지 못했어요. 편집 내용은 그대로 있으니 다시 시도해 주세요.'))
      return
    } finally {
      savingRef.current = false
      setIsSaving(false)
    }
    onReplaced(response)
  }

  return (
    <aside className={styles.organizer} aria-label="카테고리 정리">
      <header className={styles.header}>
        <div className={styles['header-top']}>
          <span className={styles.badge}><IconSparkles size={14} aria-hidden="true" /> AI 카테고리 정리</span>
          <button
            type="button"
            className={styles.close}
            aria-label="카테고리 정리 닫기"
            onClick={onClose}
            disabled={isSaving}
          >
            <IconX size={15} stroke={1.8} aria-hidden="true" />
          </button>
        </div>
        <h2 className={styles.title}>
          {state.kind === 'loading' ? 'AI가 문서를 살펴보고 있어요' : state.kind === 'review' ? 'AI가 제안한 카테고리' : '카테고리 초안을 가져오지 못했어요'}
        </h2>
        <p className={styles.lede}>{state.kind === 'review'
          ? '연필을 눌러 이름을 수정하거나 문서를 눌러 옮겨 보세요.'
          : `문서 ${sourceIds.length}개를 함께 보고 카테고리 초안을 준비해요.`}</p>
      </header>

      {state.kind === 'loading' && (
        <div className={styles['ai-working']}>
          <div className={styles['document-orbit']} aria-hidden="true">
            <span /><span /><span />
            <div className={styles['ai-core']}><IconSparkles size={24} stroke={1.5} /></div>
          </div>
          <div className={styles['working-status']} role="status">
            <span className={styles['activity-dot']} aria-hidden="true" />
            카테고리 초안을 준비하고 있어요
          </div>
          <p className={styles.state}>{LOADING_MESSAGES[state.messageIndex]}</p>
          <div className={styles['draft-skeletons']} aria-hidden="true">
            {[0, 1, 2].map((index) => <div key={index} className={styles['draft-skeleton']}><span /><span /><span /></div>)}
          </div>
          <p className={styles['draft-note']}>카테고리 초안은 확정 전까지 저장되지 않아요.</p>
        </div>
      )}

      {state.kind === 'error' && (
        <p className={styles.state} role="alert">{state.message}</p>
      )}

      {state.kind === 'review' && (
        <>
          {state.groups.length === 0 && (
            <p className={styles.state}>
              아직 나눌 만한 카테고리가 보이지 않아요. 직접 만들어 볼 수 있어요.
            </p>
          )}

          <fieldset className={styles.editor} disabled={isSaving} aria-label="카테고리 초안 편집">
          <ul className={styles.groups}>
            {state.groups.map((group) => (
              <li key={group.key} className={styles.group}>
                <div className={styles['group-header']}>
                  <InlineEditableText
                    value={group.title}
                    ariaLabel={`${group.title} 이름`}
                    maxLength={500}
                    disabled={isSaving}
                    requiredMessage="카테고리 이름을 입력해 주세요"
                    className={styles['group-title']}
                    showEditButton
                    displayClassName={styles['group-title-display']}
                    onSave={async (title) => renameGroup(group.key, title)}
                  />
                  <span className={styles['group-count']}>
                    <span aria-hidden="true">·</span>
                    {group.sourceIds.length}개
                  </span>
                  <button
                    type="button"
                    className={styles['group-remove']}
                    aria-label={`${group.title} 카테고리 지우기`}
                    onClick={() => removeGroup(group.key)}
                  >
                    <IconTrash size={15} stroke={1.8} aria-hidden="true" />
                  </button>
                </div>

                <ul className={styles.sources}>
                  {group.sourceIds.map((sourceId) => {
                    const source = sourcesById.get(sourceId)
                    const isMoving = state.movingSourceId === sourceId

                    return (
                      <li key={sourceId} className={styles.source}>
                        <button
                          type="button"
                          className={styles['source-main']}
                          aria-pressed={isMoving}
                          onClick={() => startMoving(sourceId)}
                        >
                          {sourceMark(source?.domain ?? null, 14, styles.mark)}
                          <span className={styles['source-title']}>
                            {source?.title ?? '제목 없는 문서'}
                          </span>
                        </button>
                        <button
                          type="button"
                          className={styles['source-remove']}
                          aria-label={`${source?.title ?? '문서'} 빼기`}
                          onClick={() => removeSource(sourceId)}
                        >
                          <IconX size={14} stroke={1.8} aria-hidden="true" />
                        </button>

                        {isMoving && (
                          <ul className={styles['move-targets']}>
                            {state.groups
                              .filter((target) => target.key !== group.key)
                              .map((target) => (
                                <li key={target.key}>
                                  <button
                                    type="button"
                                    className={styles['move-target']}
                                    onClick={() => moveSource(sourceId, target.key)}
                                  >
                                    <IconArrowRight size={13} stroke={1.8} aria-hidden="true" />
                                    {target.title}
                                  </button>
                                </li>
                              ))}
                            {state.groups.length <= 1 && (
                              <li className={styles['move-empty']}>옮길 카테고리가 없어요</li>
                            )}
                          </ul>
                        )}
                      </li>
                    )
                  })}

                  {group.sourceIds.length === 0 && (
                    <li className={styles['sources-empty']}>
                      다른 카테고리의 문서를 눌러 옮길 수 있어요
                    </li>
                  )}
                </ul>
              </li>
            ))}
          </ul>

          <ActionButton
            variant="outline"
            className={styles['add-group']}
            icon={<IconPlus size={16} stroke={1.8} aria-hidden="true" />}
            onClick={addGroup}
          >
            카테고리 만들기
          </ActionButton>
          </fieldset>

          {state.groups.every((group) => group.sourceIds.length === 0) && (
            <p className={styles.state}>저장할 카테고리가 없어요. 확정하면 기존 카테고리를 모두 해제해요.</p>
          )}
          {saveError && <p className={styles.state} role="alert">{saveError}</p>}

          <div className={styles.actions}>
            <ActionButton
              className={styles['confirm-action']}
              onClick={() => void confirm()}
              isLoading={isSaving}
              loadingLabel="저장하고 있어요"
            >
              확정하기
            </ActionButton>
            <ActionButton
              className={styles['cancel-action']}
              variant="plain"
              onClick={onClose}
              disabled={isSaving}
            >그만두기</ActionButton>
          </div>
        </>
      )}
    </aside>
  )
}
