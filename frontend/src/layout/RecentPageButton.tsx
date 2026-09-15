import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { IconArrowRight } from '@tabler/icons-react'
import { findSection } from './navigationItems'
import type { Folder } from '../features/folders/folderTypes'
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

/**
 * 역할: 마지막으로 보던 페이지로 돌아가는 플로팅 버튼. 기록도 여기서 한다.
 * 화면 위에 떠 있어 어느 페이지에서나 보이므로, 지금 보고 있는 곳은 가리키지 않는다.
 */
export default function RecentPageButton() {
  const { pathname, search } = useLocation()
  const recentHrefs = useRecentPageStore((state) => state.hrefs)
  const record = useRecentPageStore((state) => state.record)
  const folders = useFolderStore((state) => state.folders)

  useEffect(() => {
    // 같은 페이지의 다른 화면도 서로 다른 목적지라 쿼리까지 남긴다.
    if (findSection(pathname)) record(`${pathname}${search}`)
  }, [pathname, search, record])

  // 최근 순으로 훑어야 한다. 메뉴 배열 순서로 찾으면 더 예전에 본 쪽이 걸린다.
  // 지금 보고 있는 페이지는 화면만 다르더라도 가리키지 않는다.
  const recentHref = recentHrefs.find((href) => pathOf(href) !== pathname)
  const recent = findSection(pathOf(recentHref ?? ''))
  if (!recentHref || !recent) return null

  const { label, detail } = describe(recentHref, recent.label, folders)
  const Icon = recent.icon

  return (
    <Link
      className={styles['recent-page']}
      to={recentHref}
      aria-label={`이어서 보기: ${label}${detail ? ` ${detail}` : ''}`}
    >
      <Icon className={styles.mark} size={20} stroke={1.8} aria-hidden="true" />
      <span className={styles.copy}>
        <span className={styles.eyebrow}>이어서 보기</span>
        <span className={styles.line}>
          <strong className={styles.label}>{label}</strong>
          {detail && <span className={styles.detail}>{detail}</span>}
        </span>
      </span>
      <IconArrowRight className={styles.mark} size={20} stroke={1.8} aria-hidden="true" />
    </Link>
  )
}
