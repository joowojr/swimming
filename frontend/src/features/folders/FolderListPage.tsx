import { useEffect, useState } from 'react'
import { IconFolders, IconPlus, IconTags } from '@tabler/icons-react'
import FilterMenu from '../../components/FilterMenu'
import filterMenuStyles from '../../components/FilterMenu.module.css'
import ModalTriggerButton from '../../components/ModalTriggerButton'
import { useFolderTagStore } from '../../store/folderTagStore.ts'
import FolderCard from './FolderCard.tsx'
import { folderStatusFilterLabel } from './folderTypes.ts'
import type { Folder, FolderStatusFilter } from './folderTypes.ts'
import type { FolderLoadStatus } from './folderTypes.ts'
import styles from './FolderListPage.module.css'

const FOLDER_STATUS_FILTERS = Object.keys(folderStatusFilterLabel) as FolderStatusFilter[]

/** select 값이라 문자열로 둔다. ALL은 전체, NONE은 태그 없는 폴더, 나머지는 태그 id다. */
type TagFilter = 'ALL' | 'NONE' | `${number}`

function matchesTag(folder: Folder, tagFilter: TagFilter) {
  if (tagFilter === 'ALL') return true
  if (tagFilter === 'NONE') return folder.tag === null
  return folder.tag?.id === Number(tagFilter)
}

interface ProjectListPageProps {
  folders: Folder[]
  status: FolderLoadStatus
  filter: FolderStatusFilter
  onFilterChange: (filter: FolderStatusFilter) => void
  onRetry: () => void
  onOpenCreate: () => void
  onOpenTagManage: () => void
}

export default function FolderListPage({
  folders,
  status,
  filter,
  onFilterChange,
  onRetry,
  onOpenCreate,
  onOpenTagManage,
}: ProjectListPageProps) {
  const [selectedTag, setSelectedTag] = useState<TagFilter>('ALL')
  // 상태 필터와 상관없이 내 태그 전체를 고를 수 있게 한다.
  const tags = useFolderTagStore((state) => state.tags)
  const ensureTagsLoaded = useFolderTagStore((state) => state.ensureLoaded)
  useEffect(() => { void ensureTagsLoaded() }, [ensureTagsLoaded])
  // 고른 태그가 태그 관리에서 삭제되면 전체로 본다.
  const tagFilter: TagFilter = selectedTag === 'ALL' || selectedTag === 'NONE'
    || tags.some((tag) => String(tag.id) === selectedTag)
    ? selectedTag
    : 'ALL'
  const visibleFolders = folders.filter((folder) => matchesTag(folder, tagFilter))
  const activeFilterCount = (filter === 'ACTIVE' ? 0 : 1) + (tagFilter === 'ALL' ? 0 : 1)

  return (
    <section className={styles.page} aria-labelledby="folders-page-title">
      <header className={styles.heading}>
        <div>
          <h1 id="folders-page-title">폴더</h1>
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.secondary} onClick={onOpenTagManage}>
            <IconTags size={17} aria-hidden="true" />
            태그
          </button>
          <ModalTriggerButton
            dialogId="create-folder-dialog"
            icon={<IconPlus size={15} aria-hidden="true" />}
            onClick={onOpenCreate}
          >
            새 폴더
          </ModalTriggerButton>
        </div>
      </header>

      {status === 'loading' || status === 'idle' ? (
        <div className={styles.state} role="status">
          <span className={styles['state-mark']} aria-hidden="true" />
          <p>폴더를 불러오고 있습니다.</p>
        </div>
      ) : status === 'error' ? (
        <div className={styles.state}>
          <p>폴더 목록을 불러오지 못했습니다.</p>
          <button type="button" onClick={onRetry}>다시 불러오기</button>
        </div>
      ) : (
        <>
          <div className={styles['section-heading']}>
            <span>{visibleFolders.length}개</span>
            <FilterMenu
              ariaLabel="폴더 필터"
              triggerTitle="폴더 필터"
              triggerClassName={styles.secondary}
              activeCount={activeFilterCount}
            >
              <label className={filterMenuStyles.field}>
                <span>상태</span>
                <select
                  value={filter}
                  onChange={(event) => onFilterChange(event.target.value as FolderStatusFilter)}
                >
                  {FOLDER_STATUS_FILTERS.map((value) => (
                    <option value={value} key={value}>{folderStatusFilterLabel[value]}</option>
                  ))}
                </select>
              </label>
              <label className={filterMenuStyles.field}>
                <span>태그</span>
                <select
                  value={tagFilter}
                  onChange={(event) => setSelectedTag(event.target.value as TagFilter)}
                >
                  <option value="ALL">전체</option>
                  {tags.map((tag) => (
                    <option value={String(tag.id)} key={tag.id}>{tag.name}</option>
                  ))}
                  <option value="NONE">태그 없음</option>
                </select>
              </label>
            </FilterMenu>
          </div>
          {visibleFolders.length === 0 ? (
            <div className={styles.empty}>
              <IconFolders size={28} stroke={1.5} aria-hidden="true" />
              {tagFilter !== 'ALL' && folders.length > 0 ? (
                <>
                  <h3>폴더가 없습니다.</h3>
                </>
              ) : filter === 'ACTIVE' ? (
                <>
                  <h3>폴더를 시작할 준비가 되었습니다.</h3>
                  <p>새 폴더를 만들면 이곳에서 한눈에 확인할 수 있습니다.</p>
                </>
              ) : (
                <>
                  <h3>{folderStatusFilterLabel[filter]} 폴더가 없습니다.</h3>
                  <p>필터를 바꾸면 다른 상태의 폴더를 볼 수 있습니다.</p>
                </>
              )}
            </div>
          ) : (
            <div className={styles.grid}>
              {visibleFolders.map((folder) => (
                <FolderCard key={folder.id} folder={folder} />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  )
}
