import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ComponentType, PointerEvent as ReactPointerEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { IconArrowRight, IconPlayerPlay } from '@tabler/icons-react'
import type { IconProps } from '@tabler/icons-react'
import { findSection } from './navigationItems'
import type { Folder } from '../features/folders/folderTypes'
import { useActiveSessionStore } from '../store/activeSessionStore'
import { formatRemaining, useSessionClock } from '../features/sessions/sessionTimer'
import { useFolderStore } from '../store/folderStore'
import { useRecentPageStore } from '../store/recentPageStore'
import styles from './RecentPageButton.module.css'

/** 쿼리를 뗀 경로. 기록은 쿼리까지 남기지만 "어느 화면인가"는 경로가 정한다. */
function pathOf(href: string) {
  return href.split('?')[0]
}

/**
 * 버튼에 쓸 이름. label은 목적지, detail은 그 안에서 보던 화면이다.
 * 할 일의 매트릭스·리스트와 폴더의 할 일·링크는 같은 페이지의 다른 화면이라
 * 둘을 구분해 줘야 "이어서 보기"가 실제로 보던 곳으로 돌아간다.
 */
function describe(href: string, label: string, folders: Folder[]) {
  const path = pathOf(href)

  if (path === '/tasks') {
    return { label, detail: href.includes('view=list') ? '리스트' : '매트릭스' }
  }

  const folderId = Number(path.match(/^\/folders\/(\d+)(?:\/|$)/)?.[1])
  if (Number.isInteger(folderId)) {
    // 목록에 없으면(아직 못 불러왔거나 지워졌으면) 기능 이름으로 돌아간다.
    const name = folders.find((folder) => folder.id === folderId)?.name
    return { label: name ?? label, detail: path.endsWith('/links') ? '링크' : '할 일' }
  }

  return { label, detail: undefined }
}

/** 버튼이 가리킬 곳. 세션과 최근 페이지가 같은 모양으로 그려진다. */
interface Destination {
  href: string
  eyebrow: string
  label: string
  detail?: string
  icon: ComponentType<IconProps>
}

/**
 * 역할: 하던 일로 돌아가는 플로팅 버튼. 최근 페이지 기록도 여기서 한다.
 * 진행 중인 세션이 있으면 그쪽이 먼저다. 돌아갈 곳이 둘이면 사람은 고르지 않고 지나친다.
 * 화면 위에 떠 있어 어느 페이지에서나 보이므로, 지금 보고 있는 곳은 가리키지 않는다.
 *
 * 평소에는 아이콘만 있는 원이고, 다가가면 어디로 가는지가 펼쳐진다. 화면을 늘 가리고 있을
 * 이유가 없어서다. 펼쳐 보이는 계기는 셋이고 뜻이 같다: 마우스를 올리거나, 키보드
 * 초점이 닿거나, (마우스가 없는 기기에서) 한 번 누르거나.
 */
export default function RecentPageButton() {
  const { pathname, search } = useLocation()
  const recentHrefs = useRecentPageStore((state) => state.hrefs)
  const record = useRecentPageStore((state) => state.record)
  const folders = useFolderStore((state) => state.folders)
  const session = useActiveSessionStore((state) => state.session)
  const sessionStatus = useActiveSessionStore((state) => state.status)
  const loadActiveSession = useActiveSessionStore((state) => state.load)
  const now = useSessionClock(session?.id ?? null)

  const linkRef = useRef<HTMLAnchorElement>(null)
  const revealRef = useRef<HTMLSpanElement>(null)
  const contentRef = useRef<HTMLSpanElement>(null)
  /** 눌러서 펼친 상태. 마우스가 없는 기기에서 유일하게 펼치는 길이다. */
  const [isPinned, setIsPinned] = useState(false)
  const [isHovered, setIsHovered] = useState(false)
  const [isFocused, setIsFocused] = useState(false)
  const [openedAt, setOpenedAt] = useState(pathname)
  const isOpen = isPinned || isHovered || isFocused

  // 화면을 옮기면 가리킬 곳이 바뀐다. 펼친 채로 두면 이전 목적지를 펼쳐 놓은 꼴이 된다.
  // effect가 아니라 렌더 중에 맞춘다. effect로 미루면 한 프레임 동안 옛 목적지가 펼쳐진다.
  if (openedAt !== pathname) {
    setOpenedAt(pathname)
    setIsPinned(false)
  }

  useEffect(() => {
    // 이 버튼은 앱이 떠 있는 내내 살아 있다. 첫 한 번만 부르고, 이후 갱신은 세션을 다루는 화면이 맡는다.
    if (sessionStatus === 'idle') void loadActiveSession()
  }, [sessionStatus, loadActiveSession])

  useEffect(() => {
    // 같은 페이지의 다른 화면도 서로 다른 목적지라 쿼리까지 남긴다.
    if (findSection(pathname)) record(`${pathname}${search}`)
  }, [pathname, search, record])

  useEffect(() => {
    if (!isPinned) return

    const handlePointerDown = (event: PointerEvent) => {
      const node = event.target instanceof Node ? event.target : null
      if (!node || !linkRef.current?.contains(node)) setIsPinned(false)
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsPinned(false)
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isPinned])

  function buildDestination(): Destination | null {
    // 세션 화면은 셸 밖에서 그려지므로, 세션을 보는 중에 이 버튼이 자기 자신을 가리킬 일은 없다.
    if (session) {
      return {
        href: `/sessions/${session.id}`,
        eyebrow: '이어서 하기',
        label: session.tasks[0]?.title ?? '개인 집중 세션',
        detail: `${session.place.name} · ${formatRemaining(session, now)}`,
        icon: IconPlayerPlay,
      }
    }

    // 최근 순으로 훑어야 한다. 메뉴 배열 순서로 찾으면 더 예전에 본 쪽이 걸린다.
    // 지금 보고 있는 페이지는 화면만 다르더라도 가리키지 않는다.
    const recentHref = recentHrefs.find((href) => pathOf(href) !== pathname)
    const recent = findSection(pathOf(recentHref ?? ''))
    if (!recentHref || !recent) return null

    const { label, detail } = describe(recentHref, recent.label, folders)
    return { href: recentHref, eyebrow: '최근 보기', label, detail, icon: recent.icon }
  }

  const destination = buildDestination()

  // 펼쳤을 때의 폭을 재서 CSS에 넘긴다. width는 auto로 전환할 수 없고, max-width로
  // 흉내 내면 실제 글 폭보다 큰 값까지 이징이 이어져 다 자란 뒤에도 뜸을 들인다.
  // state가 아니라 DOM에 직접 쓴다. 폭은 그릴 내용이 아니라 잰 값이다.
  useLayoutEffect(() => {
    const reveal = revealRef.current
    const content = contentRef.current
    if (!reveal || !content) return
    // offsetWidth는 정수로 내림해서 글 폭이 소수점이면 한 픽셀이 모자란다. 그 한 픽셀에
    // .label의 ellipsis가 걸려 멀쩡한 이름이 잘린 것처럼 보인다. 잰 값을 올림해서 쓴다.
    reveal.style.setProperty('--reveal-width', `${Math.ceil(content.getBoundingClientRect().width)}px`)
  }, [destination?.label, destination?.detail, destination?.eyebrow])

  if (!destination) return null

  const { href, eyebrow, label, detail, icon: Icon } = destination

  // 손가락으로는 hover가 없다. 마우스일 때만 다가감으로 친다. 그러지 않으면 첫 탭이
  // 펼치기가 아니라 이동이 되어, 어디로 가는지 보기도 전에 떠나게 된다.
  const handlePointerEnter = (event: ReactPointerEvent<HTMLAnchorElement>) => {
    if (event.pointerType === 'mouse') setIsHovered(true)
  }

  return (
    <Link
      ref={linkRef}
      className={styles['recent-page']}
      to={href}
      data-live={session ? 'true' : undefined}
      data-open={isOpen ? 'true' : undefined}
      aria-expanded={isOpen}
      aria-label={`${eyebrow}: ${label}${detail ? ` ${detail}` : ''}`}
      onPointerEnter={handlePointerEnter}
      onPointerLeave={() => setIsHovered(false)}
      onFocus={() => setIsFocused(true)}
      onBlur={() => setIsFocused(false)}
      onClick={(event) => {
        // 접혀 있으면 첫 누름은 펼치기다. 어디로 가는지 보여 주고 나서 보낸다.
        if (isOpen) return
        event.preventDefault()
        setIsPinned(true)
      }}
    >
      <span className={styles.mark} aria-hidden="true">
        <Icon size={20} stroke={1.8} />
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
