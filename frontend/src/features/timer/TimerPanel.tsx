import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { IconHourglass, IconPlayerPlay } from '@tabler/icons-react'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import ModeToggle from '../../components/ModeToggle'
import { TIMER_MAX_MINUTES, TIMER_MIN_MINUTES, isValidTimerMinutes } from '../../store/timerStore'
import styles from './TimerWidget.module.css'

interface TimerPanelProps {
  id: string
  /** 독립 타이머가 이미 돌고 있으면 새로 시작하는 입력을 감춘다. 끝내기는 바에 있다. */
  isTimerRunning: boolean
  sessionDialogId: string
  isSessionModalOpen: boolean
  isPreparingSession: boolean
  sessionError: string | null
  onStartTimer: (minutes: number) => void
  onStartSession: () => void
}

type PanelMode = 'timer' | 'session'

/**
 * 역할: 타이머 바 위로 펼쳐지는 패널. 음악 위젯의 목록 패널과 같은 자리·같은 모양이다.
 * 세션과 무관한 타이머와 세션 중 하나를 고른다. 둘은 동시에 시작하는 것이 아니라 택일이라
 * 목록으로 늘어놓지 않고 탭으로 나눠, 고른 쪽의 시작만 보이게 한다.
 */
export default function TimerPanel({
  id,
  isTimerRunning,
  sessionDialogId,
  isSessionModalOpen,
  isPreparingSession,
  sessionError,
  onStartTimer,
  onStartSession,
}: TimerPanelProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [minutes, setMinutes] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [mode, setMode] = useState<PanelMode>('timer')

  // 펼치자마자 바로 분을 적을 수 있게 한다. 탭을 오갈 때는 초점을 탭에 남긴다.
  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const parsed = Number(minutes)
    if (!isValidTimerMinutes(parsed)) {
      setMessage(`${TIMER_MIN_MINUTES}분에서 ${TIMER_MAX_MINUTES}분 사이로 입력해 주세요.`)
      inputRef.current?.focus()
      return
    }
    onStartTimer(parsed)
    setMinutes('')
  }

  return (
    <div className={styles.panel} id={id}>
      <ModeToggle<PanelMode>
        ariaLabel="시작할 것"
        semantics="tabs"
        fullWidth
        value={mode}
        onChange={setMode}
        options={[
          {
            value: 'timer',
            label: '타이머',
            icon: <IconHourglass stroke={1.8} />,
            id: `${id}-timer-tab`,
            controls: `${id}-timer`,
          },
          {
            value: 'session',
            label: '세션',
            icon: <IconPlayerPlay stroke={1.8} />,
            id: `${id}-session-tab`,
            controls: `${id}-session`,
          },
        ]}
      />

      {mode === 'timer' ? (
        <div className={styles.tab} id={`${id}-timer`} role="tabpanel" aria-labelledby={`${id}-timer-tab`}>
          {isTimerRunning ? (
            <p className={styles.empty}>타이머가 흐르고 있어요. 끝내면 새로 시작할 수 있어요.</p>
          ) : (
            <form className={styles.source} onSubmit={submit} noValidate>
              <label>
                <span className={styles['visually-hidden']}>타이머 길이(분)</span>
                <input
                  ref={inputRef}
                  type="number"
                  inputMode="numeric"
                  min={TIMER_MIN_MINUTES}
                  max={TIMER_MAX_MINUTES}
                  step={1}
                  value={minutes}
                  placeholder="몇 분 동안 잴까요"
                  aria-invalid={message !== null}
                  onChange={(event) => {
                    setMinutes(event.target.value)
                    setMessage(null)
                  }}
                />
              </label>
              <button type="submit" disabled={!minutes.trim()}>시작</button>
            </form>
          )}
          {message && <p className={styles.message} role="alert">{message}</p>}
        </div>
      ) : (
        <div className={styles.tab} id={`${id}-session`} role="tabpanel" aria-labelledby={`${id}-session-tab`}>
          <ModalTriggerButton
            className={styles['panel-action']}
            dialogId={sessionDialogId}
            isOpen={isSessionModalOpen}
            isPreparing={isPreparingSession}
            preparingLabel="준비하는 중…"
            onClick={onStartSession}
          >
            세션 시작하기
          </ModalTriggerButton>
          {sessionError && <p className={styles.message} role="alert">{sessionError}</p>}
        </div>
      )}
    </div>
  )
}
