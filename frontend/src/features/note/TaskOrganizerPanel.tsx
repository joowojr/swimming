import { useEffect, useRef, useState } from 'react'
import { IconCalendar, IconMinus, IconPlus } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import InlineEditableText from '../../components/InlineEditableText'
import { addDailyPlanItems } from '../plans/dailyPlanApi'
import { confirmTaskOrganization, previewTaskOrganization } from './taskOrganizerApi'
import type { TaskOrganizeResponse } from './taskOrganizerTypes'
import type { ProjectOption } from './noteViewTypes'
import styles from './NoteCard.module.css'

/** 역할: AI 미리보기, Task 등록, 계획 연결의 2~3단계 흐름과 API 호출을 독립적으로 관리한다. */
interface TaskOrganizerPanelProps {
  source: {
    noteId: number
    memo: string
  }
  projects: ProjectOption[]
  onCancel: () => void
  onFinish: (message: string, options?: { suggestArchiveNoteId?: number }) => void
}

interface PreviewTaskItem {
  id: string
  sourceText: string
  title: string
  projectId: number | null
  projectName: string | null
  selected: boolean
}

interface PlanLinkItem {
  id: number
  title: string
  projectId: number | null
  projectName: string | null
  planDate: string
  selected: boolean
}

type OrganizerState =
  | { kind: 'loading'; messageIndex: number }
  | {
      kind: 'preview'
      items: PreviewTaskItem[]
      editingProjectItemId: string | null
      message: string | null
      isConfirming: boolean
    }
  | {
      kind: 'plan-link'
      items: PlanLinkItem[]
      editingPlanDateTaskId: number | null
      message: string | null
      isLinking: boolean
    }

const LOADING_MESSAGE_INTERVAL_MS = 1000
const LOADING_MESSAGES = [
  '최근 폴더 목록을 조회하고 있어요',
  '최근 할 일 목록을 살펴보고 있어요',
  '메모에서 할 일을 정리하고 있어요',
  '정리 결과를 준비하고 있어요',
] as const

function toPreviewItems(preview: TaskOrganizeResponse): PreviewTaskItem[] {
  return [
    ...preview.suggestions.map((suggestion, index) => ({
      id: `suggestion-${index}`,
      sourceText: suggestion.sourceText,
      title: suggestion.title,
      projectId: suggestion.projectId,
      projectName: suggestion.projectName,
      selected: true,
    })),
    ...preview.unclassified.map((item, index) => ({
      id: `unclassified-${index}`,
      sourceText: item.sourceText,
      title: item.title,
      projectId: null,
      projectName: null,
      selected: true,
    })),
  ]
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatPlanDate(planDate: string) {
  const [, month, day] = planDate.split('-')
  return month && day ? `${month}/${day}` : planDate
}

function isAdHocTask(item: PlanLinkItem) {
  return item.projectId === null
}

export default function TaskOrganizerPanel({
  source,
  projects,
  onCancel,
  onFinish,
}: TaskOrganizerPanelProps) {
  const [state, setState] = useState<OrganizerState>({ kind: 'loading', messageIndex: 0 })
  const planDateInputRef = useRef<HTMLInputElement>(null)
  const editingPlanDateTaskId = state.kind === 'plan-link'
    ? state.editingPlanDateTaskId
    : null

  useEffect(() => {
    let cancelled = false

    void previewTaskOrganization({ memo: source.memo })
      .then((preview) => {
        if (!cancelled) {
          setState({
            kind: 'preview',
            items: toPreviewItems(preview),
            editingProjectItemId: null,
            message: null,
            isConfirming: false,
          })
        }
      })
      .catch(() => {
        if (!cancelled) onFinish('할 일을 정리하지 못했어요')
      })

    return () => {
      cancelled = true
    }
  }, [onFinish, source.memo])

  useEffect(() => {
    if (state.kind !== 'loading') return

    const timer = window.setInterval(() => {
      setState((current) => current.kind === 'loading'
        ? { ...current, messageIndex: Math.min(current.messageIndex + 1, LOADING_MESSAGES.length - 1) }
        : current)
    }, LOADING_MESSAGE_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [state.kind])

  useEffect(() => {
    if (editingPlanDateTaskId === null) return

    const animationFrame = window.requestAnimationFrame(() => {
      const input = planDateInputRef.current
      if (!input) return
      input.focus()
      try {
        input.showPicker()
      } catch {
        // 브라우저가 날짜 선택창을 직접 열지 못하면 포커스만 제공한다.
      }
    })

    return () => window.cancelAnimationFrame(animationFrame)
  }, [editingPlanDateTaskId])

  const updatePreviewItem = (
    itemId: string,
    update: (item: PreviewTaskItem) => PreviewTaskItem,
  ) => {
    setState((current) => current.kind === 'preview'
      ? { ...current, items: current.items.map((item) => item.id === itemId ? update(item) : item), message: null }
      : current)
  }

  const updatePlanLinkItem = (
    taskId: number,
    update: (item: PlanLinkItem) => PlanLinkItem,
  ) => {
    setState((current) => current.kind === 'plan-link'
      ? { ...current, items: current.items.map((item) => item.id === taskId ? update(item) : item), message: null }
      : current)
  }

  const handlePreviewProjectChange = (itemId: string, value: string) => {
    const projectId = value ? Number(value) : null
    const projectName = projectId === null
      ? null
      : projects.find((project) => project.id === projectId)?.name ?? null

    updatePreviewItem(itemId, (item) => ({ ...item, projectId, projectName }))
    setState((current) => current.kind === 'preview'
      ? { ...current, editingProjectItemId: null }
      : current)
  }

  const handleConfirmTasks = async () => {
    if (state.kind !== 'preview' || state.isConfirming) return

    const selectedItems = state.items.filter((item) => item.selected)
    if (selectedItems.length === 0 || selectedItems.some((item) => !item.title.trim())) return

    setState({ ...state, isConfirming: true, message: null })

    try {
      const response = await confirmTaskOrganization({
        noteId: source.noteId,
        tasks: selectedItems.map((item) => ({
          sourceText: item.sourceText,
          projectId: item.projectId,
          title: item.title.trim(),
        })),
      })
      const today = formatLocalDate(new Date())
      setState({
        kind: 'plan-link',
        items: response.createdTasks.map((task) => ({
          id: task.id,
          title: task.title,
          projectId: task.projectId,
          projectName: task.projectId === null
            ? null
            : projects.find((project) => project.id === task.projectId)?.name ?? '폴더',
          planDate: today,
          selected: true,
        })),
        editingPlanDateTaskId: null,
        message: null,
        isLinking: false,
      })
    } catch {
      setState((current) => current.kind === 'preview'
        ? { ...current, isConfirming: false, message: '할 일을 만들지 못했어요. 선택 내용을 그대로 유지했어요.' }
        : current)
    }
  }

  const handleLinkPlan = async () => {
    if (state.kind !== 'plan-link' || state.isLinking) return

    const selectedItems = state.items.filter((item) => item.selected || isAdHocTask(item))
    if (selectedItems.length === 0) return

    const taskIdsByDate = new Map<string, number[]>()
    selectedItems.forEach((item) => {
      const taskIds = taskIdsByDate.get(item.planDate) ?? []
      taskIds.push(item.id)
      taskIdsByDate.set(item.planDate, taskIds)
    })

    setState({ ...state, isLinking: true, message: null })
    let remainingItems = state.items
    let linkedCount = 0
    const totalTaskCount = state.items.length

    try {
      for (const [planDate, taskIds] of taskIdsByDate) {
        await addDailyPlanItems(planDate, { taskIds })
        const linkedTaskIds = new Set(taskIds)
        remainingItems = remainingItems.filter((item) => !linkedTaskIds.has(item.id))
        linkedCount += taskIds.length
        setState((current) => current.kind === 'plan-link'
          ? { ...current, items: remainingItems }
          : current)
      }
      const shouldArchive = linkedCount / totalTaskCount >= 0.8
      onFinish(
        `${linkedCount}개 할 일을 계획에 연결했어요`,
        shouldArchive ? { suggestArchiveNoteId: source.noteId } : undefined,
      )
    } catch {
      setState((current) => current.kind === 'plan-link'
        ? {
            ...current,
            items: remainingItems,
            isLinking: false,
            message: linkedCount > 0
              ? `${linkedCount}개는 연결했어요. 남은 할 일을 다시 연결해 주세요.`
              : '계획에 연결하지 못했어요. 날짜와 선택 내용을 그대로 유지했어요.',
          }
        : current)
    }
  }

  if (state.kind === 'loading') {
    return (
      <section className={styles['organize-loading']} aria-labelledby="organize-loading-title" aria-busy="true">
        <div className={styles['organize-loading-heading']}>
          <h4 id="organize-loading-title">메모를 할 일로 정리하고 있어요</h4>
          <p className={styles['organize-loading-message']} key={state.messageIndex} role="status" aria-live="polite">
            {LOADING_MESSAGES[state.messageIndex]}
          </p>
        </div>
        <ul className={styles['organize-loading-list']} aria-hidden="true">
          {[0, 1, 2].map((index) => (
            <li className={styles['organize-loading-row']} key={index}>
              <span className={styles['organize-loading-title']} />
              <span className={styles['organize-loading-project']} />
            </li>
          ))}
        </ul>
      </section>
    )
  }

  if (state.kind === 'plan-link') {
    const selectedCount = state.items.filter((item) => item.selected || isAdHocTask(item)).length
    const hasAdHocTask = state.items.some(isAdHocTask)
    return (
      <section className={styles['organize-preview']} aria-labelledby="plan-link-title" aria-busy={state.isLinking}>
        <div className={styles['organize-preview-heading']}>
          <h4 id="plan-link-title">계획에 연결하기</h4>
          <span>{selectedCount}개 선택</span>
        </div>
        <p className={styles['organize-preview-guide']}>만든 할 일을 진행할 날짜에 연결해 두세요.</p>
        <p className={styles['organize-preview-guide']}>날짜를 두 번 누르면 변경할 수 있어요.</p>
        <div className={styles['organize-preview-list-wrap']}>
          <ul className={styles['organize-task-list']}>
            {state.items.map((item) => (
              <li className={item.selected || isAdHocTask(item) ? styles['plan-link-task-row'] : styles['organize-task-row-excluded']} key={item.id}>
                <div className={styles['plan-link-task-fields']}>
                  <div className={styles['plan-link-title-row']}>
                    <span className={styles['plan-link-task-title']}>{item.title}</span>
                    {state.editingPlanDateTaskId === item.id ? (
                      <input ref={planDateInputRef} className={styles['plan-link-date-input']} type="date" value={item.planDate}
                        required disabled={state.isLinking} aria-label={`${item.title} 계획 날짜`}
                        onChange={(event) => updatePlanLinkItem(item.id, (current) => ({ ...current, planDate: event.target.value || current.planDate }))}
                        onBlur={() => setState((current) => current.kind === 'plan-link' ? { ...current, editingPlanDateTaskId: null } : current)}
                        onKeyDown={(event) => {
                          if (event.key === 'Escape' || event.key === 'Enter') {
                            setState((current) => current.kind === 'plan-link' ? { ...current, editingPlanDateTaskId: null } : current)
                          }
                        }} />
                    ) : (
                      <button type="button" className={styles['plan-link-date-action']} disabled={state.isLinking || (!item.selected && !isAdHocTask(item))}
                        aria-label={`${item.title} 계획 날짜 ${formatPlanDate(item.planDate)}. 두 번 눌러 변경`} title="두 번 눌러 날짜 변경"
                        onDoubleClick={() => setState((current) => current.kind === 'plan-link' ? { ...current, editingPlanDateTaskId: item.id } : current)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault()
                            setState((current) => current.kind === 'plan-link' ? { ...current, editingPlanDateTaskId: item.id } : current)
                          }
                        }}>
                        <IconCalendar size={14} aria-hidden="true" />
                        {formatPlanDate(item.planDate)}
                      </button>
                    )}
                  </div>
                  <span className={styles['plan-link-project-name']}>{item.projectName ?? '미분류'}</span>
                </div>
                <button type="button" className={styles['organize-exclude-action']} disabled={state.isLinking || isAdHocTask(item)}
                  onClick={() => updatePlanLinkItem(item.id, (current) => ({ ...current, selected: !current.selected }))}
                  aria-label={isAdHocTask(item)
                    ? `${item.title} 미분류 할 일은 계획에 연결해야 함`
                    : item.selected ? `${item.title} 계획 연결에서 빼기` : `${item.title} 계획 연결에 다시 포함`}
                  title={isAdHocTask(item) ? '미분류 할 일은 계획에 연결해야 해요' : item.selected ? '연결에서 빼기' : '다시 포함'}>
                  {item.selected ? <IconMinus size={16} aria-hidden="true" /> : <IconPlus size={16} aria-hidden="true" />}
                </button>
              </li>
            ))}
          </ul>
        </div>
        {state.message && <p className={styles['organize-preview-message']} role="status">{state.message}</p>}
        <div className={styles['organize-preview-actions']}>
          <ActionButton className={styles['organize-confirm-action']} isLoading={state.isLinking} loadingLabel="계획에 연결하는 중"
            disabled={selectedCount === 0} onClick={handleLinkPlan}>{selectedCount}개 계획에 연결하기</ActionButton>
          <ActionButton className={styles['organize-cancel-action']} variant="plain" disabled={state.isLinking || hasAdHocTask}
            onClick={() => onFinish('할 일을 만들었어요')}>나중에</ActionButton>
        </div>
      </section>
    )
  }

  const selectedItems = state.items.filter((item) => item.selected)
  const hasUntitledSelectedItem = selectedItems.some((item) => !item.title.trim())
  return (
    <section className={styles['organize-preview']} aria-labelledby="organize-preview-title">
      <div className={styles['organize-preview-heading']}>
        <h4 id="organize-preview-title">할 일 미리보기</h4>
        <span>{selectedItems.length}개 등록</span>
      </div>
      <p className={styles['organize-preview-guide']}>할 일과 폴더를 클릭하면 원하는 대로</p>
      <p className={styles['organize-preview-guide']}>수정할 수 있어요</p>
      <div className={styles['organize-preview-list-wrap']}>
        {state.items.length > 0 ? (
          <ul className={styles['organize-task-list']}>
            {state.items.map((item) => {
              const projectName = item.projectId === null
                ? '미분류'
                : projects.find((project) => project.id === item.projectId)?.name ?? item.projectName ?? '폴더'
              return (
                <li className={!item.selected
                  ? styles['organize-task-row-excluded']
                  : item.projectId === null ? styles['organize-task-row-unclassified'] : styles['organize-task-row']} key={item.id}>
                  <div className={styles['organize-task-fields']}>
                    <InlineEditableText className={styles['organize-title-edit']} errorClassName={styles['organize-title-error']}
                      value={item.title} ariaLabel="할 일 제목" maxLength={255} disabled={state.isConfirming}
                      requiredMessage="할 일 제목을 입력해 주세요."
                      onSave={(title) => {
                        updatePreviewItem(item.id, (current) => ({ ...current, title }))
                        return Promise.resolve()
                      }} />
                    {state.editingProjectItemId === item.id ? (
                      <select className={styles['organize-project-select']} value={item.projectId ?? ''} disabled={state.isConfirming}
                        autoFocus onBlur={() => setState((current) => current.kind === 'preview' ? { ...current, editingProjectItemId: null } : current)}
                        onChange={(event) => handlePreviewProjectChange(item.id, event.target.value)} aria-label={`${item.title} Project 선택`}>
                        <option value="">미분류</option>
                        {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
                      </select>
                    ) : (
                      <button type="button" className={styles['organize-project-action']} disabled={state.isConfirming}
                        onClick={() => setState((current) => current.kind === 'preview' ? { ...current, editingProjectItemId: item.id } : current)}
                        aria-label={`${item.title} Project 변경`}>{projectName}</button>
                    )}
                  </div>
                  <button type="button" className={styles['organize-exclude-action']} disabled={state.isConfirming}
                    onClick={() => updatePreviewItem(item.id, (current) => ({ ...current, selected: !current.selected }))}
                    aria-label={item.selected ? `${item.title} 후보에서 빼기` : `${item.title} 다시 포함`}
                    title={item.selected ? '후보에서 빼기' : '다시 포함'}>
                    {item.selected ? <IconMinus size={16} aria-hidden="true" /> : <IconPlus size={16} aria-hidden="true" />}
                  </button>
                </li>
              )
            })}
          </ul>
        ) : <p className={styles['organize-preview-empty']}>지금 만들 할 일 후보는 없어요.</p>}
      </div>
      {state.message && <p className={styles['organize-preview-message']} role="status">{state.message}</p>}
      <div className={styles['organize-preview-actions']}>
        <ActionButton className={styles['organize-confirm-action']} isLoading={state.isConfirming} loadingLabel="할 일 만드는 중"
          onClick={handleConfirmTasks} disabled={selectedItems.length === 0 || hasUntitledSelectedItem}>
          {selectedItems.length}개 할 일 만들기
        </ActionButton>
        <ActionButton className={styles['organize-cancel-action']} variant="plain" onClick={onCancel} disabled={state.isConfirming}>취소</ActionButton>
      </div>
    </section>
  )
}
