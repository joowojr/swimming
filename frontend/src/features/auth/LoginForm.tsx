import { useState, type FormEvent } from 'react'
import type { ApiError } from '../../api/client'
import { login } from './authApi'
import type {
  AuthResponse,
  LoginFieldErrors,
  LoginRequest,
} from './authTypes'
import styles from './LoginForm.module.css'

interface LoginFormProps {
  onSuccess: (response: AuthResponse) => void
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate(request: LoginRequest): LoginFieldErrors {
  const errors: LoginFieldErrors = {}

  if (!request.email) {
    errors.email = '이메일을 입력해 주세요.'
  } else if (!EMAIL_PATTERN.test(request.email)) {
    errors.email = '올바른 이메일 형식을 입력해 주세요.'
  }

  if (!request.password) {
    errors.password = '비밀번호를 입력해 주세요.'
  }

  return errors
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null
}

export default function LoginForm({ onSuccess }: LoginFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<LoginFieldErrors>({})
  const [requestError, setRequestError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const request: LoginRequest = {
      email: email.trim(),
      password,
    }
    const validationErrors = validate(request)

    setFieldErrors(validationErrors)
    setRequestError(null)

    if (Object.keys(validationErrors).length > 0) {
      return
    }

    setIsSubmitting(true)

    try {
      const response = await login(request)
      onSuccess(response)
    } catch (error: unknown) {
      if (isApiError(error)) {
        const apiFieldErrors = error.errors ?? {}
        setFieldErrors({
          email: apiFieldErrors.email,
          password: apiFieldErrors.password,
        })
        setRequestError(
          error.message ?? '이메일 또는 비밀번호가 올바르지 않습니다. 입력 정보를 확인해 주세요.',
        )
      } else {
        setRequestError('로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form className={styles['login-form']} onSubmit={handleSubmit} noValidate>
      <div className={styles['form-field']}>
        <label htmlFor="login-email">이메일</label>
        <input
          id="login-email"
          name="email"
          type="email"
          autoComplete="email"
          value={email}
          aria-invalid={Boolean(fieldErrors.email)}
          aria-describedby={fieldErrors.email ? 'login-email-error' : undefined}
          onChange={(event) => {
            setEmail(event.target.value)
            setFieldErrors((current) => ({ ...current, email: undefined }))
          }}
        />
        {fieldErrors.email && (
          <p className={styles['field-error']} id="login-email-error" role="alert">
            {fieldErrors.email}
          </p>
        )}
      </div>

      <div className={styles['form-field']}>
        <label htmlFor="login-password">비밀번호</label>
        <input
          id="login-password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          aria-invalid={Boolean(fieldErrors.password)}
          aria-describedby={
            fieldErrors.password ? 'login-password-error' : undefined
          }
          onChange={(event) => {
            setPassword(event.target.value)
            setFieldErrors((current) => ({ ...current, password: undefined }))
          }}
        />
        {fieldErrors.password && (
          <p className={styles['field-error']} id="login-password-error" role="alert">
            {fieldErrors.password}
          </p>
        )}
      </div>

      {requestError && (
        <p className={styles['form-error']} role="alert">
          {requestError}
        </p>
      )}

      <button className={styles['login-submit']} type="submit" disabled={isSubmitting}>
        {isSubmitting ? '로그인 중…' : '로그인'}
      </button>
    </form>
  )
}
