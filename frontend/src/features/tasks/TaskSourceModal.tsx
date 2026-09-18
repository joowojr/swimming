import { useEffect, useRef } from 'react'
import type { MouseEvent } from 'react'
import { IconX } from '@tabler/icons-react'
import modalStyles from '../../components/ModalShell.module.css'
import TaskSourceList from './TaskSourceList'
import styles from './TaskSourceModal.module.css'

interface TaskSourceModalProps {
  taskId: number
  taskTitle: string
  /** 고를 수 있는 링크의 범위. 미분류 할 일은 볼 폴더가 없어 null이다. */
  folderId: number | null
  onClose: () => void
}

/**
 * 역할: 폴더에 저장한 링크를 골라 할 일에 연결한다.
 *
 * 수정 모달과 나눠 둔다. 저장 버튼을 눌러야 반영되는 할 일의 속성과 달리 연결은 누르는
 * 즉시 붙고 떨어져서, 한 창에 두면 무엇이 저장을 기다리는 값인지 흐려진다.
 */
export default function TaskSourceModal({ taskId, taskTitle, folderId, onClose }: TaskSourceModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    dialogRef.current?.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  const requestClose = () => dialogRef.current?.close()
  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  return (
    <dialog
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <div className={`${styles.modal} ${modalStyles.surface}`} aria-labelledby="task-sources-heading">
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="task-sources-heading">링크 연결하기</h2>
            <p>{taskTitle}</p>
          </div>
          <button type="button" className={styles.close} aria-label="링크 창 닫기" onClick={requestClose}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          <TaskSourceList taskId={taskId} folderId={folderId} />
        </div>
      </div>
    </dialog>
  )
}
