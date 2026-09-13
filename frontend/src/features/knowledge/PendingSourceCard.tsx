import { useEffect, useState } from 'react'
import styles from './PendingSourceCard.module.css'

interface PendingSourceCardProps {
  url: string
}

const LOADING_MESSAGE_INTERVAL_MS = 5000
const LOADING_MESSAGES = [
  '링크를 가져오고 있어요',
  '본문을 읽고 있어요',
  '주제와 키워드를 뽑고 있어요',
  '링크의 요약을 만들고 있어요',
] as const

function domainOf(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

/** 저장 중인 링크가 들어올 자리를 목록 맨 위에 미리 잡아 둔다. */
export default function PendingSourceCard({ url }: PendingSourceCardProps) {
  const [messageIndex, setMessageIndex] = useState(0)

  // 저장이 끝날 때까지 진행 문구를 한 단계씩 넘기고, 마지막 문구에서 멈춘다.
  useEffect(() => {
    const timer = window.setInterval(() => {
      setMessageIndex((index) => Math.min(index + 1, LOADING_MESSAGES.length - 1))
    }, LOADING_MESSAGE_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [])

  return (
    <article className={styles.card} aria-busy="true">
      <div className={styles.meta}>
        <span className={styles.domain}>{domainOf(url)}</span>
      </div>
      <p className={styles.message} key={messageIndex} role="status" aria-live="polite">
        {LOADING_MESSAGES[messageIndex]}
      </p>
    </article>
  )
}
