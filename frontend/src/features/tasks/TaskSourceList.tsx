import { useEffect, useMemo, useState } from 'react'
import { IconLoader2, IconPlus, IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import LoadMoreButton from '../../components/LoadMoreButton'
import { getSources } from '../knowledge/knowledgeApi'
import { SOURCE_STATUS_LABEL } from '../knowledge/knowledgeLabels'
import type { SourceCard } from '../knowledge/knowledgeTypes'
import { attachTaskSources, detachTaskSource, getTaskSources } from './taskSourceApi'
import type { TaskSource } from './taskSourceTypes'
import styles from './TaskSourceList.module.css'

interface TaskSourceListProps {
  taskId: number
  /** 고를 수 있는 링크의 범위. 미분류 할 일은 볼 폴더가 없어 null이다. */
  folderId: number | null
}

/** 무엇을 다루는 문서인지 한눈에 보이도록 목적(topic)을 앞에, 개념(subject)을 뒤에 둔다. */
function tagsOf(source: SourceCard) {
  return [...(source.topic ? [source.topic] : []), ...(source.subjects ?? [])]
}

function errorText(error: unknown, fallback: string) {
  const apiError = error as ApiError
  return apiError?.message ?? fallback
}

/**
 * 역할: 폴더에 저장해 둔 링크를 골라 할 일에 연결하고, 연결한 것을 다시 뗀다.
 *
 * 여기서 링크를 새로 수집하지 않는다. 수집은 폴더의 링크 화면이 맡고, 이 창은 이미
 * 저장된 것 중에서 고르기만 한다. 연결은 누르는 즉시 반영되어 저장 버튼이 없다.
 */
export default function TaskSourceList({ taskId, folderId }: TaskSourceListProps) {
  const [attached, setAttached] = useState<TaskSource[]>([])
  const [candidates, setCandidates] = useState<SourceCard[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  /** 지금 붙이거나 떼는 중인 링크. 한 번에 하나만 다룬다. */
  const [busySourceId, setBusySourceId] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    const load = async () => {
      try {
        // 연결 목록이 먼저다. 폴더가 없어도 이미 연결된 링크는 보여 줘야 한다.
        const sources = await getTaskSources(taskId, controller.signal)
        if (controller.signal.aborted) return
        setAttached(sources)

        if (folderId !== null) {
          const page = await getSources(folderId)
          if (controller.signal.aborted) return
          setCandidates(page.items)
          setNextCursor(page.nextCursor)
        }
        setIsLoading(false)
      } catch (caught: unknown) {
        if (controller.signal.aborted) return
        setError(errorText(caught, '링크를 불러오지 못했어요.'))
        setIsLoading(false)
      }
    }

    void load()
    return () => controller.abort()
  }, [taskId, folderId])

  const loadMore = async () => {
    if (folderId === null || !nextCursor || isLoadingMore) return

    setIsLoadingMore(true)
    setError(null)
    try {
      const page = await getSources(folderId, { cursor: nextCursor })
      setCandidates((current) => [...current, ...page.items])
      setNextCursor(page.nextCursor)
    } catch (caught: unknown) {
      setError(errorText(caught, '링크를 더 불러오지 못했어요.'))
    } finally {
      setIsLoadingMore(false)
    }
  }

  const attach = async (source: SourceCard) => {
    if (busySourceId) return

    setBusySourceId(source.sourceId)
    setError(null)
    try {
      setAttached(await attachTaskSources(taskId, [source.sourceId]))
    } catch (caught: unknown) {
      setError(errorText(caught, '링크를 연결하지 못했어요.'))
    } finally {
      setBusySourceId(null)
    }
  }

  const detach = async (sourceId: string) => {
    if (busySourceId) return

    setBusySourceId(sourceId)
    setError(null)
    try {
      await detachTaskSource(taskId, sourceId)
      setAttached((current) => current.filter((source) => source.sourceId !== sourceId))
    } catch (caught: unknown) {
      setError(errorText(caught, '링크를 떼지 못했어요.'))
    } finally {
      setBusySourceId(null)
    }
  }

  // 이미 연결한 링크는 고르는 목록에서 뺀다. 같은 링크가 두 줄에 나오면 어느 쪽을 눌러야
  // 하는지 알 수 없다.
  const choices = useMemo(() => {
    const attachedIds = new Set(attached.map((source) => source.sourceId))
    return candidates.filter((source) => !attachedIds.has(source.sourceId))
  }, [attached, candidates])

  return (
    <div className={styles.panel}>
      {error && <p className={styles.message} data-tone="error" role="alert">{error}</p>}

      <section className={styles.section} aria-labelledby="task-sources-attached">
        <span className={styles.title} id="task-sources-attached">연결한 링크</span>
        {isLoading
          ? <p className={styles.empty}>불러오는 중이에요.</p>
          : attached.length === 0
            ? <p className={styles.empty}>아직 연결한 링크가 없어요.</p>
            : (
              <ul className={styles.list}>
                {attached.map((source) => (
                  <li className={styles.item} key={source.sourceId}>
                    <a className={styles.link} href={source.url} target="_blank" rel="noreferrer noopener">
                      <span className={styles.linkTitle}>{source.title}</span>
                      {source.status !== 'COMPLETED' && (
                        <span className={styles.status} data-tone={source.status === 'FAILED' ? 'error' : 'mute'}>
                          {SOURCE_STATUS_LABEL[source.status]}
                        </span>
                      )}
                    </a>
                    <button
                      type="button"
                      className={styles.action}
                      aria-label={`${source.title} 연결 끊기`}
                      disabled={busySourceId !== null}
                      onClick={() => void detach(source.sourceId)}
                    >
                      {busySourceId === source.sourceId
                        ? <IconLoader2 className={styles.spinner} size={16} stroke={1.8} aria-hidden="true" />
                        : <IconX size={16} stroke={1.8} aria-hidden="true" />}
                    </button>
                  </li>
                ))}
              </ul>
            )}
      </section>

      <section className={styles.section} aria-labelledby="task-sources-choices">
        <span className={styles.title} id="task-sources-choices">폴더에 저장한 링크</span>

        {folderId === null
          ? <p className={styles.empty}>할 일에 폴더를 정하면 그 폴더에 모은 링크를 연결할 수 있어요.</p>
          : (
            <>
              {isLoading
                ? <p className={styles.empty}>불러오는 중이에요.</p>
                : choices.length === 0
                  ? (
                    <p className={styles.empty}>
                      {candidates.length === 0
                        ? '이 폴더에 저장한 링크가 없어요. 링크 화면에서 먼저 모아 주세요.'
                        : '저장한 링크를 모두 연결했어요.'}
                    </p>
                  )
                  : (
                    <ul className={styles.list}>
                      {choices.map((source) => (
                        <li className={styles.choice} key={source.sourceId}>
                          <div className={styles.choiceBody}>
                            <a className={styles.link} href={source.url} target="_blank" rel="noreferrer noopener">
                              <span className={styles.linkTitle}>{source.title}</span>
                              {source.domain && <span className={styles.status}>{source.domain}</span>}
                            </a>
                            {tagsOf(source).length > 0 && (
                              <div className={styles.chips}>
                                {tagsOf(source).map((tag) => (
                                  <span className={styles.chip} key={tag.nodeId}>{tag.title}</span>
                                ))}
                              </div>
                            )}
                          </div>
                          <button
                            type="button"
                            className={styles.action}
                            aria-label={`${source.title} 연결하기`}
                            disabled={busySourceId !== null}
                            onClick={() => void attach(source)}
                          >
                            {busySourceId === source.sourceId
                              ? <IconLoader2 className={styles.spinner} size={16} stroke={1.8} aria-hidden="true" />
                              : <IconPlus size={16} stroke={1.8} aria-hidden="true" />}
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}

              {nextCursor && (
                <LoadMoreButton isLoading={isLoadingMore} onClick={() => void loadMore()} />
              )}
            </>
          )}
      </section>
    </div>
  )
}
