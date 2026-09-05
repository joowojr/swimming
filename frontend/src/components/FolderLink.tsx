import { Link } from 'react-router-dom'
import styles from './FolderLink.module.css'

interface FolderLinkProps {
  folderId: number
  /** 폴더 이름. 아직 못 받아온 경우를 대비해 없으면 '폴더'로 부른다. */
  name?: string
}

/** 역할: task에 붙는 폴더 이름을 그 폴더 화면으로 가는 링크로 보여준다. */
export default function FolderLink({ folderId, name }: FolderLinkProps) {
  return (
    <Link
      className={styles.link}
      to={`/folders/${folderId}`}
      // 매트릭스 카드가 드래그 대상이라 링크가 자기 드래그를 시작하지 않게 막는다.
      draggable={false}
    >
      {name ?? '폴더'}
    </Link>
  )
}
