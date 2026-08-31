import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import {
  IconCheck,
  IconChevronDown,
  IconChevronUp,
  IconCopy,
  IconHistory,
  IconLink,
  IconLoader2,
  IconMusic,
  IconTrash,
} from '@tabler/icons-react'
import ReactPlayer from 'react-player'
import {
  clearRecentMusicHistory,
  normalizeYouTubeUrl,
  readRecentMusicHistory,
  rememberRecentMusic,
} from './recentMusicHistory'
import styles from './SessionMusicPlayer.module.css'

export interface SessionMusicOption {
  id: number
  label: string
  url: string
}

interface SessionMusicPlayerProps {
  source: string | null
  options: SessionMusicOption[]
  historyOwnerId: number | null
  onSourceChange: (source: string | null) => Promise<void>
  className?: string
}

const playedAtFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

export default function SessionMusicPlayer({
  source,
  options,
  historyOwnerId,
  onSourceChange,
  className,
}: SessionMusicPlayerProps) {
  const recentListId = useId()
  const [draft, setDraft] = useState(source ?? '')
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>(
    source ? 'loading' : 'idle',
  )
  const [message, setMessage] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [recentHistory, setRecentHistory] = useState(() => readRecentMusicHistory(historyOwnerId))
  const [isRecentExpanded, setIsRecentExpanded] = useState(false)
  const [copiedUrl, setCopiedUrl] = useState<string | null>(null)

  const markAsReady = () => setStatus('ready')

  const saveSource = async (nextSource: string | null) => {
    const draftSource = nextSource?.trim() || null
    const normalizedSource = draftSource ? normalizeYouTubeUrl(draftSource) : null
    if (normalizedSource && (
      normalizedSource.length > 2048
      || !ReactPlayer.canPlay?.(normalizedSource)
    ) || (draftSource && !normalizedSource)) {
      setMessage('재생할 수 있는 YouTube 영상 또는 재생목록 주소를 입력해 주세요.')
      return
    }
    if (normalizedSource === source) {
      setDraft(source ?? '')
      setMessage(null)
      return
    }

    setIsSaving(true)
    setMessage(null)
    try {
      await onSourceChange(normalizedSource)
    } catch {
      setMessage('음악 설정을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsSaving(false)
    }
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void saveSource(draft)
  }

  const copyRecentUrl = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url)
      setCopiedUrl(url)
      setMessage(null)
    } catch {
      setMessage('최근 재생 주소를 복사하지 못했습니다.')
    }
  }

  return (
    <section className={`${styles.player} ${className ?? ''}`} aria-labelledby="session-music-title">
      <header className={styles.header}>
        <IconMusic aria-hidden="true" />
        <div>
          <strong id="session-music-title">YouTube 음악</strong>
          <span>세션에서 재생할 음악을 설정해보세요.</span>
        </div>
        {recentHistory.length > 0 && (
          <button
            type="button"
            className={styles['recent-toggle']}
            aria-label={isRecentExpanded ? '최근 재생 목록 접기' : '최근 재생 목록 펼치기'}
            aria-expanded={isRecentExpanded}
            aria-controls={recentListId}
            disabled={isSaving}
            onClick={() => {
              setIsRecentExpanded((expanded) => !expanded)
              setCopiedUrl(null)
            }}
          >
            <IconHistory aria-hidden="true" />
            {isRecentExpanded
              ? <IconChevronUp aria-hidden="true" />
              : <IconChevronDown aria-hidden="true" />}
          </button>
        )}
      </header>

      {options.length > 0 && (
        <div className={styles.options} aria-label="제공 음악">
          {options.map((option) => (
            <button
              type="button"
              key={option.id}
              aria-pressed={source === option.url}
              disabled={isSaving}
              onClick={() => void saveSource(option.url)}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}

      <form className={styles.source} onSubmit={submit}>
        <div className={styles['source-row']}>
          <IconLink aria-hidden="true" />
          <label>
            <span className={styles['visually-hidden']}>YouTube 주소</span>
            <input
              type="url"
              value={draft}
              maxLength={2048}
              placeholder="YouTube 주소 입력"
              disabled={isSaving}
              onChange={(event) => {
                setDraft(event.target.value)
                setMessage(null)
              }}
            />
          </label>
          <button type="submit" disabled={isSaving || draft.trim() === (source ?? '')}>
            {isSaving ? <IconLoader2 aria-hidden="true" /> : '저장'}
          </button>
          {source && (
            <button
              type="button"
              className={styles.remove}
              aria-label="현재 세션의 저장된 음악 주소 삭제"
              disabled={isSaving}
              onClick={() => void saveSource(null)}
            >
              <IconTrash aria-hidden="true" />
            </button>
          )}
        </div>
        {isRecentExpanded && recentHistory.length > 0 && (
          <section className={styles.recent} id={recentListId} aria-labelledby={`${recentListId}-title`}>
            <header>
              <strong id={`${recentListId}-title`}>최근 재생</strong>
              <button
                type="button"
                disabled={isSaving}
                onClick={() => {
                  clearRecentMusicHistory(historyOwnerId)
                  setRecentHistory([])
                  setIsRecentExpanded(false)
                }}
              >
                기록 삭제
              </button>
            </header>
            <ul>
              {recentHistory.map((entry) => (
                <li key={entry.url}>
                  <button
                    type="button"
                    className={styles['recent-source']}
                    title={entry.url}
                    aria-pressed={normalizeYouTubeUrl(source ?? '') === entry.url}
                    disabled={isSaving}
                    onClick={() => {
                      setIsRecentExpanded(false)
                      void saveSource(entry.url)
                    }}
                  >
                    <span>{entry.url}</span>
                    <time dateTime={entry.playedAt}>{playedAtFormatter.format(new Date(entry.playedAt))}</time>
                  </button>
                  <button
                    type="button"
                    className={styles['recent-copy']}
                    aria-label={`${entry.url} 복사`}
                    onClick={() => void copyRecentUrl(entry.url)}
                  >
                    {copiedUrl === entry.url
                      ? <IconCheck aria-hidden="true" />
                      : <IconCopy aria-hidden="true" />}
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}
      </form>

      {message && <p className={styles.message} role="alert">{message}</p>}

      {source ? (
        <div className={styles.frame} data-status={status}>
          {status === 'loading' && (
            <span className={styles.loading} role="status">
              <IconLoader2 aria-hidden="true" /> 플레이어를 불러오는 중…
            </span>
          )}
          <ReactPlayer
            src={source}
            controls
            playsInline
            width="100%"
            height="100%"
            onReady={markAsReady}
            onCanPlay={markAsReady}
            onLoadedMetadata={markAsReady}
            onPlay={() => {
              markAsReady()
              setRecentHistory(rememberRecentMusic(historyOwnerId, source))
            }}
            onError={() => {
              setStatus('error')
              setMessage('이 YouTube 콘텐츠를 재생할 수 없습니다. 공개 상태를 확인해 주세요.')
            }}
          />
        </div>
      ) : (
        <p className={styles.empty}>제공 음악을 선택하거나 YouTube 주소를 입력해 주세요.</p>
      )}
    </section>
  )
}
