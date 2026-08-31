import type {FormEvent, MouseEvent} from 'react'
import {useEffect, useMemo, useRef, useState} from 'react'
import {IconUser, IconUsers, IconX,} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ChecklistCard from '../../components/ChecklistCard'
import type {DailyPlanItem} from '../plans/dailyPlanTypes'
import {TASK_STATUS_LABEL} from '../tasks/taskLabels'
import type {TaskStatus} from '../tasks/taskTypes'
import {getPlaces} from '../places/placeApi'
import type {City, Place} from '../places/placeTypes'
import {startPersonalSession} from './sessionApi'
import {GROUP_ROOM_MOCK} from './sessionMocks'
import type {SessionResponse} from './sessionTypes'
import styles from './CreateSessionModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'
import ActionButton from "../../components/ActionButton.tsx";

interface CreateSessionModalProps {
  todayTasks: DailyPlanItem[]
  initialTaskId?: number
  onClose: () => void
  onStarted: (session: SessionResponse) => void
}

type SessionMode = 'personal' | 'group'
type DurationPreset = 25 | 45 | 60 | 'custom'
type PlacesStatus = 'loading' | 'ready' | 'error'

// 지금 하는 일을 먼저, 끝난 일을 마지막에 둔다.
const TASK_STATUS_ORDER: Record<TaskStatus, number> = {
  DOING: 0,
  TODO: 1,
  HOLD: 2,
  DONE: 3,
}

interface PlaceOption {
  city: City
  place: Place
}

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
  // 같은 상태끼리는 계획에 담은 순서를 유지한다(Array.prototype.sort는 안정 정렬).
  const linkedTasks = useMemo(
    () => [...todayTasks].sort(
      (first, second) => TASK_STATUS_ORDER[first.status] - TASK_STATUS_ORDER[second.status],
    ),
    [todayTasks],
  )
  const [selectedTaskIds, setSelectedTaskIds] = useState<number[]>(
    initialTaskId !== undefined ? [initialTaskId] : [],
  )
  const [mode, setMode] = useState<SessionMode>('personal')
  const [cities, setCities] = useState<City[]>([])
  const [placesStatus, setPlacesStatus] = useState<PlacesStatus>('loading')
  const [placeId, setPlaceId] = useState<number | null>(null)
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

  useEffect(() => {
    let active = true
    void getPlaces()
      .then((response) => {
        if (!active) return
        const firstPlace = response.flatMap((city) => city.places)[0]
        setCities(response)
        setPlaceId((current) => current ?? firstPlace?.id ?? null)
        setPlacesStatus('ready')
      })
      .catch(() => {
        if (active) setPlacesStatus('error')
      })
    return () => { active = false }
  }, [])

  const durationMinutes = durationPreset === 'custom' ? Number(customMinutes) : durationPreset
  const validDuration = Number.isInteger(durationMinutes) && durationMinutes >= 1 && durationMinutes <= 1440
  const totalMinutes = validDuration ? durationMinutes * repeat + Math.max(0, repeat - 1) * 5 : 0
  const placeOptions = useMemo<PlaceOption[]>(
    () => cities.flatMap((city) => city.places.map((place) => ({ city, place }))),
    [cities],
  )
  const selectedPlace = placeOptions.find(({ place }) => place.id === placeId)
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
      setSubmitError('세션에서 진행할 작업을 하나 이상 선택해 주세요.')
      return
    }
    if (mode === 'group') {
      setMockNotice(`${GROUP_ROOM_MOCK.startsAtLabel} ${GROUP_ROOM_MOCK.city} 다이브 세션 참여를 선택했습니다.`)
      return
    }
    if (!selectedPlace) {
      setSubmitError('집중할 공간을 선택해 주세요.')
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
        placeId: selectedPlace.place.id,
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
      id="create-session-dialog"
      ref={dialogRef}
      className={`${styles.dialog} ${modalStyles.dialog}`}
      aria-labelledby="create-session-title"
      aria-busy={isSubmitting}
      onCancel={(event) => { if (isSubmitting) event.preventDefault() }}
      onClose={onClose}
      onMouseDown={handleBackdrop}
    >
      <section className={`${styles.modal} ${modalStyles.surface}`}>
        <header className={`${styles.header} ${modalStyles.header}`}>
          <h2 id="create-session-title">다이브 세션</h2>
          <button
            type="button"
            className={styles.close}
            aria-label="세션 생성 창 닫기"
            disabled={isSubmitting}
            onClick={requestClose}
          >
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <div className={styles.body}>
            <fieldset className={styles.fieldset}>
              <legend>무엇을 할까요</legend>
              <p className={styles.hint}>오늘 계획에서 함께 진행할 작업을 모두 선택해 주세요.</p>
              {linkedTasks.length === 0 ? (
                <p className={styles.empty}>오늘 계획에 담긴 Task가 없습니다.</p>
              ) : (
                <ul className={styles['task-list']}>
                  {linkedTasks.map((task) => (
                    <li key={task.taskId}>
                      <ChecklistCard
                        id={task.taskId}
                        title={task.title}
                        checked={selectedTaskIds.includes(task.taskId)}
                        ariaLabel={`${task.title} 선택`}
                        name="session-task"
                        disabled={isSubmitting}
                        onToggle={() => toggleTask(task.taskId)}
                        actions={(
                          <span className={styles['task-status-chip']} data-status={task.status}>
                            {TASK_STATUS_LABEL[task.status]}
                          </span>
                        )}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </fieldset>

            <fieldset className={styles.fieldset}>
              <legend>어떻게 할까요 </legend>
              <div className={styles['mode-grid']}>
                <label className={styles['mode-choice']}>
                  <input type="radio" name="session-mode" checked={mode === 'personal'} onChange={() => setMode('personal')} disabled={isSubmitting} />
                  <IconUser size={19} aria-hidden="true" />
                  <span>개인</span>
                </label>
                <label className={styles['mode-choice']}>
                  <input type="radio" name="session-mode" checked={mode === 'group'} onChange={() => setMode('group')} disabled={isSubmitting || mode === 'group'} />
                  <IconUsers size={19} aria-hidden="true" />
                  <span>그룹</span>
                </label>
              </div>
              <p className={styles.hint}>
                {mode === 'group'
                  ? `공간과 타이머는 Room 설정을 따릅니다. ${GROUP_ROOM_MOCK.startsAtLabel} ${GROUP_ROOM_MOCK.city} · ${GROUP_ROOM_MOCK.durationMin}분 · ${GROUP_ROOM_MOCK.participantCount}명`
                  : '지금 바로 시작합니다.'}
              </p>
            </fieldset>

            {mode === 'personal' && (
              <>
                <fieldset className={styles.fieldset}>
                  <legend>어디서 할까요 <span>(공간 선택)</span></legend>

                  {placesStatus === 'loading' && <p className={styles.empty} role="status">공간을 불러오는 중…</p>}
                  {placesStatus === 'error' && <p className={styles.empty} role="alert">공간을 불러오지 못했습니다. 창을 닫고 다시 시도해 주세요.</p>}
                  {placesStatus === 'ready' && placeOptions.length === 0 && <p className={styles.empty}>현재 선택할 수 있는 공간이 없습니다.</p>}
                  {placeOptions.length > 0 && (
                    <div className={styles['place-grid']}>
                      {placeOptions.map(({ city, place }) => (
                        <label className={styles['place-choice']} key={place.id}>
                          <input type="radio" name="session-place" checked={placeId === place.id} onChange={() => setPlaceId(place.id)} disabled={isSubmitting} />
                          <span className={styles['place-mark']} aria-hidden="true">{city.name.slice(0, 1)}</span>
                          <span className={styles['place-name']}>
                            <span className="sr-only">{city.name} </span>{place.name}
                          </span>
                        </label>
                      ))}
                    </div>
                  )}
                </fieldset>

                <div className={styles.split}>
                  <fieldset className={styles.fieldset}>
                    <legend>얼마나 집중할까요</legend>
                    <p className={styles['value-box']}>
                      {durationPreset === 'custom' ? (
                        <label className={styles['custom-value']}>
                          <span className="sr-only">집중 시간</span>
                          <input
                            type="number"
                            min={1}
                            max={1440}
                            value={customMinutes}
                            placeholder="—"
                            onChange={(event) => setCustomMinutes(event.target.value)}
                            aria-invalid={Boolean(customMinutes) && !validDuration}
                            disabled={isSubmitting}
                          />
                          <span aria-hidden="true">분</span>
                        </label>
                      ) : <><strong>{validDuration ? durationMinutes : '—'}</strong>분</>}
                    </p>
                    <div className={styles.durations}>
                      {([25, 45, 60] as const).map((minutes) => (
                        <label key={minutes}>
                          <input type="radio" name="duration" checked={durationPreset === minutes} onChange={() => setDurationPreset(minutes)} disabled={isSubmitting} />
                          <span>{minutes}분</span>
                        </label>
                      ))}
                      <label>
                        <input type="radio" name="duration" checked={durationPreset === 'custom'} onChange={() => setDurationPreset('custom')} disabled={isSubmitting} />
                        <span>직접 입력</span>
                      </label>
                    </div>
                  </fieldset>

                  <fieldset className={styles.fieldset}>
                    <legend>반복</legend>
                    <div className={styles.repeat}>
                      <button type="button" aria-label="반복 줄이기" onClick={() => setRepeat((value) => Math.max(1, value - 1))} disabled={isSubmitting || repeat === 1}>−</button>
                      <strong>{repeat}</strong>
                      <button type="button" aria-label="반복 늘리기" onClick={() => setRepeat((value) => Math.min(8, value + 1))} disabled={isSubmitting || repeat === 8}>+</button>
                    </div>
                    <p className={styles.hint}>총 {formatTotalTime(totalMinutes)} · 사이에 5분 휴식</p>
                  </fieldset>
                </div>
              </>
            )}

            <p className={styles.review}>
              <strong>
                {selectedTaskTitles.length > 0
                  ? `${selectedTaskTitles[0]}${selectedTaskTitles.length > 1 ? ` 외 ${selectedTaskTitles.length - 1}개` : ''}`
                  : '할 일 미선택'}
              </strong>
              {mode === 'personal' && selectedPlace
                ? ` · ${selectedPlace.city.name} · ${validDuration ? `${durationMinutes}분` : '시간 미입력'}`
                : ` · ${GROUP_ROOM_MOCK.city} 그룹`}
            </p>
            {submitError && <p className={styles.error} role="alert">{submitError}</p>}
            {mockNotice && <p className={styles.notice} role="status">{mockNotice}</p>}
          </div>

          <ActionButton
              type="submit"
              className={styles.submit}
              isLoading={isSubmitting}
              loadingLabel="시작 중…"
              disabled={
                  isSubmitting ||
                  selectedTaskIds.length === 0 ||
                  mode === 'group' ||
                  (mode === 'personal' && (!validDuration || !selectedPlace))
              }
          >
            {mode === 'group' ? '준비 중' : '시작하기'}
          </ActionButton>
        </form>
      </section>
    </dialog>
  )
}
