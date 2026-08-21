import { useState } from 'react'
import { IconLink, IconLoader2, IconMusic } from '@tabler/icons-react'
import ReactPlayer from 'react-player'
import InlineEditableText from '../../../components/InlineEditableText'
import styles from './SessionMusicPlayer.module.css'

interface SessionMusicPlayerProps {
  source: string | null
  onSourceChange: (source: string | null) => void
  className?: string
}

function isYouTubeSource(value: string) {
  try {
    const hostname = new URL(value).hostname.toLowerCase().replace(/^www\./, '')
    return hostname === 'youtu.be'
      || hostname === 'youtube.com'
      || hostname.endsWith('.youtube.com')
      || hostname === 'youtube-nocookie.com'
      || hostname.endsWith('.youtube-nocookie.com')
  } catch {
    return false
  }
}

export default function SessionMusicPlayer({
  source,
  onSourceChange,
  className,
}: SessionMusicPlayerProps) {
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>(
    source ? 'loading' : 'idle',
  )
  const [message, setMessage] = useState<string | null>(null)

  const saveSource = async (nextSource: string) => {
    if (!isYouTubeSource(nextSource) || !ReactPlayer.canPlay?.(nextSource)) {
      throw new Error('INVALID_YOUTUBE_SOURCE')
    }
    setMessage(null)
    setStatus('loading')
    onSourceChange(nextSource)
  }

  return (
    <section className={`${styles.player} ${className ?? ''}`} aria-labelledby="session-music-title">
      <header className={styles.header}>
        <IconMusic aria-hidden="true" />
        <div>
          <strong id="session-music-title">YouTube 음악</strong>
          <span>현재 세션에서만 재생됩니다.</span>
        </div>
      </header>

      <div className={styles.source}>
        <IconLink aria-hidden="true" />
        <InlineEditableText
          className={styles['inline-source']}
          errorClassName={styles['inline-error']}
          value={source ?? ''}
          emptyText="YouTube 주소 입력"
          ariaLabel="YouTube 주소"
          requiredMessage="YouTube 주소를 입력해 주세요."
          onSave={saveSource}
          getErrorMessage={() => '재생할 수 있는 YouTube 주소를 입력해 주세요.'}
        />
      </div>

      {message && <p className={styles.message} id="session-music-message" role="alert">{message}</p>}

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
            onReady={() => setStatus('ready')}
            onError={() => {
              setStatus('error')
              setMessage('이 YouTube 콘텐츠를 재생할 수 없습니다. 공개 상태를 확인해 주세요.')
            }}
          />
        </div>
      ) : (
        <p className={styles.empty}>YouTube 영상이나 재생목록 주소를 입력해 주세요.</p>
      )}
    </section>
  )
}
