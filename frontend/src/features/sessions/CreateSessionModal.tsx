import type {FormEvent, MouseEvent} from 'react'
import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {IconUser, IconUsers, IconX,} from '@tabler/icons-react'
import type {ApiError} from '../../api/client'
import ChecklistCard from '../../components/ChecklistCard'
import ModeToggle from '../../components/ModeToggle'
import type {DailyPlanItem} from '../plans/dailyPlanTypes'
import {TASK_STATUS_LABEL} from '../tasks/taskLabels'
import type {TaskStatus} from '../tasks/taskTypes'
import {getPlaces} from '../places/placeApi'
import type {City, Place} from '../places/placeTypes'
import {startPersonalSession} from './sessionApi'
import {GROUP_ROOM_MOCK} from './sessionMocks'
import type {SessionDetailResponse} from './sessionTypes'
import styles from './CreateSessionModal.module.css'
import modalStyles from '../../components/ModalShell.module.css'
import ActionButton from "../../components/ActionButton.tsx";

interface CreateSessionModalProps {
  todayTasks: DailyPlanItem[]
  initialTaskId?: number
  onClose: () => void
  onStarted: (session: SessionDetailResponse) => void
}

type SessionMode = 'personal' | 'group'
type DurationPreset = 25 | 45 | 60 | 'custom'
type BreakPreset = 5 | 10 | 15 | 'custom'
type PlacesStatus = 'loading' | 'ready' | 'error'

// 지금 하는 일을 먼저, 끝난 일을 마지막에 둔다.
const TASK_STATUS_ORDER: Record<TaskStatus, number> = {
  DOING: 0,
  TODO: 1,
  HOLD: 2,
  DONE: 3,
}

const SESSION_MODE_OPTIONS = [
  { value: 'personal', label: '개인', icon: <IconUser aria-hidden="true" /> },
  { value: 'group', label: '그룹', icon: <IconUsers aria-hidden="true" />, disabled: true },
] as const

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
  const firstTaskRef = useRef<HTMLUListElement>(null)
  const customMinutesRef = useRef<HTMLInputElement>(null)
  const customBreakMinutesRef = useRef<HTMLInputElement>(null)
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
  const [breakPreset, setBreakPreset] = useState<BreakPreset>(5)
  const [customBreakMinutes, setCustomBreakMinutes] = useState('')
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

  const loadPlaces = useCallback(() => {
    void getPlaces()
      .then((response) => {
        const firstPlace = response.flatMap((city) => city.places)[0]
        setCities(response)
        setPlaceId((current) => current ?? firstPlace?.id ?? null)
        setPlacesStatus('ready')
      })
      .catch(() => {
        setPlacesStatus('error')
      })
  }, [])

  useEffect(() => {
    loadPlaces()
  }, [loadPlaces])

  const durationMinutes = durationPreset === 'custom' ? Number(customMinutes) : durationPreset
  const breakMinutes = breakPreset === 'custom' ? Number(customBreakMinutes) : breakPreset
  const validDuration = Number.isInteger(durationMinutes) && durationMinutes >= 1 && durationMinutes <= 1440
  const validBreak = Number.isInteger(breakMinutes) && breakMinutes >= 1 && breakMinutes <= 60
  const totalMinutes = validDuration ? durationMinutes * repeat + Math.max(0, repeat - 1) * breakMinutes : 0
  const validTotal = totalMinutes >= 1 && totalMinutes <= 1440
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
      firstTaskRef.current?.focus()
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
    if (!validDuration || !validTotal) {
      setSubmitError('집중 시간은 1분 이상 1,440분 이하로 입력해 주세요.')
      customMinutesRef.current?.focus()
      return
    }
    if (repeat > 1 && !validBreak) {
      setSubmitError('휴식 시간은 1분에서 60분 사이로 입력해 주세요.')
      customBreakMinutesRef.current?.focus()
      return
    }

    setIsSubmitting(true)
    try {
      onStarted(await startPersonalSession({
        taskIds: selectedTaskIds,
        placeId: selectedPlace.place.id,
        plannedDurationSec: durationMinutes * 60,
        focusDurationSec: durationMinutes * 60,
        breakDurationSec: repeat > 1 ? breakMinutes * 60 : 0,
        repeatCount: repeat,
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
              <legend>어떻게 할까요</legend>
              <ModeToggle
                ariaLabel="세션 모드"
                options={SESSION_MODE_OPTIONS}
                value={mode}
                disabled={isSubmitting}
                fullWidth
                onChange={setMode}
              />
              <p className={styles.hint}>
                {mode === 'group'
                  ? `공간과 타이머는 Room 설정을 따릅니다. ${GROUP_ROOM_MOCK.startsAtLabel} ${GROUP_ROOM_MOCK.city} · ${GROUP_ROOM_MOCK.durationMin}분 · ${GROUP_ROOM_MOCK.participantCount}명`
                  : ''}
              </p>
            </fieldset>

            <fieldset className={styles.fieldset}>
              <legend>무엇을 할까요</legend>
              <p className={styles.hint}>오늘 계획에서 함께 진행할 작업을 모두 선택해 주세요.</p>
              {linkedTasks.length === 0 ? (
                <p className={styles.empty}>오늘 계획에 포함된 할 일이 없습니다.</p>
              ) : (
                <ul className={styles['task-list']} ref={firstTaskRef} tabIndex={-1}>
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

            {mode === 'personal' && (
              <>
                <fieldset className={styles.fieldset}>
                  <legend>어디서 할까요 <span>(공간 선택)</span></legend>

                  {placesStatus === 'loading' && <p className={styles.empty} role="status">공간을 불러오는 중…</p>}
                  {placesStatus === 'error' && (
                    <div className={styles['load-error']} role="alert">
                      <p className={styles.empty}>공간을 불러오지 못했습니다.</p>
                      <button
                        type="button"
                        onClick={() => {
                          setPlacesStatus('loading')
                          loadPlaces()
                        }}
                        disabled={isSubmitting}
                      >
                        다시 시도
                      </button>
                    </div>
                  )}
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
                            ref={customMinutesRef}
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
                    {durationPreset === 'custom' && customMinutes.length > 0 && !validDuration && (
                      <p className={styles['field-error']} role="alert">1분에서 1,440분 사이로 입력해 주세요.</p>
                    )}
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
                    <legend>휴식 시간</legend>
                    <p className={styles['value-box']}>
                      {breakPreset === 'custom' ? (
                        <label className={styles['custom-value']}>
                          <span className="sr-only">휴식 시간</span>
                          <input ref={customBreakMinutesRef} type="number" min={1} max={60} value={customBreakMinutes} placeholder="—" onChange={(event) => setCustomBreakMinutes(event.target.value)} aria-invalid={Boolean(customBreakMinutes) && !validBreak} disabled={isSubmitting} />
                          <span aria-hidden="true">분</span>
                        </label>
                      ) : <><strong>{breakMinutes}</strong>분</>}
                    </p>
                    <div className={styles.durations}>
                      {([5, 10, 15] as const).map((minutes) => (
                        <label key={minutes}><input type="radio" name="break-duration" checked={breakPreset === minutes} onChange={() => setBreakPreset(minutes)} disabled={isSubmitting} /><span>{minutes}분</span></label>
                      ))}
                      <label><input type="radio" name="break-duration" checked={breakPreset === 'custom'} onChange={() => setBreakPreset('custom')} disabled={isSubmitting} /><span>직접 입력</span></label>
                    </div>
                  </fieldset>

                  <fieldset className={styles.fieldset}>
                    <legend>반복</legend>
                    <div className={styles.repeat}>
                      <button type="button" aria-label="반복 줄이기" onClick={() => setRepeat((value) => Math.max(1, value - 1))} disabled={isSubmitting || repeat === 1}>−</button>
                      <strong>{repeat}</strong>
                      <button type="button" aria-label="반복 늘리기" onClick={() => setRepeat((value) => Math.min(8, value + 1))} disabled={isSubmitting || repeat === 8}>+</button>
                    </div>
                    <p className={styles.hint}>
                      총 {formatTotalTime(totalMinutes)}
                      {repeat > 1 && ` · 사이에 ${breakMinutes}분 휴식`}
                    </p>
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
                  isSubmitting || mode === 'group'
              }
          >
            {mode === 'group' ? '준비 중' : '시작하기'}
          </ActionButton>
        </form>
      </section>
    </dialog>
  )
}
