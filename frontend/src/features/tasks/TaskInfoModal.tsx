import { useEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { IconLoader2, IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import { useDailyPlanStore } from '../../store/dailyPlanStore'
import { useFolderStore } from '../../store/folderStore.ts'
import { useTaskStore } from '../../store/taskStore'
import { updateTaskInfo } from './taskApi'
import styles from './TaskMoveModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'

interface TaskMoveModalProps {
  taskId: number
  taskTitle: string
  currentFolderId: number | null
  currentPriority: boolean
  currentUrgent: boolean
  /** 계획 항목일 때만 날짜를 바꿀 수 있다. 데일리 플래너에서만 넘어온다. */
  plan?: { itemId: number; date: string }
  onClose: () => void
}

function requestErrorMessage(error: unknown, fallback: string) {
  const apiError = typeof error === 'object' && error !== null ? error as ApiError : undefined
  return apiError?.message ?? fallback
}

/**
 * 역할: 할 일의 폴더와, 계획 항목이면 날짜까지 한 화면에서 옮긴다.
 * 서버가 한 트랜잭션으로 처리하므로 폴더만 바뀌고 날짜는 안 바뀌는 중간 상태가 없다.
 * TODO(task-owns-plan-date): 계획 날짜가 task 컬럼이 되면 plan prop과 응답의 plans가 사라지고
 *   날짜도 폴더와 같은 평범한 필드가 된다. docs/backlog/task-owns-plan-date.md
 */
export default function TaskMoveModal({
  taskId,
  taskTitle,
  currentFolderId,
  currentPriority,
  currentUrgent,
  plan,
  onClose,
}: TaskMoveModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const folders = useFolderStore((state) => state.folders)
  const upsertTasks = useTaskStore((state) => state.upsert)
  const applyPlans = useDailyPlanStore((state) => state.applyPlans)

  const [folderId, setFolderId] = useState<number | null>(currentFolderId)
  const [priority, setPriority] = useState(currentPriority)
  const [urgent, setUrgent] = useState(currentUrgent)
  const [planDate, setPlanDate] = useState(plan?.date ?? '')
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

  const isFolderChanged = folderId !== currentFolderId
  const isPriorityChanged = priority !== currentPriority
  const isUrgentChanged = urgent !== currentUrgent
  const isDateChanged = plan !== undefined && planDate !== '' && planDate !== plan.date
  const hasChange = isFolderChanged || isPriorityChanged || isUrgentChanged || isDateChanged

  const save = async () => {
    if (!hasChange || isSaving) return

    setIsSaving(true)
    setMessage(null)

    try {
      // 바뀐 것만 보낸다. 서버는 받은 필드만 반영한다.
      const updated = await updateTaskInfo(taskId, {
        ...(isFolderChanged ? { folderId } : {}),
        ...(isPriorityChanged ? { priority } : {}),
        ...(isUrgentChanged ? { urgent } : {}),
        ...(plan && isDateChanged ? { plan: { itemId: plan.itemId, date: planDate } } : {}),
      })
      upsertTasks([updated.task])
      applyPlans(updated.plans)
      dialogRef.current?.close()
    } catch (error: unknown) {
      setMessage(requestErrorMessage(error, '옮기지 못했습니다. 잠시 후 다시 시도해 주세요.'))
      setIsSaving(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      aria-labelledby="task-move-title"
      aria-busy={isSaving}
      onCancel={(event) => { if (isSaving) event.preventDefault() }}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <form
        className={`${styles.modal} ${modalStyles.surface}`}
        onSubmit={(event) => { event.preventDefault(); void save() }}
      >
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="task-move-title">이동하기</h2>
            <p>{taskTitle}</p>
          </div>
          <button type="button" className={styles.close} aria-label="이동 창 닫기" disabled={isSaving} onClick={requestClose}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <label className={styles.field}>
            <span>폴더</span>
            <select
              value={folderId ?? ''}
              disabled={isSaving}
              onChange={(event) => setFolderId(event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">미분류</option>
              {folders.map((folder) => (
                <option value={folder.id} key={folder.id}>{folder.name}</option>
              ))}
            </select>
          </label>

          <fieldset className={styles.flags}>
            <legend>표시</legend>
            <label>
              <input
                type="checkbox"
                checked={priority}
                disabled={isSaving}
                onChange={(event) => setPriority(event.target.checked)}
              />
              <span aria-hidden="true">📌</span> 중요
            </label>
            <label>
              <input
                type="checkbox"
                checked={urgent}
                disabled={isSaving}
                onChange={(event) => setUrgent(event.target.checked)}
              />
              <span aria-hidden="true">⚡</span> 즉시
            </label>
          </fieldset>

          {plan && (
            <label className={styles.field}>
              <span>날짜</span>
              <input
                type="date"
                value={planDate}
                disabled={isSaving}
                onChange={(event) => setPlanDate(event.target.value)}
              />
            </label>
          )}

          {message && <p className={styles.message} role="alert">{message}</p>}
        </div>

        <footer className={styles.footer}>
          <button type="button" disabled={isSaving} onClick={requestClose}>취소</button>
          <button type="submit" className={styles.submit} disabled={!hasChange || isSaving}>
            {isSaving && <IconLoader2 className={styles.spinner} size={16} aria-hidden="true" />}
            옮기기
          </button>
        </footer>
      </form>
    </dialog>
  )
}
