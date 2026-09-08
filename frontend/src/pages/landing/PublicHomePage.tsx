import { Link } from 'react-router-dom'
import GoogleLoginButton from '../../features/auth/GoogleLoginButton.tsx'
import ArcadeDemo from '../../features/landing/ArcadeDemo.tsx'
import { authActions } from '../../store/authStore.ts'
import logo from '../../assets/logo.svg'
import styles from './PublicHomePage.module.css'

const WORKFLOW = [
  {
    number: '01',
    title: '적기',
    body: '흩어진 정보와 해야 할 일을 한곳에 적어두면, AI가 정리해줘요.',
  },
  {
    number: '02',
    title: '고르기',
    body: '지금 중요한 것, 먼저 해야하는 것을 생각하고 골라봐요.',
  },
  {
    number: '03',
    title: '몰입하기',
    body: '해야할 일과 시간을 정해서 세션을 시작해 몰입을 경험해보세요..',
  },
  {
    number: '04',
    title: '쌓아보기',
    body: '완료한 할 일, 정보 수집과 집중 기록을 쌓으며 다음을 생각해요.',
  },
] as const

export default function PublicHomePage() {
  return (
    <div className={styles['public-home']}>
      <header className={styles['public-header']}>
        <a className={styles.wordmark} href="#top">
          <img src={logo} alt="Swimming" />
        </a>
        <Link className={styles['header-cta']} to="/login">
          Google로 시작
        </Link>
      </header>

      <main id="top">
        <section className={styles.hero} aria-labelledby="login-title">
          <div className={styles['hero-copy']}>
            <p className={styles.intro}>혼자 일하는 날에도, 흐름은 이어지도록</p>
            <h1 id="login-title">복잡한 생각은 맡기고, </h1>
            <h1 id="login-title"> 중요한 일에 집중하세요.</h1>
            <p className={styles.lead}>
              AI가 생각과 할 일을 맥락에 맞게 정리해 실행 가능한 흐름으로 바꿉니다.
            </p>

            <div className={styles['login-area']} id="login">
              <GoogleLoginButton onSuccess={authActions.completeLogin} />
              <p className={styles.note}>Google 계정 하나로 바로 시작합니다.</p>
            </div>
          </div>

          <ArcadeDemo />
        </section>

        <section className={styles.workflow} aria-labelledby="workflow-title">
          <div className={styles['workflow-heading']}>
            <h2 id="workflow-title">생각이 행동이 되는 하나의 흐름</h2>
            <p>Swimming의 하루는 네 단계로 이어집니다.</p>
          </div>

          <ol className={styles['workflow-list']}>
            {WORKFLOW.map(({ number, title, body }) => (
              <li key={number} className={styles['workflow-step']}>
                <span className={styles['step-number']} aria-hidden="true">{number}</span>
                <div>
                  <h3>{title}</h3>
                  <p>{body}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      </main>

      <footer className={styles.footer}>
        <p className={styles['footer-statement']}>나만의 속도로, 오늘의 흐름을 시작하세요.</p>
        <div className={styles['footer-meta']}>
          <span>© {new Date().getFullYear()} Swimming</span>
          <a href="#login">시작으로 돌아가기</a>
        </div>
      </footer>

    </div>
  )
}
