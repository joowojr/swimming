import { useEffect, useLayoutEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { IconArrowRight, IconClock } from '@tabler/icons-react'
import { useRevealOnApproach } from '../../lib/useRevealOnApproach'
import { useActiveSessionStore } from '../../store/activeSessionStore'
import { formatCountdown, useCountdownClock } from './timerClock'
import styles from './TimerWidget.module.css'

/** 초까지 보여주므로 매초 다시 그린다. */
const TICK_MS = 1000

/**
 * 역할: 지금 재고 있는 것의 남은 시간을 어느 화면에서나 보여주는 플로팅 타이머.
 *
 * 접혀 있어도 시간은 숨기지 않는다. 흘깃 보는 것이 타이머의 존재 이유라, 원 안에 남은
 * 시간을 두고 무엇을 하던 중인지는 다가갔을 때 내준다.
 *
 * 잴 것이 없어도 자리를 지킨다. 타이머는 있다가 없어지는 것이 아니라 늘 거기 있고 지금
 * 비어 있을 뿐이다. 빌 때는 시계를 보여주고 시작할 수 있는 곳으로 보낸다.
 *
 * 지금 재는 것은 진행 중인 세션 하나뿐이다. 할 일 타이머가 생기면 재는 대상이 여기서
 * 늘어나고, 빈 상태의 "세션 시작하기"도 그때 "타이머 시작"이 된다.
 * 세는 규칙(timerClock)은 대상을 가리지 않으므로 그때도 그대로 쓴다.
 *
 * 진행 중인 세션을 처음 불러오는 자리이기도 하다. 이 위젯은 셸이 떠 있는 내내 살아 있어
 * 어느 화면에서 들어오든 한 번은 지나간다. 이후 갱신은 세션을 다루는 화면이 맡는다.
 *
 * 세션 화면(`/sessions/:id`)은 셸 밖에서 그려지므로, 세션을 보는 중에 이 타이머가
 * 자기 자신을 가리킬 일은 없다.
 */
export default function TimerWidget() {
  const session = useActiveSessionStore((state) => state.session)
  const status = useActiveSessionStore((state) => state.status)
  const loadActiveSession = useActiveSessionStore((state) => state.load)
  const now = useCountdownClock(session?.id ?? null, TICK_MS)

  const { isOpen, ref, approachProps, pin } = useRevealOnApproach<HTMLAnchorElement>()
  const revealRef = useRef<HTMLSpanElement>(null)
  const contentRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (status === 'idle') void loadActiveSession()
  }, [status, loadActiveSession])

  const countdown = session
    ? formatCountdown(session.startedAt, session.plannedDurationSec, now)
    : null
  const isOvertime = countdown?.startsWith('+') ?? false

  // 빈 상태에서는 세션을 시작할 수 있는 곳으로 보낸다. 시작 카드가 핀보드에 있다.
  const href = session ? `/sessions/${session.id}` : '/pinboard'
  const eyebrow = !session ? '타이머' : isOvertime ? '진행 중' : '진행 중'
  const label = session ? session.tasks[0]?.title ?? '개인 집중 세션' : '세션 시작하기'
  const detail = session?.place.name
  const state = !session ? 'idle' : isOvertime ? 'overtime' : 'running'

  // 펼쳤을 때의 폭을 재서 CSS에 넘긴다. width는 auto로 전환할 수 없고, max-width로
  // 흉내 내면 실제 글 폭보다 큰 값까지 이징이 이어져 다 자란 뒤에도 뜸을 들인다.
  useLayoutEffect(() => {
    const reveal = revealRef.current
    const content = contentRef.current
    if (!reveal || !content) return
    // offsetWidth는 정수로 내림해서 글 폭이 소수점이면 한 픽셀이 모자란다. 그 한 픽셀에
    // ellipsis가 걸려 멀쩡한 이름이 잘린 것처럼 보인다. 잰 값을 올림해서 쓴다.
    reveal.style.setProperty('--reveal-width', `${Math.ceil(content.getBoundingClientRect().width)}px`)
  }, [eyebrow, label, detail])

  return (
    <Link
      ref={ref}
      className={styles.timer}
      to={href}
      data-open={isOpen ? 'true' : undefined}
      data-state={state}
      aria-expanded={isOpen}
      aria-label={session
        ? `${label} 세션 ${isOvertime ? '추가 진행' : '남은 시간'} ${countdown}, 이어서 하기`
        : '타이머: 진행 중인 세션이 없습니다. 세션 시작하기'}
      {...approachProps}
      onClick={(event) => {
        // 접혀 있으면 첫 누름은 펼치기다. 어디로 가는지 보여 주고 나서 보낸다.
        if (isOpen) return
        event.preventDefault()
        pin()
      }}
    >
      <span className={styles.dial} aria-hidden="true">
        {countdown ?? <IconClock size={20} stroke={1.8} />}
      </span>
      <span className={styles.reveal} ref={revealRef}>
        <span className={styles['reveal-content']} ref={contentRef}>
          <span className={styles.copy}>
            <span className={styles.eyebrow}>{eyebrow}</span>
            <span className={styles.line}>
              <strong className={styles.label}>{label}</strong>
              {detail && <span className={styles.detail}>{detail}</span>}
            </span>
          </span>
          <IconArrowRight className={styles.arrow} size={20} stroke={1.8} aria-hidden="true" />
        </span>
      </span>
    </Link>
  )
}
