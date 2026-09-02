import { useEffect, useRef, useState } from 'react'
import { loginWithGoogle } from './authApi'
import type { AuthResponse } from './authTypes'
import styles from './GoogleLoginButton.module.css'

interface GoogleCredentialResponse {
  credential: string
}

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (configuration: {
            client_id: string
            callback: (response: GoogleCredentialResponse) => void
          }) => void
          renderButton: (parent: HTMLElement, options: Record<string, string>) => void
        }
      }
    }
  }
}

interface GoogleLoginButtonProps {
  onSuccess: (response: AuthResponse) => void
}

export default function GoogleLoginButton({ onSuccess }: GoogleLoginButtonProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
    const container = containerRef.current
    if (!clientId || !container) {
      setError('Google 로그인을 준비하지 못했습니다.')
      return
    }

    const render = () => {
      if (!window.google) {
        setError('Google 로그인을 불러오지 못했습니다.')
        return
      }
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: async ({ credential }) => {
          try {
            setError(null)
            onSuccess(await loginWithGoogle({ credential }))
          } catch (requestError: unknown) {
            setError(requestError instanceof Error && requestError.message
              ? requestError.message
              : 'Google 로그인에 실패했습니다. 다시 시도해 주세요.')
          }
        },
      })
      container.replaceChildren()
      window.google.accounts.id.renderButton(container, {
        theme: 'outline',
        size: 'large',
        text: 'signin_with',
        shape: 'rectangular',
        width: '320',
      })
    }

    if (window.google) {
      render()
      return
    }
    const script = document.getElementById('google-identity-services')
    script?.addEventListener('load', render, { once: true })
    script?.addEventListener('error', () => setError('Google 로그인을 불러오지 못했습니다.'), { once: true })
    return () => script?.removeEventListener('load', render)
  }, [onSuccess])

  return (
    <div className={styles.login}>
      <div ref={containerRef} />
      {error && <p className={styles.error} role="alert">{error}</p>}
    </div>
  )
}
