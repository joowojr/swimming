import GoogleLoginButton from '../features/auth/GoogleLoginButton'
import { authActions } from '../store/authStore'
import styles from './LoginPage.module.css'

export default function LoginPage() {
  return (
    <div className={styles['login-page']}>
      <section className={styles['login-panel']} aria-labelledby="login-title">
        <h1 id="login-title">로그인</h1>
        <GoogleLoginButton onSuccess={authActions.completeLogin} />
      </section>
    </div>
  )
}
