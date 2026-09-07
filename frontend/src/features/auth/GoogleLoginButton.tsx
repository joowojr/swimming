import { useEffect, useRef, useState } from 'react'
import { loginWithGoogle } from './authApi'
import type { AuthResponse } from './authTypes'
import styles from './GoogleLoginButton.module.css'

interface GoogleCredentialResponse {
  credential: string
}

interface GoogleIdentityState {
  clientId: string
  credentialHandler: ((response: GoogleCredentialResponse) => void) | null
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
    swimmingGoogleIdentity?: GoogleIdentityState
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

    const credentialHandler = async ({ credential }: GoogleCredentialResponse) => {
      try {
        setError(null)
        onSuccess(await loginWithGoogle({ credential }))
      } catch (requestError: unknown) {
        setError(requestError instanceof Error && requestError.message
          ? requestError.message
          : 'Google 로그인에 실패했습니다. 다시 시도해 주세요.')
      }
    }

    const render = () => {
      if (!window.google) {
        setError('Google 로그인을 불러오지 못했습니다.')
        return
      }

      if (!window.swimmingGoogleIdentity) {
        const identityState: GoogleIdentityState = {
          clientId,
          credentialHandler,
        }
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => identityState.credentialHandler?.(response),
        })
        window.swimmingGoogleIdentity = identityState
      } else {
        window.swimmingGoogleIdentity.credentialHandler = credentialHandler
      }

      container.replaceChildren()
      window.google.accounts.id.renderButton(container, {
        theme: 'outline',
        size: 'large',
        text: 'signin_with',
        shape: 'rectangular',
        width: '320',
      })
    }

    const handleScriptError = () => setError('Google 로그인을 불러오지 못했습니다.')

    if (window.google) {
      render()
    } else {
      const script = document.getElementById('google-identity-services')
      script?.addEventListener('load', render, { once: true })
      script?.addEventListener('error', handleScriptError, { once: true })
    }

    const script = document.getElementById('google-identity-services')
    return () => {
      script?.removeEventListener('load', render)
      script?.removeEventListener('error', handleScriptError)
      if (window.swimmingGoogleIdentity?.credentialHandler === credentialHandler) {
        window.swimmingGoogleIdentity.credentialHandler = null
      }
    }
  }, [onSuccess])

  return (
    <div className={styles.login}>
      <div ref={containerRef} />
      {error && <p className={styles.error} role="alert">{error}</p>}
    </div>
  )
}
