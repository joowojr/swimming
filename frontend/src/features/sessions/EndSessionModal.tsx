import type {FormEvent, MouseEvent} from 'react'
import {useEffect, useRef, useState} from 'react'
import {IconChecklist, IconClock, IconX} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ActionButton from '../../components/ActionButton'
import TaskChecklist from '../../components/TaskChecklist'
import {endSession} from './sessionApi'
import type {EndSessionRequest, SessionTask} from './sessionTypes'
import styles from './EndSessionModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'

interface EndSessionModalProps {
    sessionId: number
    tasks: SessionTask[]
    onClose: () => void
    onEnded: () => void
}

const SUMMARY_MAX_LENGTH = 255

function validateSummary(value: string) {
    if (value.trim().length > SUMMARY_MAX_LENGTH) {
        return `한 줄 기록은 ${SUMMARY_MAX_LENGTH}자 이하로 입력해 주세요.`
    }
    return undefined
}

function isApiError(error: unknown): error is ApiError {
    return typeof error === 'object' && error !== null
}

export default function EndSessionModal({
                                            sessionId,
                                            tasks,
                                            onClose,
                                            onEnded,
                                        }: EndSessionModalProps) {
    const dialogRef = useRef<HTMLDialogElement>(null)
    const summaryInputRef = useRef<HTMLTextAreaElement>(null)
    const [completedTaskIds, setCompletedTaskIds] = useState<number[]>([])
    const [summary, setSummary] = useState('')
    const [usePlannedDuration, setUsePlannedDuration] = useState(false)
    const [isSummaryTouched, setIsSummaryTouched] = useState(false)
    const [summaryError, setSummaryError] = useState<string | undefined>(undefined)
    const [submitError, setSubmitError] = useState<string | null>(null)
    const [isSubmitting, setIsSubmitting] = useState(false)

    useEffect(() => {
        const dialog = dialogRef.current
        if (!dialog) return

        dialog.showModal()
        summaryInputRef.current?.focus()

        const previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'

        return () => {
            document.body.style.overflow = previousOverflow
        }
    }, [])

    const requestClose = () => {
        if (!isSubmitting) dialogRef.current?.close()
    }

    const handleBackdropMouseDown = (event: MouseEvent<HTMLDialogElement>) => {
        if (event.target === event.currentTarget) requestClose()
    }

    const toggleTask = (taskId: number) => {
        setCompletedTaskIds((current) =>
            current.includes(taskId)
                ? current.filter((id) => id !== taskId)
                : [...current, taskId],
        )
    }

    const submit = async (request: EndSessionRequest) => {
        setSubmitError(null)
        setIsSubmitting(true)
        try {
            await endSession(sessionId, request)
            onEnded()
        } catch (error) {
            if (isApiError(error) && error.errors?.summary) {
                setSummaryError(error.errors.summary)
            }
            setSubmitError(
                isApiError(error) && error.message
                    ? error.message
                    : '세션을 마치지 못했습니다. 잠시 후 다시 시도해 주세요.',
            )
            setIsSubmitting(false)
        }
    }

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()

        const nextSummaryError = validateSummary(summary)
        setIsSummaryTouched(true)
        setSummaryError(nextSummaryError)
        if (nextSummaryError) {
            summaryInputRef.current?.focus()
            return
        }

        void submit({
            summary: summary.trim() || null,
            usePlannedDuration,
            taskResults: tasks.map((task) => ({
                taskId: task.id,
                isCompleted: completedTaskIds.includes(task.id),
            })),
        })
    }

    return (
        <dialog
            id="end-session-dialog"
            ref={dialogRef}
            className={`${styles['end-session-dialog']} ${modalStyles.dialog}`}
            aria-labelledby="end-session-title"
            aria-describedby="end-session-description"
            aria-busy={isSubmitting}
            onCancel={(event) => {
                if (isSubmitting) event.preventDefault()
            }}
            onClose={onClose}
            onMouseDown={handleBackdropMouseDown}
        >
            <section className={`${styles['end-session-modal']} ${modalStyles.surface}`}>
                <header className={`${styles['modal-header']} ${modalStyles.header}`}>
                    <div>
                        <h2 id="end-session-title">기록하기</h2>
                        <p id="end-session-description">
                            이번 집중에서 끝낸 내용을 남겨 두세요. 건너뛰어도 집중 시간은 그대로 기록됩니다.
                        </p>
                    </div>
                    <button
                        type="button"
                        className={styles['modal-close']}
                        aria-label="세션 마무리 창 닫기"
                        onClick={requestClose}
                        disabled={isSubmitting}
                    >
                        <IconX size={20} aria-hidden="true"/>
                    </button>
                </header>

                <form onSubmit={handleSubmit} noValidate>
                    <div className={styles['modal-body']}>
                        <fieldset className={styles['modal-field']}>
                            <legend><IconClock aria-hidden="true"/>끝낸 시간 <span>선택</span></legend>
                            <label className={styles['planned-duration']}>
                                <input
                                    type="checkbox"
                                    checked={usePlannedDuration}
                                    onChange={(event) => setUsePlannedDuration(event.target.checked)}
                                    disabled={isSubmitting}
                                />
                                <span>계획한 시간으로 마칩니다.</span>
                            </label>
                        </fieldset>
                        <fieldset className={styles['modal-field']}>
                            <legend><IconChecklist aria-hidden="true"/>끝낸 할 일 <span>선택</span></legend>
                            <TaskChecklist
                                items={tasks.map((task) => ({
                                    id: task.id,
                                    title: task.title,
                                    description: task.projectName ?? undefined,
                                }))}
                                selectedIds={completedTaskIds}
                                name="completed-session-task"
                                emptyMessage="이 세션에 연결된 Task가 없습니다."
                                disabled={isSubmitting}
                                onToggle={toggleTask}
                            />
                            <p className={styles['modal-field-message']} aria-live="polite">
                                {' '}
                            </p>
                        </fieldset>

                        <div className={styles['modal-field']}>
                            <label htmlFor="session-summary">한 줄 기록 <span>선택</span></label>
                            <textarea
                                ref={summaryInputRef}
                                id="session-summary"
                                value={summary}
                                rows={3}
                                maxLength={SUMMARY_MAX_LENGTH}
                                placeholder="예: 초안 절반까지 정리했어요."
                                aria-invalid={Boolean(summaryError)}
                                aria-describedby="session-summary-error"
                                onBlur={() => {
                                    setIsSummaryTouched(true)
                                    setSummaryError(validateSummary(summary))
                                }}
                                onChange={(event) => {
                                    const value = event.target.value
                                    setSummary(value)
                                    if (isSummaryTouched) {
                                        setSummaryError(validateSummary(value))
                                    }
                                }}
                                disabled={isSubmitting}
                            />
                            <p
                                className={styles['modal-field-message']}
                                id="session-summary-error"
                                aria-live="polite"
                            >
                                {summaryError ?? ' '}
                            </p>
                        </div>

                        {submitError && (
                            <p className={styles['modal-submit-error']} role="alert">{submitError}</p>
                        )}
                    </div>

                    <footer className={styles['modal-footer']}>
                        <ActionButton
                            type="submit"
                            className={styles['modal-submit']}
                            isLoading={isSubmitting}
                            loadingLabel="기록 중…"
                        >
                            세션 마치기
                        </ActionButton>
                    </footer>
                </form>
            </section>
        </dialog>
    )
}
