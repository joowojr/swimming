import LoginForm from '../features/auth/LoginForm'
import { authActions } from '../store/authStore'

export default function LoginPage() {
  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <h1 id="login-title">로그인</h1>
        <LoginForm onSuccess={authActions.completeLogin} />
      </section>
    </main>
  )
}
