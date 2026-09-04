import {useEffect, useRef, useState} from 'react'
import {Link} from 'react-router-dom'
import {IconSearch,} from '@tabler/icons-react'
import styles from './TopBar.module.css'
import logoUrl from '../assets/logo.svg'

interface TopBarProps {
  userEmail: string | null
  onLogin: () => void
}

export default function TopBar({ userEmail, onLogin }: TopBarProps) {
  const searchInputRef = useRef<HTMLInputElement>(null)
  const [searchQuery, setSearchQuery] = useState('')

  useEffect(() => {
    const focusGlobalSearch = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        searchInputRef.current?.focus()
      }
    }

    window.addEventListener('keydown', focusGlobalSearch)
    return () => window.removeEventListener('keydown', focusGlobalSearch)
  }, [])

  return (
    <header className={styles['top-bar']}>
      <div className={styles['top-bar-primary']}>
        <Link className={styles.brand} to="/pinboard" aria-label="Swimming 핀보드">
          <span className={styles['brand-mark']} aria-hidden="true">
            <img src={logoUrl} alt=""/>
          </span>
          {/*<span className={styles['brand-name']}>swimming</span>*/}
        </Link>
        <nav className={styles['workspace-navigation']} aria-label="워크스페이스">
        </nav>
      </div>

      <div className={styles['top-bar-tools']}>
        <label className={styles['global-search']}>
          <span className="sr-only">전체 검색</span>
          <IconSearch className={styles['global-search-icon']} size={16} aria-hidden="true" />
          <input
            ref={searchInputRef}
            type="search"
            value={searchQuery}
            placeholder="검색어를 입력하세요"
            aria-label="전체 검색"
            onChange={(event) => setSearchQuery(event.target.value)}
          />
          <span className={styles['search-shortcut']} aria-hidden="true">
            <kbd>⌘</kbd>
            <kbd>K</kbd>
          </span>
        </label>

        <div className={styles['global-actions']} aria-label="전역 도구">
          {/*<button type="button" aria-label="알림 — 준비 중" title="알림 · 준비 중" disabled>*/}
          {/*  <IconBell size={18} aria-hidden="true" />*/}
          {/*</button>*/}
          {/*<button type="button" aria-label="도움말 — 준비 중" title="도움말 · 준비 중" disabled>*/}
          {/*  <IconHelpCircle size={18} aria-hidden="true" />*/}
          {/*</button>*/}
          {/*<button type="button" aria-label="설정 — 준비 중" title="설정 · 준비 중" disabled>*/}
          {/*  <IconSettings size={18} aria-hidden="true" />*/}
          {/*</button>*/}
        </div>

        <div className={styles['account-menu']}>
          {userEmail ? (
            <Link className={styles['account-link']} to="/settings" aria-label="개인 설정 열기">
              <div className={styles['account-copy']}>
                <strong>{userEmail}</strong>
                <span>내 계정</span>
              </div>
              <span className={styles['account-avatar']} aria-hidden="true">
                {userEmail.slice(0, 1).toUpperCase()}
              </span>
            </Link>
          ) : (
            <div className={styles['guest-actions']}>
              <button className={styles['top-bar-login']} type="button" onClick={onLogin}>
                로그인
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
