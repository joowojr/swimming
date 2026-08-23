import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconLink, IconLoader2, IconMusic, IconTrash } from '@tabler/icons-react'
import ReactPlayer from 'react-player'
import styles from './SessionMusicPlayer.module.css'

export interface SessionMusicOption {
  id: number
  label: string
  url: string
}

interface SessionMusicPlayerProps {
  source: string | null
  options: SessionMusicOption[]
  onSourceChange: (source: string | null) => Promise<void>
  className?: string
}

function isYouTubeSource(value: string) {
  try {
    const url = new URL(value)
    const schemeAllowed = url.protocol === 'http:' || url.protocol === 'https:'
    const hostname = url.hostname.toLowerCase()
    if (!schemeAllowed) return false
    if (hostname === 'youtu.be' || hostname.endsWith('.youtu.be')) {
      return url.pathname.length > 1
    }

    const isYouTube = hostname === 'youtube.com' || hostname.endsWith('.youtube.com')
    const isYouTubeNoCookie = hostname === 'youtube-nocookie.com'
      || hostname.endsWith('.youtube-nocookie.com')
    if (!isYouTube && !isYouTubeNoCookie) return false
    if (url.pathname.startsWith('/embed/') || url.pathname.startsWith('/shorts/')) {
      return url.pathname.split('/').filter(Boolean).length > 1
    }
    if (!isYouTube) return false
    return (url.pathname === '/watch' && Boolean(url.searchParams.get('v')))
      || (url.pathname === '/playlist' && Boolean(url.searchParams.get('list')))
  } catch {
    return false
  }
}

export default function SessionMusicPlayer({
  source,
  options,
  onSourceChange,
  className,
}: SessionMusicPlayerProps) {
  const [draft, setDraft] = useState(source ?? '')
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>(
    source ? 'loading' : 'idle',
  )
  const [message, setMessage] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const markAsReady = () => setStatus('ready')

  const saveSource = async (nextSource: string | null) => {
    const normalizedSource = nextSource?.trim() || null
    if (normalizedSource && (
      normalizedSource.length > 2048
      || !isYouTubeSource(normalizedSource)
      || !ReactPlayer.canPlay?.(normalizedSource)
    )) {
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

  return (
    <section className={`${styles.player} ${className ?? ''}`} aria-labelledby="session-music-title">
      <header className={styles.header}>
        <IconMusic aria-hidden="true" />
        <div>
          <strong id="session-music-title">YouTube 음악</strong>
          <span>이 세션에 저장되며 재생은 직접 시작합니다.</span>
        </div>
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
            aria-label="저장된 음악 주소 삭제"
            disabled={isSaving}
            onClick={() => void saveSource(null)}
          >
            <IconTrash aria-hidden="true" />
          </button>
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
            onPlay={markAsReady}
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
