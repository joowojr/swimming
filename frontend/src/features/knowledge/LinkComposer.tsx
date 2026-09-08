import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconBookmarkPlus, IconLink, IconLoader2 } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import { collectSources } from './knowledgeApi'
import { sourceFailureMessage } from './knowledgeLabels'
import type { SourceCard } from './knowledgeTypes'
import styles from './LinkComposer.module.css'

interface LinkComposerProps {
  folderId: number
  onSaved: (source: SourceCard) => void
}

type Notice = { tone: 'info' | 'error'; text: string }

function validateUrl(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return '저장할 링크를 입력해 주세요.'
  if (trimmed.length > 2048) return '링크는 2048자 이하로 입력해 주세요.'
  if (!/^https?:\/\//i.test(trimmed)) return 'http 또는 https로 시작하는 링크를 입력해 주세요.'
  return undefined
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null
}

/**
 * 링크 하나를 저장한다. 수집과 소화를 나누어 부르지 않으므로 호출은 한 번이다.
 * 지금은 서버가 소화까지 마치고 응답해 시간이 걸리지만, 비동기로 바뀌어도 이 화면은 그대로다.
 */
export default function LinkComposer({ folderId, onSaved }: LinkComposerProps) {
  const [url, setUrl] = useState('')
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [notice, setNotice] = useState<Notice | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const validationError = validateUrl(url)
    setFieldError(validationError)
    setNotice(null)
    if (validationError) return

    setIsSubmitting(true)
    try {
      const [item] = (await collectSources(folderId, [url.trim()])).items
      if (!item) return

      if (item.result === 'FAILED') {
        setNotice({
          tone: 'error',
          text: sourceFailureMessage(item.failureMessage, '링크를 가져오지 못했어요.'),
        })
        return
      }

      if (item.source) onSaved(item.source)
      setUrl('')
      setNotice(
        item.result === 'ALREADY_SAVED'
          ? { tone: 'info', text: '이미 이 폴더에 저장된 링크예요.' }
          : { tone: 'info', text: '링크를 저장했어요.' },
      )
    } catch (error: unknown) {
      setNotice({
        tone: 'error',
        text: isApiError(error) && error.message
          ? error.message
          : '링크를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form
      className={styles.composer}
      aria-label="링크 저장"
      aria-busy={isSubmitting}
      onSubmit={(event) => void handleSubmit(event)}
      noValidate
    >
      <div className={styles.row}>
        <label className="sr-only" htmlFor="knowledge-url">저장할 링크</label>
        <span className={styles.field}>
          <IconLink size={16} stroke={1.8} aria-hidden="true" />
          <input
            id="knowledge-url"
            type="url"
            value={url}
            maxLength={2048}
            placeholder="저장할 링크를 붙여 넣어 주세요."
            aria-invalid={Boolean(fieldError)}
            aria-describedby={fieldError || notice ? 'knowledge-url-message' : undefined}
            disabled={isSubmitting}
            onChange={(event) => {
              setUrl(event.target.value)
              setFieldError(undefined)
              setNotice(null)
            }}
          />
        </span>
        <button type="submit" className={styles.submit} disabled={isSubmitting}>
          {isSubmitting
            ? <IconLoader2 className={styles.spinner} size={14} stroke={1.8} aria-hidden="true" />
            : <IconBookmarkPlus size={14} stroke={1.8} aria-hidden="true" />}
          {isSubmitting ? '저장 중' : '저장'}
        </button>
      </div>
      {(fieldError || notice) && (
        <p
          className={styles.message}
          id="knowledge-url-message"
          data-tone={fieldError ? 'error' : notice?.tone}
          role={fieldError || notice?.tone === 'error' ? 'alert' : 'status'}
        >
          {fieldError ?? notice?.text}
        </p>
      )}
    </form>
  )
}
