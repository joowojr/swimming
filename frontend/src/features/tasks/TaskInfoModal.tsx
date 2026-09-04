import { useEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import ActionButton from '../../components/ActionButton'
import modalStyles from '../../components/ModalShell.module.css'
import { useDailyPlanStore } from '../../store/dailyPlanStore'
import { useFolderStore } from '../../store/folderStore.ts'
import { useTaskStore } from '../../store/taskStore'
import { updateTaskInfo } from './taskApi'
import styles from './TaskInfoModal.module.css'

interface TaskInfoModalProps {
  taskId: number
  taskTitle: string
  currentFolderId: number | null
  currentPriority: boolean
  currentUrgent: boolean
  /**
   * 날짜를 다룰 수 있는 화면만 넘긴다.
   * itemId가 있으면 그 계획 항목을 옮기고, 없으면 고른 날짜의 계획에 새로 담는다.
   * date는 지금 담긴 날짜이고, 모르면 빈 문자열이다.
   */
  plan?: { itemId?: number; date: string }
  /**
   * 중요·즉시 수정 허용 여부. 매트릭스는 이 두 값이 곧 섹션이라 여기서 바꾸면
   * 카드가 다른 섹션으로 사라진다. 매트릭스에서는 드래그로 옮긴다.
   */
  canEditFlags?: boolean
  onClose: () => void
}

function requestErrorMessage(error: unknown, fallback: string) {
  const apiError = error as ApiError
  return apiError.message ?? fallback
}

/**
 * 역할: 할 일의 날짜·폴더·표시를 한 화면에서 고친다.
 * 서버가 한 트랜잭션으로 처리하므로 일부만 반영되는 중간 상태가 없다.
 * TODO(task-owns-plan-date): 계획 날짜가 task 컬럼이 되면 plan prop과 응답의 plans가 사라지고
 *   날짜도 폴더와 같은 평범한 필드가 된다. docs/backlog/task-owns-plan-date.md
 */
export default function TaskInfoModal({
  taskId,
  taskTitle,
  currentFolderId,
  currentPriority,
  currentUrgent,
  plan,
  canEditFlags = true,
  onClose,
}: TaskInfoModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const folders = useFolderStore((state) => state.folders)
  const upsertTasks = useTaskStore((state) => state.upsert)
  const applyPlans = useDailyPlanStore((state) => state.applyPlans)

  const [title, setTitle] = useState(taskTitle)
  const [planDate, setPlanDate] = useState(plan?.date ?? '')
  const [folderId, setFolderId] = useState<number | null>(currentFolderId)
  const [priority, setPriority] = useState(currentPriority)
  const [urgent, setUrgent] = useState(currentUrgent)
  const [isSaving, setIsSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    dialogRef.current?.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  const requestClose = () => {
    if (!isSaving) dialogRef.current?.close()
  }
  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const trimmedTitle = title.trim()
  const isTitleChanged = trimmedTitle !== taskTitle
  const isDateChanged = plan !== undefined && planDate !== '' && planDate !== plan.date
  const isFolderChanged = folderId !== currentFolderId
  const isPriorityChanged = canEditFlags && priority !== currentPriority
  const isUrgentChanged = canEditFlags && urgent !== currentUrgent
  const hasChange = trimmedTitle !== ''
    && (isTitleChanged || isDateChanged || isFolderChanged || isPriorityChanged || isUrgentChanged)

  const save = async () => {
    if (!hasChange || isSaving) return

    setIsSaving(true)
    setMessage(null)

    try {
      // folderId는 null이 "미분류"라 생략과 구분되지 않으므로 세 값을 항상 보낸다.
      // 날짜만 바뀌지 않았을 때 보내지 않아, 계획 항목을 건드리지 않는다.
      const updated = await updateTaskInfo(taskId, {
        title: trimmedTitle,
        folderId,
        priority: canEditFlags ? priority : currentPriority,
        urgent: canEditFlags ? urgent : currentUrgent,
        ...(plan && isDateChanged
          ? { plan: { ...(plan.itemId === undefined ? {} : { itemId: plan.itemId }), date: planDate } }
          : {}),
      })
      upsertTasks([updated.task])
      applyPlans(updated.plans)
      dialogRef.current?.close()
    } catch (error) {
      setMessage(requestErrorMessage(error, '수정하지 못했습니다. 잠시 후 다시 시도해 주세요.'))
      setIsSaving(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      onClose={onClose}
      onMouseDown={handleBackdrop}
      onCancel={(event) => { if (isSaving) event.preventDefault() }}
    >
      <form
        className={`${styles.modal} ${modalStyles.surface}`}
        aria-labelledby="task-info-title"
        onSubmit={(event) => { event.preventDefault(); void save() }}
      >
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="task-info-title">수정하기</h2>
          </div>
          <button type="button" className={styles.close} aria-label="수정 창 닫기" onClick={requestClose} disabled={isSaving}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <label className={styles.field}>
            <span>제목</span>
            <input type="text" value={title} maxLength={255} disabled={isSaving}
              onChange={(event) => setTitle(event.target.value)} />
          </label>

          {plan && (
            <label className={styles.field}>
              <span>날짜</span>
              <input type="date" value={planDate} disabled={isSaving}
                onChange={(event) => setPlanDate(event.target.value)} />
            </label>
          )}

          <label className={styles.field}>
            <span>폴더</span>
            <select value={folderId ?? ''} disabled={isSaving}
              onChange={(event) => setFolderId(event.target.value ? Number(event.target.value) : null)}>
              <option value="">미분류</option>
              {folders.map((folder) => <option value={folder.id} key={folder.id}>{folder.name}</option>)}
            </select>
          </label>

          {canEditFlags && (
          <div className={styles.field}>
            <span id="task-info-flags">표시</span>
            <div className={styles.chips} role="list" aria-labelledby="task-info-flags">
              <span role="listitem">
                <button type="button" aria-pressed={urgent} disabled={isSaving}
                  onClick={() => setUrgent((current) => !current)}>
                  <span aria-hidden="true">⚡</span>
                  즉시
                </button>
              </span>
              <span role="listitem">
                <button type="button" aria-pressed={priority} disabled={isSaving}
                  onClick={() => setPriority((current) => !current)}>
                  <span aria-hidden="true">📌</span>
                  중요
                </button>
              </span>
            </div>
          </div>
          )}

          {message && <p className={styles.message} role="alert">{message}</p>}
        </div>

        <footer className={styles.footer}>
          <ActionButton variant="plain" onClick={requestClose} disabled={isSaving}>취소</ActionButton>
          <ActionButton type="submit" isLoading={isSaving} loadingLabel="저장 중" disabled={!hasChange}>저장</ActionButton>
        </footer>
      </form>
    </dialog>
  )
}
