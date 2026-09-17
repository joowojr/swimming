import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  closestCenter,
  getFirstCollision,
  pointerWithin,
  rectIntersection,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import type { Announcements, CollisionDetection, DragEndEvent, DragOverEvent, DragStartEvent, UniqueIdentifier } from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { IconGripVertical, IconPlus, IconSparkles, IconTrash, IconX } from '@tabler/icons-react'
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
  | { kind: 'review'; groups: DraftGroup[] }

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

/** 카테고리 드롭 영역 id. 문서 id와 겹치지 않게 접두사를 붙인다. */
const GROUP_DROP_PREFIX = 'group:'

function groupDropId(key: string) {
  return `${GROUP_DROP_PREFIX}${key}`
}

/** 드롭 대상이 카테고리 자체인지 문서인지에 따라 그 문서가 속한 카테고리 key를 찾는다. */
function findGroupKey(groups: DraftGroup[], id: UniqueIdentifier): string | undefined {
  const value = String(id)
  if (value.startsWith(GROUP_DROP_PREFIX)) return value.slice(GROUP_DROP_PREFIX.length)
  return groups.find((group) => group.sourceIds.includes(value))?.key
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
  const [activeSourceId, setActiveSourceId] = useState<string | null>(null)
  /** 드래그를 취소하면 끌기 전 모습으로 되돌린다. */
  const groupsBeforeDragRef = useRef<DraftGroup[] | null>(null)
  const lastOverIdRef = useRef<UniqueIdentifier | null>(null)
  /** 카테고리를 막 옮긴 직후에는 레이아웃이 다시 잡히기 전이라 충돌 판정이 원래 카테고리로 튈 수 있다. */
  const recentlyMovedToNewGroupRef = useRef(false)

  useEffect(() => {
    let cancelled = false

    void previewCategories(folderId, { sourceIds })
      .then((preview) => {
        if (!cancelled) {
          setState({
            kind: 'review',
            groups: toGroups(preview.categories),
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

  const removeSource = (sourceId: string) => {
    updateGroups((groups) => groups.map((group) => ({
      ...group,
      sourceIds: group.sourceIds.filter((id) => id !== sourceId),
    })))
  }

  // 모바일은 손잡이를 잠깐 눌러야 드래그가 시작돼 스크롤과 구분된다.
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 4 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const titleOf = (sourceId: UniqueIdentifier) =>
    sourcesById.get(String(sourceId))?.title ?? '제목 없는 문서'

  const groupTitleOf = (id: UniqueIdentifier | undefined) => {
    if (state.kind !== 'review' || id === undefined) return undefined
    const key = findGroupKey(state.groups, id)
    return state.groups.find((group) => group.key === key)?.title
  }

  const announcements: Announcements = {
    onDragStart: ({ active }) => `${titleOf(active.id)} 문서를 집었어요.`,
    onDragOver: ({ active, over }) => {
      const target = groupTitleOf(over?.id)
      return target ? `${titleOf(active.id)} 문서가 ${target} 카테고리 위에 있어요.` : undefined
    },
    onDragEnd: ({ active, over }) => {
      const target = groupTitleOf(over?.id)
      return target ? `${titleOf(active.id)} 문서를 ${target} 카테고리에 놓았어요.` : `${titleOf(active.id)} 문서를 제자리에 두었어요.`
    },
    onDragCancel: ({ active }) => `${titleOf(active.id)} 문서 옮기기를 취소했어요.`,
  }

  const reviewGroups = state.kind === 'review' ? state.groups : null

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      recentlyMovedToNewGroupRef.current = false
    })
    return () => cancelAnimationFrame(frame)
  }, [reviewGroups])

  /**
   * dnd-kit 다중 목록 예제의 충돌 판정이다. 카테고리를 넘나들 때 판정이 두 카테고리 사이를 오가며
   * 상태를 계속 바꾸는(엉키는) 문제를 막는다. 포인터가 카테고리 위에 있으면 그 안에서 가장 가까운 문서를 고른다.
   */
  const collisionDetection = useCallback<CollisionDetection>((args) => {
    const pointerCollisions = pointerWithin(args)
    const collisions = pointerCollisions.length > 0 ? pointerCollisions : rectIntersection(args)
    let overId = getFirstCollision(collisions, 'id')

    if (overId != null) {
      if (typeof overId === 'string' && overId.startsWith(GROUP_DROP_PREFIX)) {
        const key = overId.slice(GROUP_DROP_PREFIX.length)
        const itemIds = new Set<UniqueIdentifier>(reviewGroups?.find((group) => group.key === key)?.sourceIds ?? [])
        if (itemIds.size > 0) {
          overId = closestCenter({
            ...args,
            droppableContainers: args.droppableContainers.filter((container) => itemIds.has(container.id)),
          })[0]?.id ?? overId
        }
      }
      lastOverIdRef.current = overId
      return [{ id: overId }]
    }

    if (recentlyMovedToNewGroupRef.current) lastOverIdRef.current = args.active.id
    return lastOverIdRef.current != null ? [{ id: lastOverIdRef.current }] : []
  }, [reviewGroups])

  const handleDragStart = ({ active }: DragStartEvent) => {
    if (state.kind !== 'review') return
    groupsBeforeDragRef.current = state.groups
    lastOverIdRef.current = null
    setActiveSourceId(String(active.id))
  }

  /** 다른 카테고리 위로 넘어가는 순간 문서를 그 카테고리로 옮긴다. 한 문서는 한 카테고리에만 속한다. */
  const handleDragOver = ({ active, over }: DragOverEvent) => {
    if (!over) return
    updateGroups((groups) => {
      const sourceId = String(active.id)
      const fromKey = findGroupKey(groups, sourceId)
      const toKey = findGroupKey(groups, over.id)
      if (!fromKey || !toKey || fromKey === toKey) return groups
      recentlyMovedToNewGroupRef.current = true

      return groups.map((group) => {
        if (group.key === fromKey) {
          return { ...group, sourceIds: group.sourceIds.filter((id) => id !== sourceId) }
        }
        if (group.key !== toKey) return group
        const overIndex = group.sourceIds.indexOf(String(over.id))
        // 끄는 문서가 대상 문서보다 아래로 내려갔으면 그 뒤에 넣는다.
        const translated = active.rect.current.translated
        const isBelowOver = translated !== null && translated.top > over.rect.top + over.rect.height / 2
        const insertAt = overIndex >= 0 ? overIndex + (isBelowOver ? 1 : 0) : group.sourceIds.length
        return {
          ...group,
          sourceIds: [...group.sourceIds.slice(0, insertAt), sourceId, ...group.sourceIds.slice(insertAt)],
        }
      })
    })
  }

  /** 같은 카테고리 안에서는 놓은 자리로 순서를 바꾼다. */
  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    setActiveSourceId(null)
    groupsBeforeDragRef.current = null
    if (!over || active.id === over.id) return
    updateGroups((groups) => groups.map((group) => {
      const from = group.sourceIds.indexOf(String(active.id))
      const to = group.sourceIds.indexOf(String(over.id))
      return from >= 0 && to >= 0 ? { ...group, sourceIds: arrayMove(group.sourceIds, from, to) } : group
    }))
  }

  const handleDragCancel = () => {
    const snapshot = groupsBeforeDragRef.current
    setActiveSourceId(null)
    groupsBeforeDragRef.current = null
    if (snapshot) updateGroups(() => snapshot)
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
          ? '연필을 눌러 이름을 수정하거나 손잡이로 문서를 끌어 옮겨 보세요.'
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
          <DndContext
            sensors={sensors}
            collisionDetection={collisionDetection}
            accessibility={{
              announcements,
              screenReaderInstructions: {
                draggable: '문서를 옮기려면 스페이스바를 누르고 방향키로 움직인 뒤 스페이스바로 놓으세요. 취소는 Esc예요.',
              },
            }}
            onDragStart={handleDragStart}
            onDragOver={handleDragOver}
            onDragEnd={handleDragEnd}
            onDragCancel={handleDragCancel}
          >
          <ul className={styles.groups}>
            {state.groups.map((group) => (
              <DroppableGroup key={group.key} groupKey={group.key}>
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

                <SortableContext items={group.sourceIds} strategy={verticalListSortingStrategy}>
                  <ul className={styles.sources}>
                    {group.sourceIds.map((sourceId) => (
                      <SortableSource
                        key={sourceId}
                        sourceId={sourceId}
                        source={sourcesById.get(sourceId)}
                        disabled={isSaving}
                        onRemove={() => removeSource(sourceId)}
                      />
                    ))}

                    {group.sourceIds.length === 0 && (
                      <li className={styles['sources-empty']}>
                        다른 카테고리의 문서를 여기로 끌어 놓을 수 있어요
                      </li>
                    )}
                  </ul>
                </SortableContext>
              </DroppableGroup>
            ))}
          </ul>

          <DragOverlay>
            {activeSourceId && (
              <div className={`${styles.source} ${styles['source-overlay']}`}>
                <span className={styles['source-handle']} aria-hidden="true">
                  <IconGripVertical size={14} stroke={1.8} />
                </span>
                <span className={styles['source-main']}>
                  {sourceMark(sourcesById.get(activeSourceId)?.domain ?? null, 14, styles.mark)}
                  <span className={styles['source-title']}>{titleOf(activeSourceId)}</span>
                </span>
              </div>
            )}
          </DragOverlay>
          </DndContext>

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

/** 카테고리 전체가 드롭 영역이다. 비어 있는 카테고리에도 문서를 놓을 수 있다. */
function DroppableGroup({ groupKey, children }: { groupKey: string; children: ReactNode }) {
  const { setNodeRef, isOver } = useDroppable({ id: groupDropId(groupKey) })
  return (
    <li
      ref={setNodeRef}
      className={`${styles.group} ${isOver ? styles['group-over'] : ''}`}
    >
      {children}
    </li>
  )
}

interface SortableSourceProps {
  sourceId: string
  source: SourceCard | undefined
  disabled: boolean
  onRemove: () => void
}

/** 손잡이로만 드래그를 시작한다. 줄의 나머지 부분은 모바일에서 스크롤로 남는다. */
function SortableSource({ sourceId, source, disabled, onRemove }: SortableSourceProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: sourceId, disabled })
  const title = source?.title ?? '제목 없는 문서'

  return (
    <li
      ref={setNodeRef}
      className={`${styles.source} ${isDragging ? styles['source-dragging'] : ''}`}
      style={{ transform: CSS.Transform.toString(transform), transition }}
    >
      <button
        type="button"
        ref={setActivatorNodeRef}
        className={styles['source-handle']}
        aria-label={`${title} 옮기기`}
        {...attributes}
        {...listeners}
      >
        <IconGripVertical size={14} stroke={1.8} aria-hidden="true" />
      </button>
      <span className={styles['source-main']}>
        {sourceMark(source?.domain ?? null, 14, styles.mark)}
        <span className={styles['source-title']}>{title}</span>
      </span>
      <button
        type="button"
        className={styles['source-remove']}
        aria-label={`${title} 빼기`}
        onClick={onRemove}
      >
        <IconX size={14} stroke={1.8} aria-hidden="true" />
      </button>
    </li>
  )
}
