import { useEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { IconCheck, IconPlus, IconX } from '@tabler/icons-react'
import ActionButton from '../../components/ActionButton'
import { linkWorkItems } from './agentWorkApi'
import { sessionTitle } from './agentWorkBoard'
import { workItemKey } from './agentWorkKeys'
import { UNCATEGORIZED_LABEL } from './agentWorkLabels'
import type { AgentSession, AgentWorkItem } from './agentWorkTypes'
import styles from './SessionWorkItemsModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'

interface SessionWorkItemsModalProps {
  session: AgentSession
  /** 보드의 모든 카드. 이 세션에 붙어 있지 않은 할 일이 후보가 된다. */
  cards: AgentWorkItem[]
  onClose: () => void
  onLinked: () => void
}

interface CandidateGroup {
  label: string
  hint: string
  cards: AgentWorkItem[]
}

export default function SessionWorkItemsModal({ session, cards, onClose, onLinked }: SessionWorkItemsModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [stagedKeys, setStagedKeys] = useState<string[]>([])
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    dialog.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  const groups: CandidateGroup[] = [
    {
      label: '시작 전',
      hint: '아직 Agent가 시작하지 않은 할 일',
      cards: cards.filter((card) => !card.session),
    },
    {
      label: '다른 세션에서 옮김',
      hint: '지금 붙어 있는 세션에서 떼어 이 세션으로 옮깁니다',
      cards: cards.filter((card) => card.session && card.session.id !== session.id),
    },
  ].filter((group) => group.cards.length > 0)

  const staged = stagedKeys.flatMap((key) => cards.filter((card) => workItemKey(card) === key))

  const toggle = (card: AgentWorkItem) => {
    const key = workItemKey(card)
    setError(null)
    setStagedKeys((current) => (
      current.includes(key) ? current.filter((staged) => staged !== key) : [...current, key]
    ))
  }

  const requestClose = () => {
    if (!isSubmitting) dialogRef.current?.close()
  }

  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const submit = async () => {
    if (staged.length === 0 || isSubmitting) return

    setIsSubmitting(true)
    setError(null)
    try {
      // 고른 할 일을 한 번에 붙인다. 다른 세션 소속은 서버가 떼어 내고 옮긴다.
      await linkWorkItems(session.id, staged.map((card) => ({
        resourceType: card.workItem.type,
        resourceId: card.workItem.id,
      })))
      onLinked()
      dialogRef.current?.close()
    } catch {
      setError('할 일을 붙이지 못했어요. 세션이 끝났을 수 있어요.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      aria-labelledby="session-work-items-title"
      aria-busy={isSubmitting}
      onCancel={(event) => { if (isSubmitting) event.preventDefault() }}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <form
        className={`${styles.modal} ${modalStyles.surface}`}
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <h2 id="session-work-items-title">세션에 할 일 붙이기</h2>
            <p>{sessionTitle(session)}</p>
          </div>
          <button type="button" aria-label="창 닫기" disabled={isSubmitting} onClick={requestClose}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.panes}>
          <div className={styles.body}>
            {groups.length === 0 ? (
              <p className={styles.state}>붙일 수 있는 할 일이 없어요.</p>
            ) : groups.map((group) => (
              <section className={styles.group} key={group.label}>
                <div className={styles['group-heading']}>
                  <h3>{group.label}</h3>
                  <span>{group.hint}</span>
                </div>
                <ul>
                  {group.cards.map((card) => {
                    const key = workItemKey(card)
                    const isStaged = stagedKeys.includes(key)
                    return (
                      <li className={isStaged ? styles.selected : undefined} key={key}>
                        <span className={styles['card-title']}>
                          {(card.workItem.urgent || card.workItem.important) && (
                            <span aria-label={`${card.workItem.urgent ? '즉시 ' : ''}${card.workItem.important ? '중요' : ''}`}>
                              {card.workItem.urgent ? '⚡️' : ''}{card.workItem.important ? '📌' : ''}{' '}
                            </span>
                          )}
                          {card.workItem.title}
                          <small>{card.workItem.containerName ?? UNCATEGORIZED_LABEL}</small>
                        </span>
                        <button
                          type="button"
                          aria-pressed={isStaged}
                          aria-label={`${card.workItem.title} ${isStaged ? '담기 취소' : '담기'}`}
                          disabled={isSubmitting}
                          onClick={() => toggle(card)}
                        >
                          {isStaged
                            ? <IconCheck size={16} aria-hidden="true" />
                            : <IconPlus size={16} aria-hidden="true" />}
                        </button>
                      </li>
                    )
                  })}
                </ul>
              </section>
            ))}
          </div>

          <section className={styles.stage} aria-labelledby="session-staged-title">
            <div className={styles['stage-heading']}>
              <h3 id="session-staged-title">붙일 할 일</h3>
              <span>{staged.length}개</span>
            </div>
            {staged.length === 0 ? (
              <p className={styles['stage-empty']}>왼쪽에서 담은 할 일이 여기에 모입니다.</p>
            ) : (
              <ul>
                {staged.map((card) => (
                  <li key={workItemKey(card)}>
                    <span>
                      <small>{card.workItem.containerName ?? UNCATEGORIZED_LABEL}</small>
                      <strong>{card.workItem.title}</strong>
                    </span>
                    <button
                      type="button"
                      aria-label={`${card.workItem.title} 빼기`}
                      disabled={isSubmitting}
                      onClick={() => toggle(card)}
                    >
                      <IconX size={16} aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <footer className={styles.footer}>
          {error && <p className={styles.error} role="alert">{error}</p>}
          <ActionButton
            type="submit"
            isLoading={isSubmitting}
            loadingLabel="붙이는 중…"
            disabled={staged.length === 0}
          >
            {staged.length === 0 ? '할 일 붙이기' : `할 일 ${staged.length}개 붙이기`}
          </ActionButton>
        </footer>
      </form>
    </dialog>
  )
}
