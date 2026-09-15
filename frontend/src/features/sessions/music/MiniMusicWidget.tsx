import { useEffect, useId, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import {
  IconChevronDown,
  IconChevronUp,
  IconLink,
  IconMusic,
  IconPlayerPauseFilled,
  IconPlayerPlayFilled,
} from '@tabler/icons-react'
import ReactPlayer from 'react-player'
import { useAuthStore } from '../../../store/authStore'
import { usePlaceStore } from '../../../store/placeStore'
import { normalizeYouTubeUrl, readRecentMusicHistory, rememberRecentMusic } from './recentMusicHistory'
import { fetchYouTubeMeta } from './youtubeOembed'
import styles from './MiniMusicWidget.module.css'

interface Track {
  url: string
  label: string
}

/** 최근 기록은 주소만 남아 이름이 없다. 제공 음악과 같은 주소면 그 이름을, 아니면 영상 id를 쓴다. */
function labelOf(url: string, provided: Track[]) {
  const match = provided.find((track) => track.url === url)
  if (match) return match.label

  try {
    const params = new URL(url).searchParams
    const videoId = params.get('v')
    return videoId ? `영상 ${videoId}` : `재생목록 ${params.get('list') ?? ''}`.trim()
  } catch {
    return url
  }
}

/**
 * 역할: 화면 왼쪽 아래에 떠 있는 작은 음악 재생기.
 *
 * 세션 화면(`/sessions/:id`)은 AppShell 바깥 분기라 이 위젯이 없다. 세션에서 나오면
 * 그쪽 재생은 끊기고, 여기서 다시 고르면 처음부터 재생된다.
 */
export default function MiniMusicWidget() {
  const panelId = useId()
  const userId = useAuthStore().user?.id ?? null
  const cities = usePlaceStore((state) => state.cities)
  const loadPlaces = usePlaceStore((state) => state.load)

  const [isExpanded, setIsExpanded] = useState(false)
  const [track, setTrack] = useState<Track | null>(null)
  const [isPlaying, setIsPlaying] = useState(false)
  const [recent, setRecent] = useState(() => readRecentMusicHistory(userId))
  const [draft, setDraft] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => { void loadPlaces() }, [loadPlaces])

  // 세션 화면과 같은 목록이다. 장소마다 정해둔 음악이 곧 "제공 음악"이다.
  const provided = useMemo<Track[]>(() => {
    const seen = new Set<string>()
    return cities.flatMap((city) => city.places.flatMap((place) => {
      const url = place.defaultMusicUrl && normalizeYouTubeUrl(place.defaultMusicUrl)
      if (!url || seen.has(url)) return []
      seen.add(url)
      return [{ url, label: `${city.name} · ${place.name}` }]
    }))
  }, [cities])

  const recentTracks = useMemo<Track[]>(
    () => recent.map((entry) => ({
      url: entry.url,
      label: entry.title ?? labelOf(entry.url, provided),
    })),
    [recent, provided],
  )

  const isEmpty = recentTracks.length === 0 && provided.length === 0

  const pick = (next: Track) => {
    setMessage(null)
    setTrack(next)
    setIsPlaying(true)
  }

  /**
   * 재생이 시작되면 이름을 한 번 받아 기록에 캐시한다. 기록은 최대 5개라 호출은 드물다.
   * 못 받으면 아무 일도 하지 않고, 주소만으로 만든 이름이 그대로 남는다.
   */
  const nameTrack = async (url: string) => {
    const meta = await fetchYouTubeMeta(url)
    if (!meta) return

    setRecent(rememberRecentMusic(userId, url, new Date(), meta))
    setTrack((current) => (current?.url === url ? { ...current, label: meta.title } : current))
  }

  /** 세션 플레이어와 같은 규칙으로 거른다. 재생할 수 없는 주소는 담지 않는다. */
  const submitSource = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const url = normalizeYouTubeUrl(draft)
    if (!url || url.length > 2048 || !ReactPlayer.canPlay?.(url)) {
      setMessage('재생할 수 있는 YouTube 영상 또는 재생목록 주소를 입력해 주세요.')
      return
    }

    pick({ url, label: labelOf(url, provided) })
    setDraft('')
  }

  return (
    <section className={styles.widget} data-expanded={isExpanded} aria-label="음악">
      {isExpanded && (
        <div className={styles.panel} id={panelId}>
          <form className={styles.source} onSubmit={submitSource}>
            <IconLink className={styles.mark} size={20} stroke={1.8} aria-hidden="true" />
            <label>
              <span className={styles['visually-hidden']}>YouTube 주소</span>
              <input
                type="url"
                value={draft}
                maxLength={2048}
                placeholder="YouTube 주소 입력"
                onChange={(event) => {
                  setDraft(event.target.value)
                  setMessage(null)
                }}
              />
            </label>
            <button type="submit" disabled={!draft.trim()}>재생</button>
          </form>

          {message && <p className={styles.message} role="alert">{message}</p>}

          {recentTracks.length > 0 && (
            <div className={styles.group}>
              <p className={styles['group-label']}>최근 재생</p>
              <ul>
                {recentTracks.map((entry) => (
                  <li key={entry.url}>
                    <button type="button" aria-pressed={track?.url === entry.url} onClick={() => pick(entry)}>
                      {entry.label}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {provided.length > 0 && (
            <div className={styles.group}>
              <p className={styles['group-label']}>제공 음악</p>
              <ul>
                {provided.map((entry) => (
                  <li key={entry.url}>
                    <button type="button" aria-pressed={track?.url === entry.url} onClick={() => pick(entry)}>
                      {entry.label}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {isEmpty && (
            <p className={styles.empty}>
              세션에서 음악을 재생하면 여기에 모여요.
            </p>
          )}
        </div>
      )}

      {/*
        접어도 재생이 이어지도록 플레이어는 항상 붙여 둔다. 접힌 동안에는 감싼 칸의 높이를
        0으로 줄여 감춘다. display:none으로 지우면 재생이 멈춘다.
      */}
      {track && (
        <div className={styles.stage} data-expanded={isExpanded}>
          <ReactPlayer
            src={track.url}
            playing={isPlaying}
            controls
            playsInline
            width="100%"
            height="100%"
            onPlay={() => {
              setIsPlaying(true)
              setRecent(rememberRecentMusic(userId, track.url))
              void nameTrack(track.url)
            }}
            onPause={() => setIsPlaying(false)}
          />
        </div>
      )}

      <div className={styles.bar}>
        <IconMusic className={styles.mark} size={20} stroke={1.8} aria-hidden="true" />
        <span className={styles.title}>{track?.label ?? (isEmpty ? '음악 없음' : '음악 고르기')}</span>
        {track && (
          <button
            type="button"
            className={`${styles.control} ${styles['control-play']}`}
            aria-label={isPlaying ? '일시정지' : '재생'}
            onClick={() => setIsPlaying((playing) => !playing)}
          >
            {isPlaying
              ? <IconPlayerPauseFilled size={16} aria-hidden="true" />
              : <IconPlayerPlayFilled size={16} aria-hidden="true" />}
          </button>
        )}
        <button
          type="button"
          className={styles.control}
          aria-expanded={isExpanded}
          aria-controls={panelId}
          aria-label={isExpanded ? '음악 목록 접기' : '음악 목록 펼치기'}
          onClick={() => {
            // 펼칠 때 기록을 다시 읽는다. 세션 화면에서 듣고 온 곡이 바로 목록에 오른다.
            if (!isExpanded) setRecent(readRecentMusicHistory(userId))
            setIsExpanded((expanded) => !expanded)
          }}
        >
          {isExpanded
            ? <IconChevronDown size={16} aria-hidden="true" />
            : <IconChevronUp size={16} aria-hidden="true" />}
        </button>
      </div>
    </section>
  )
}
