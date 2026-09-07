import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { IconChevronRight } from '@tabler/icons-react'
import { Link } from 'react-router-dom'
import type { ApiError } from '../../api/client'
import ModeToggle from '../../components/ModeToggle'
import FolderHeader from '../folders/FolderHeader'
import type { Folder } from '../folders/folderTypes'
import LinkComposer from './LinkComposer'
import SourceFeedCard from './SourceFeedCard'
import { getSources } from './knowledgeApi'
import type { SourceCard } from './knowledgeTypes'
import styles from './LinkFolderView.module.css'

interface LinkFolderViewProps {
  folder: Folder
  onDeleted: (folderId: number) => void
}

type SourceView = 'list' | 'graph'

const SOURCE_VIEWS: { value: SourceView; label: string }[] = [
  { value: 'list', label: '목록' },
  { value: 'graph', label: '그래프' },
]

// 그래프는 렌더링 라이브러리를 함께 받아 온다. 목록만 볼 때는 내려받지 않게 나눠 둔다.
const KnowledgeGraph = lazy(() => import('./graph/KnowledgeGraph'))

type ListState =
  | { status: 'loading' }
  | { status: 'ready'; items: SourceCard[]; nextCursor: string | null }
  | { status: 'error'; message: string }

function errorMessage(error: unknown) {
  const apiMessage = typeof error === 'object' && error !== null
    ? (error as ApiError).message
    : undefined
  return apiMessage ?? '저장된 링크를 불러오지 못했습니다.'
}

/** 폴더 하나에 저장된 Source를 최근 순으로 보여준다. 할 일 폴더 화면과 같은 뼈대를 쓴다. */
export default function LinkFolderView({ folder, onDeleted }: LinkFolderViewProps) {
  const [state, setState] = useState<ListState>({ status: 'loading' })
  const [view, setView] = useState<SourceView>('list')
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [requestKey, setRequestKey] = useState(0)

  // 폴더를 바꾸면 쓰는 쪽이 key로 새로 마운트하므로 여기서 loading으로 되돌리지 않는다.
  useEffect(() => {
    let active = true

    void getSources(folder.id)
      .then((page) => {
        if (active) setState({ status: 'ready', items: page.items, nextCursor: page.nextCursor })
      })
      .catch((error: unknown) => {
        if (active) setState({ status: 'error', message: errorMessage(error) })
      })

    return () => { active = false }
  }, [folder.id, requestKey])

  const loadMore = useCallback(async () => {
    if (state.status !== 'ready' || !state.nextCursor || isLoadingMore) return

    setIsLoadingMore(true)
    try {
      const page = await getSources(folder.id, { cursor: state.nextCursor })
      setState((current) => current.status === 'ready'
        ? { status: 'ready', items: [...current.items, ...page.items], nextCursor: page.nextCursor }
        : current)
    } catch {
      // 다음 페이지를 못 가져와도 이미 보이는 목록은 그대로 둔다.
    } finally {
      setIsLoadingMore(false)
    }
  }, [folder.id, isLoadingMore, state])

  const removeSource = (sourceId: string) => {
    setState((current) => current.status === 'ready'
      ? { ...current, items: current.items.filter((item) => item.sourceId !== sourceId) }
      : current)
  }

  const prependSource = (source: SourceCard) => {
    setState((current) => {
      if (current.status !== 'ready') return current
      const rest = current.items.filter((item) => item.sourceId !== source.sourceId)
      return { ...current, items: [source, ...rest] }
    })
  }

  const savedCount = state.status === 'ready'
    ? `${state.items.length}${state.nextCursor ? '개 이상' : '개'}`
    : null

  return (
    <article className={styles.view} aria-labelledby="link-folder-title">
      <nav className={styles.breadcrumb} aria-label="Breadcrumb">
        <Link to="/folders?section=links">폴더</Link>
        {folder.tag && (
          <>
            <IconChevronRight size={14} aria-hidden="true" />
            <span>{folder.tag.name}</span>
          </>
        )}
        <IconChevronRight size={14} aria-hidden="true" />
        <span aria-current="page">{folder.name}</span>
      </nav>

      <FolderHeader
        folder={folder}
        titleId="link-folder-title"
        deleteMessage="폴더를 삭제하면 이 폴더에 저장한 링크도 함께 사라집니다."
        showStatus={false}
        onDeleted={onDeleted}
      />

      <section className={styles.sources} aria-labelledby="link-sources-title">
        <div className={styles['section-heading']}>
          <div className={styles['section-title']}>
            <h3 id="link-sources-title">저장된 링크</h3>
            <span>
              {savedCount
                ? `Source ${savedCount}를 모았어요`
                : '저장된 링크를 확인하고 있어요'}
            </span>
          </div>
          <ModeToggle
            ariaLabel="저장된 링크 보기 방식"
            options={SOURCE_VIEWS}
            value={view}
            onChange={setView}
          />
        </div>

        {view === 'graph' ? (
          <Suspense
            fallback={<div className={styles.state} role="status"><p>지식 그래프를 준비하고 있습니다.</p></div>}
          >
            <KnowledgeGraph
              folderId={folder.id}
              sources={state.status === 'ready' ? state.items : []}
            />
          </Suspense>
        ) : (
        <div className={styles['link-list-stack']}>
          <LinkComposer folderId={folder.id} onSaved={prependSource} />

          {state.status === 'loading' ? (
            <div className={styles.state} role="status">
              <p>저장된 링크를 불러오고 있습니다.</p>
            </div>
          ) : state.status === 'error' ? (
            <div className={styles.state}>
              <p>{state.message}</p>
              <button
                type="button"
                onClick={() => {
                  setState({ status: 'loading' })
                  setRequestKey((key) => key + 1)
                }}
              >
                다시 불러오기
              </button>
            </div>
          ) : state.items.length === 0 ? (
            <div className={styles.state}>
              <p>아직 저장된 링크가 없어요.</p>
              <p className={styles.hint}>링크를 붙여 넣으면 이곳에 하나씩 쌓입니다.</p>
            </div>
          ) : (
            <>
              <div className={styles.feed}>
                {state.items.map((source) => (
                  <SourceFeedCard
                    key={source.sourceId}
                    source={source}
                    onDeleted={removeSource}
                  />
                ))}
              </div>
              {state.nextCursor && (
                <button
                  type="button"
                  className={styles.more}
                  disabled={isLoadingMore}
                  onClick={() => void loadMore()}
                >
                  {isLoadingMore ? '불러오는 중' : '더 보기'}
                </button>
              )}
            </>
          )}
        </div>
        )}
      </section>
    </article>
  )
}
