import { IconBrandGoogle, IconUser } from '@tabler/icons-react'
import type { AuthUser } from '../auth/authTypes'
import styles from './UserSettingsPage.module.css'

interface UserSettingsPageProps {
  user: AuthUser
}

export default function UserSettingsPage({ user }: UserSettingsPageProps) {
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles['header-mark']} aria-hidden="true">
          <IconUser size={22} stroke={1.6} />
        </div>
        <div>
          <p className={styles.eyebrow}>개인 설정</p>
          <h1>내 계정</h1>
          <p className={styles.intro}>Google 계정으로 로그인하고 있습니다.</p>
        </div>
      </header>

      <section className={styles.card} aria-labelledby="account-information-heading">
        <div className={`${styles.section} ${styles['account-information']}`}>
          <div className={styles['section-heading']}>
            <h2 id="account-information-heading">계정 정보</h2>
            <p>Google에서 확인한 로그인 정보입니다.</p>
          </div>
          <div className={styles.field}>
            <label htmlFor="account-email">이메일</label>
            <input id="account-email" type="email" value={user.email} disabled />
          </div>
        </div>

        <div className={styles.divider} />

        <div className={styles.section}>
          <div className={styles['integration-copy']}>
            <span className={styles['google-mark']} aria-hidden="true"><IconBrandGoogle size={20} /></span>
            <div>
              <strong>Google</strong>
              <span>로그인에 사용 중</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}
