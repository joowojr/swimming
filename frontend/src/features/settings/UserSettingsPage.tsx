import { useState, type FormEvent } from 'react'
import { IconBrandGoogle, IconCheck, IconLock, IconUser } from '@tabler/icons-react'
import type { AuthUser } from '../auth/authTypes'
import { changePassword } from './userSettingsApi'
import styles from './UserSettingsPage.module.css'

interface UserSettingsPageProps {
  user: AuthUser
}

interface PasswordFields {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

type FieldName = keyof PasswordFields
type FormState = 'idle' | 'loading' | 'success' | 'error'

const initialFields: PasswordFields = {
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
}

export default function UserSettingsPage({ user }: UserSettingsPageProps) {
  const [fields, setFields] = useState(initialFields)
  const [touched, setTouched] = useState<Partial<Record<FieldName, boolean>>>({})
  const [formState, setFormState] = useState<FormState>('idle')
  const [message, setMessage] = useState('')

  const errors: Partial<Record<FieldName, string>> = {
    currentPassword: touched.currentPassword && !fields.currentPassword
      ? '현재 비밀번호를 입력해 주세요.'
      : undefined,
    newPassword: touched.newPassword && fields.newPassword.length < 10
      ? '새 비밀번호는 10자 이상 입력해 주세요.'
      : touched.newPassword && !/[0-9]|[^\p{L}\p{N}\s]/u.test(fields.newPassword)
        ? '숫자 또는 특수문자를 하나 이상 포함해 주세요.'
      : touched.newPassword && fields.newPassword === fields.currentPassword
        ? '현재 비밀번호와 다른 비밀번호를 입력해 주세요.'
      : undefined,
    confirmPassword: touched.confirmPassword && fields.confirmPassword !== fields.newPassword
      ? '새 비밀번호가 일치하지 않습니다.'
      : undefined,
  }

  const updateField = (name: FieldName, value: string) => {
    setFields((current) => ({ ...current, [name]: value }))
    setFormState('idle')
    setMessage('')
  }

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const nextTouched = {
      currentPassword: true,
      newPassword: true,
      confirmPassword: true,
    }
    setTouched(nextTouched)

    const hasError = !fields.currentPassword
      || fields.newPassword.length < 10
      || !/[0-9]|[^\p{L}\p{N}\s]/u.test(fields.newPassword)
      || fields.newPassword === fields.currentPassword
      || fields.confirmPassword !== fields.newPassword
    if (hasError) return

    setFormState('loading')
    setMessage('')
    try {
      await changePassword({
        currentPassword: fields.currentPassword,
        newPassword: fields.newPassword,
      })
      setFields(initialFields)
      setTouched({})
      setFormState('success')
      setMessage('비밀번호가 변경되었습니다.')
    } catch (error) {
      setFormState('error')
      setMessage(error instanceof Error && error.message
        ? error.message
        : '비밀번호를 변경하지 못했습니다. 입력 정보를 확인해 주세요.')
    }
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div className={styles['header-mark']} aria-hidden="true">
          <IconUser size={22} stroke={1.6} />
        </div>
        <div>
          <p className={styles.eyebrow}>개인 설정</p>
          <h1>내 계정</h1>
          <p className={styles.intro}>계정 정보와 로그인 보안을 관리합니다.</p>
        </div>
      </header>

      <section className={styles.card} aria-labelledby="account-information-heading">
        <div className={`${styles.section} ${styles['account-information']}`}>
          <div className={styles['section-heading']}>
            <h2 id="account-information-heading">계정 정보</h2>
            <p>현재 로그인에 사용하는 정보입니다.</p>
          </div>
          <div className={styles.field}>
            <label htmlFor="account-email">이메일</label>
            <input id="account-email" type="email" value={user.email} disabled />
            <span className={styles.helper}>이메일 변경은 Google 연동 준비 후 지원됩니다.</span>
          </div>
        </div>

        <div className={styles.divider} />

        <div className={styles.section}>
          <div className={styles['section-heading']}>
            <div className={styles['heading-with-icon']}>
              <IconLock size={18} stroke={1.7} aria-hidden="true" />
              <h2>비밀번호</h2>
            </div>
            <p>현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.</p>
          </div>

          <form className={styles.form} onSubmit={submit} noValidate>
            <PasswordField
              id="current-password"
              label="현재 비밀번호"
              name="currentPassword"
              value={fields.currentPassword}
              error={errors.currentPassword}
              onBlur={() => setTouched((current) => ({ ...current, currentPassword: true }))}
              onChange={updateField}
              autoComplete="current-password"
            />
            <PasswordField
              id="new-password"
              label="새 비밀번호"
              name="newPassword"
              value={fields.newPassword}
              error={errors.newPassword}
              onBlur={() => setTouched((current) => ({ ...current, newPassword: true }))}
              onChange={updateField}
              autoComplete="new-password"
              maxLength={64}
            />
            <PasswordField
              id="confirm-password"
              label="새 비밀번호 확인"
              name="confirmPassword"
              value={fields.confirmPassword}
              error={errors.confirmPassword}
              onBlur={() => setTouched((current) => ({ ...current, confirmPassword: true }))}
              onChange={updateField}
              autoComplete="new-password"
              maxLength={64}
            />
            <div className={styles['form-actions']}>
              <button
                className={styles['primary-button']}
                type="submit"
                disabled={formState === 'loading'}
                data-state={formState}
              >
                {formState === 'loading' ? '변경 중…' : '비밀번호 변경'}
              </button>
              <p className={`${styles.feedback} ${styles[`feedback-${formState}`]}`} role={formState === 'error' ? 'alert' : 'status'}>
                {formState === 'success' && <IconCheck size={16} aria-hidden="true" />}
                {message}
              </p>
            </div>
          </form>
        </div>
      </section>

      <section className={styles.card} aria-labelledby="integrations-heading">
        <div className={styles.section}>
          <div className={styles['section-heading']}>
            <h2 id="integrations-heading">연동</h2>
            <p>다른 서비스와 계정을 연결합니다.</p>
          </div>
          <div className={styles.integration}>
            <div className={styles['integration-copy']}>
              <span className={styles['google-mark']} aria-hidden="true"><IconBrandGoogle size={20} /></span>
              <div>
                <strong>Google 이메일</strong>
                <span>연동 준비 중</span>
              </div>
            </div>
            <button className={styles['secondary-button']} type="button" disabled aria-describedby="google-disabled-help">
              연동하기
            </button>
          </div>
          <p className={styles['disabled-help']} id="google-disabled-help">Google 계정 연동 기능은 준비 중입니다.</p>
        </div>
      </section>
    </div>
  )
}

interface PasswordFieldProps {
  id: string
  label: string
  name: FieldName
  value: string
  error?: string
  autoComplete: string
  maxLength?: number
  onBlur: () => void
  onChange: (name: FieldName, value: string) => void
}

function PasswordField({ id, label, name, value, error, autoComplete, maxLength, onBlur, onChange }: PasswordFieldProps) {
  return (
    <div className={styles.field} data-state={error ? 'error' : value ? 'filled' : 'default'}>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        name={name}
        type="password"
        value={value}
        autoComplete={autoComplete}
        maxLength={maxLength}
        aria-invalid={Boolean(error)}
        aria-describedby={`${id}-help`}
        onBlur={onBlur}
        onChange={(event) => onChange(name, event.target.value)}
      />
      <span className={`${styles.helper} ${error ? styles['helper-error'] : ''}`} id={`${id}-help`}>
        {error ?? ' '}
      </span>
    </div>
  )
}
