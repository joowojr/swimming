import { IconChartBar, IconSparkles, IconTargetArrow } from '@tabler/icons-react'
import loginArt from '../../assets/login.svg'
import GoogleLoginButton from './GoogleLoginButton'
import { authActions } from '../../store/authStore'
import styles from './LoginPage.module.css'

const FEATURES = [
  {
    Icon: IconTargetArrow,
    tone: 'fountain',
    title: '복잡한 할 일은, 오늘 할 만큼만',
    body: '할 일을 정리해보고, 지금 시작할 일에만 집중하세요.',
  },
  {
    Icon: IconChartBar,
    tone: 'indigo',
    title: '집중할 시간과 할 일을 정해두기',
    body: '세션을 시작해, 그동안은 지금 할 일에만 집중하세요.',
  },
  {
    Icon: IconSparkles,
    tone: 'bay-leaf',
    title: '생각이 엉켜 있어도, 시작할 수 있게',
    body: '떠오른 노트를 AI가 실행 가능한 할 일로 바꿔줍니다.',
  },
] as const

export default function LoginPage() {
  return (
    <div className={styles['login-page']}>
      <section className={styles.hero} aria-hidden="true">
        <img className={styles['hero-art']} src={loginArt} alt="" />
      </section>

      <section className={styles['login-panel']} aria-labelledby="login-title">
        <div className={styles['panel-inner']}>
          <p className={styles.eyebrow}>나만의 속도로, swimming</p>
          <h1 id="login-title">오늘의 흐름을 시작하기</h1>
          <p className={styles.lead}>
            해야 할 일을 적고, 지금의 흐름에 올라타세요.
          </p>

          <GoogleLoginButton onSuccess={authActions.completeLogin} />
          <p className={styles.note}>Google 계정으로 바로 시작합니다.</p>

          <ul className={styles.features}>
            {FEATURES.map(({ Icon, tone, title, body }) => (
              <li key={title} className={styles.feature}>
                <span className={styles['feature-icon']} data-tone={tone}>
                  <Icon size={16} stroke={2} />
                </span>
                <span>
                  <strong className={styles['feature-title']}>{title}</strong>
                  <span className={styles['feature-body']}>{body}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  )
}
