import { useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, MouseEvent } from 'react'
import {
  IconClock,
  IconLoader2,
  IconMapPin,
  IconPlayerPlay,
  IconUser,
  IconUsers,
  IconX,
} from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import type { DailyPlanItem } from '../plans/dailyPlanTypes'
import { startPersonalSession } from './sessionApi'
import { GROUP_ROOM_MOCK, SESSION_PLACE_MOCKS } from './sessionMocks'
import type { SessionResponse } from './sessionTypes'
import styles from './CreateSessionModal.module.css'

interface CreateSessionModalProps {
  todayTasks: DailyPlanItem[]
  initialTaskId?: number
  onClose: () => void
  onStarted: (session: SessionResponse) => void
}

type SessionMode = 'personal' | 'group'
type DurationPreset = 25 | 45 | 60 | 'custom'

function formatTotalTime(minutes: number) {
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  if (hours && remainingMinutes) return `${hours}시간 ${remainingMinutes}분`
  if (hours) return `${hours}시간`
  return `${remainingMinutes}분`
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null
}

export default function CreateSessionModal({
  todayTasks,
  initialTaskId,
  onClose,
  onStarted,
}: CreateSessionModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const linkedTasks = todayTasks.filter(
    (task): task is DailyPlanItem & { taskId: number } => task.taskId !== null,
  )
  const [selectedTaskIds, setSelectedTaskIds] = useState<number[]>(
    initialTaskId !== undefined
      ? [initialTaskId]
      : linkedTasks[0]
        ? [linkedTasks[0].taskId]
        : [],
  )
  const [mode, setMode] = useState<SessionMode>('personal')
  const [placeId, setPlaceId] = useState(SESSION_PLACE_MOCKS[0].id)
  const [durationPreset, setDurationPreset] = useState<DurationPreset>(45)
  const [customMinutes, setCustomMinutes] = useState('')
  const [repeat, setRepeat] = useState(2)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [mockNotice, setMockNotice] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    dialog.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  const durationMinutes = durationPreset === 'custom' ? Number(customMinutes) : durationPreset
  const validDuration = Number.isInteger(durationMinutes) && durationMinutes >= 1 && durationMinutes <= 1440
  const totalMinutes = validDuration ? durationMinutes * repeat + Math.max(0, repeat - 1) * 5 : 0
  const selectedPlace = SESSION_PLACE_MOCKS.find((place) => place.id === placeId)
  const selectedTaskTitles = useMemo(
    () => selectedTaskIds.map(
      (taskId) => linkedTasks.find((task) => task.taskId === taskId)?.title,
    ).filter((title): title is string => title !== undefined),
    [selectedTaskIds, linkedTasks],
  )

  const toggleTask = (taskId: number) => {
    setSelectedTaskIds((current) => current.includes(taskId)
      ? current.filter((selectedTaskId) => selectedTaskId !== taskId)
      : [...current, taskId])
  }

  const requestClose = () => {
    if (!isSubmitting) dialogRef.current?.close()
  }

  const handleBackdrop = (event: MouseEvent<HTMLDialogElement>) => {
    if (event.target === event.currentTarget) requestClose()
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitError(null)
    setMockNotice(null)

    if (selectedTaskIds.length === 0) {
      setSubmitError('세션에서 진행할 Task를 하나 이상 선택해 주세요.')
      return
    }
    if (mode === 'group') {
      setMockNotice(`${GROUP_ROOM_MOCK.startsAtLabel} ${GROUP_ROOM_MOCK.city} 그룹 세션 참여를 선택했습니다. API 연결은 준비 중입니다.`)
      return
    }
    if (!selectedPlace) {
      setSubmitError('집중할 place를 선택해 주세요.')
      return
    }
    if (!validDuration) {
      setSubmitError('집중 시간은 1분 이상 1,440분 이하로 입력해 주세요.')
      return
    }

    setIsSubmitting(true)
    try {
      onStarted(await startPersonalSession({
        taskIds: selectedTaskIds,
        plannedDurationSec: durationMinutes * 60,
      }))
    } catch (error) {
      setSubmitError(
        isApiError(error) && error.message
          ? error.message
          : '세션을 시작하지 못했습니다. 설정을 확인한 뒤 다시 시도해 주세요.',
      )
      setIsSubmitting(false)
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby="create-session-title"
      aria-describedby="create-session-description"
      aria-busy={isSubmitting}
      onCancel={(event) => { if (isSubmitting) event.preventDefault() }}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <section className={styles.modal}>
        <header className={styles.header}>
          <div>
            <h2 id="create-session-title">세션 시작</h2>
            <p id="create-session-description">오늘 어디서, 무엇을 해볼까요</p>
          </div>
          {/*<span className={styles['header-icon']} aria-hidden="true"><IconRoute size={21}/></span>*/}
          <button type="button" className={styles.close} aria-label="세션 시작 창 닫기" disabled={isSubmitting}
                  onClick={requestClose}><IconX size={19}/></button>
        </header>

        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <div className={styles.body}>
            <fieldset className={styles.fieldset}>
              <legend><span>1</span>무엇을 할까요</legend>
              <p className={styles.hint}>오늘 계획에서 함께 진행할 Task를 모두 선택해 주세요.</p>
              <div className={styles.choices}>
                {linkedTasks.length === 0 ? <p className={styles.empty}>오늘 계획에 담긴 Task가 없습니다.</p> : linkedTasks.map((task) => (
                  <label className={styles['task-choice']} key={task.taskId}>
                    <input
                      type="checkbox"
                      name="session-task"
                      value={task.taskId}
                      checked={selectedTaskIds.includes(task.taskId)}
                      onChange={() => toggleTask(task.taskId)}
                      disabled={isSubmitting}
                    />
                    <span><strong>{task.title}</strong><small>{task.projectName}</small></span>
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset className={styles.fieldset}>
              <legend><span>2</span>어떻게 할까요</legend>
              <div className={styles['mode-grid']}>
                <label className={styles['mode-choice']}>
                  <input type="radio" name="session-mode" checked={mode === 'personal'} onChange={() => setMode('personal')} disabled={isSubmitting} />
                  <IconUser size={19} aria-hidden="true" /><span><strong>혼자</strong><small>지금 바로 시작</small></span>
                </label>
                <label className={styles['mode-choice']}>
                  <input type="radio" name="session-mode" checked={mode === 'group'} onChange={() => setMode('group')} disabled={isSubmitting} />
                  <IconUsers size={19} aria-hidden="true" /><span><strong>함께</strong><small>{GROUP_ROOM_MOCK.startsAtLabel} {GROUP_ROOM_MOCK.city} · {GROUP_ROOM_MOCK.participantCount}명</small></span>
                </label>
              </div>
              <p className={styles.hint}>{mode === 'group' ? `place와 타이머는 Room 설정을 따릅니다. (${GROUP_ROOM_MOCK.city} · ${GROUP_ROOM_MOCK.durationMin}분)` : '함께 참여하면 place와 타이머는 Room 설정을 따릅니다.'}</p>
            </fieldset>

            {mode === 'personal' && (
              <>
                <fieldset className={styles.fieldset}>
                  <legend><span>3</span>어디서 할까요</legend>
                  <div className={styles['place-grid']}>
                    {SESSION_PLACE_MOCKS.map((place) => (
                      <label className={styles['place-choice']} key={place.id}>
                        <input type="radio" name="session-place" checked={placeId === place.id} onChange={() => setPlaceId(place.id)} disabled={isSubmitting} />
                        <IconMapPin size={17} aria-hidden="true" /><span><strong>{place.city}</strong><small>{place.name}</small></span>
                      </label>
                    ))}
                  </div>
                </fieldset>

                <fieldset className={styles.fieldset}>
                  <legend><span>4</span>얼마나 집중할까요</legend>
                  <div className={styles['timer-grid']}>
                    <div>
                      <p className={styles.label}>한 번에</p>
                      <div className={styles.durations}>
                        {([25, 45, 60] as const).map((minutes) => (
                          <label key={minutes}><input type="radio" name="duration" checked={durationPreset === minutes} onChange={() => setDurationPreset(minutes)} disabled={isSubmitting} /><span>{minutes}분</span></label>
                        ))}
                        <label><input type="radio" name="duration" checked={durationPreset === 'custom'} onChange={() => setDurationPreset('custom')} disabled={isSubmitting} /><span>커스텀</span></label>
                      </div>
                      {durationPreset === 'custom' && <label className={styles.custom}><span>집중 시간</span><input type="number" min={1} max={1440} value={customMinutes} onChange={(event) => setCustomMinutes(event.target.value)} aria-invalid={Boolean(customMinutes) && !validDuration} disabled={isSubmitting} /><span>분</span></label>}
                    </div>
                    <div>
                      <p className={styles.label}>반복 <span>목업</span></p>
                      <div className={styles.repeat}>
                        <button type="button" aria-label="반복 줄이기" onClick={() => setRepeat((value) => Math.max(1, value - 1))} disabled={isSubmitting || repeat === 1}>−</button>
                        <strong>{repeat}</strong>
                        <button type="button" aria-label="반복 늘리기" onClick={() => setRepeat((value) => Math.min(8, value + 1))} disabled={isSubmitting || repeat === 8}>+</button>
                      </div>
                    </div>
                  </div>
                  <div className={styles.summary}><IconClock size={17} aria-hidden="true" /><p>총 <strong>{formatTotalTime(totalMinutes)}</strong> · 사이에 5분 휴식이 들어갑니다</p></div>
                </fieldset>
              </>
            )}

            <p className={styles.review}><strong>{selectedTaskTitles.length > 0 ? `${selectedTaskTitles[0]}${selectedTaskTitles.length > 1 ? ` 외 ${selectedTaskTitles.length - 1}개` : ''}` : 'Task 미선택'}</strong>{mode === 'personal' && selectedPlace ? ` · ${selectedPlace.city} · ${validDuration ? `${durationMinutes}분` : '시간 미입력'}` : ` · ${GROUP_ROOM_MOCK.city} 그룹`}</p>
            {submitError && <p className={styles.error} role="alert">{submitError}</p>}
            {mockNotice && <p className={styles.notice} role="status">{mockNotice}</p>}
          </div>

          <footer className={styles.footer}>
            <button type="button" className={styles.cancel} onClick={requestClose} disabled={isSubmitting}>취소</button>
            <button type="submit" className={styles.submit} disabled={isSubmitting || selectedTaskIds.length === 0 || (mode === 'personal' && !validDuration)}>
              {isSubmitting ? <IconLoader2 className={styles.spinner} size={17} aria-hidden="true" /> : <IconPlayerPlay size={17} aria-hidden="true" />}
              {isSubmitting ? '시작 중…' : mode === 'group' ? '참여 확인' : '시작하기'}
            </button>
          </footer>
        </form>
      </section>
    </dialog>
  )
}
