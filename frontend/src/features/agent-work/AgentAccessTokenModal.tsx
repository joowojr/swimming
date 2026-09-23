import { useEffect, useRef, useState } from 'react'
import { IconCheck, IconCopy, IconKey, IconLink, IconTrash, IconX } from '@tabler/icons-react'
import type { ApiError } from '../../api/client'
import ActionButton from '../../components/ActionButton'
import modalStyles from '../../components/ModalShell.module.css'
import {
  createAgentAccessToken,
  getAgentAccessTokens,
  revokeAgentAccessToken,
} from './agentWorkApi'
import type { AgentAccessToken, IssuedAgentAccessToken } from './agentWorkTypes'
import styles from './AgentAccessTokenModal.module.css'

interface AgentAccessTokenModalProps {
  onClose: () => void
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null
}

function formatDate(value: string | null) {
  if (!value) return '만료 없음'
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' }).format(new Date(value))
}

export default function AgentAccessTokenModal({ onClose }: AgentAccessTokenModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [tokens, setTokens] = useState<AgentAccessToken[]>([])
  const [name, setName] = useState('내 코딩 에이전트')
  const [expiresInDays, setExpiresInDays] = useState('30')
  const [issued, setIssued] = useState<IssuedAgentAccessToken | null>(null)
  const [copied, setCopied] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    dialogRef.current?.showModal()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    void getAgentAccessTokens()
      .then(setTokens)
      .catch(() => setErrorMessage('토큰 목록을 불러오지 못했어요.'))
      .finally(() => setIsLoading(false))
    return () => { document.body.style.overflow = previousOverflow }
  }, [])

  const close = () => {
    if (!isSubmitting) dialogRef.current?.close()
  }

  const issueToken = async () => {
    const trimmedName = name.trim()
    if (!trimmedName) {
      setErrorMessage('토큰 이름을 입력해 주세요.')
      return
    }
    setIsSubmitting(true)
    setErrorMessage(null)
    setCopied(false)
    try {
      const result = await createAgentAccessToken({
        name: trimmedName,
        ...(expiresInDays ? { expiresInDays: Number(expiresInDays) } : {}),
      })
      setIssued(result)
      setTokens(await getAgentAccessTokens())
    } catch (error) {
      setErrorMessage(isApiError(error) && error.message ? error.message : '토큰을 발급하지 못했어요.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const copyToken = async () => {
    if (!issued) return
    await navigator.clipboard.writeText(issued.token)
    setCopied(true)
  }

  const revoke = async (token: AgentAccessToken) => {
    if (token.revokedAt) return
    try {
      await revokeAgentAccessToken(token.id)
      setTokens((current) => current.map((item) => item.id === token.id
        ? { ...item, revokedAt: new Date().toISOString() } : item))
    } catch (error) {
      setErrorMessage(isApiError(error) && error.message ? error.message : '토큰을 폐기하지 못했어요.')
    }
  }

  return (
    <dialog ref={dialogRef} className={`${styles.dialog} ${modalStyles.dialog}`} onClose={onClose}>
      <section className={`${styles.modal} ${modalStyles.surface}`} aria-labelledby="agent-token-title">
        <header className={`${styles.header} ${modalStyles.header}`}>
          <div>
            <p className={styles.eyebrow}><IconKey size={14} aria-hidden="true" /> Agent Connector</p>
            <h2 id="agent-token-title">에이전트 연결</h2>
            <p>Codex와 Claude Code가 Swimming의 작업을 안전하게 보고할 수 있어요.</p>
          </div>
          <button type="button" className={styles.close} aria-label="에이전트 연결 창 닫기" onClick={close}>
            <IconX size={20} aria-hidden="true" />
          </button>
        </header>

        <div className={styles.body}>
          {issued ? (
            <section className={styles.issued} aria-live="polite">
              <div className={styles.issuedHeading}><IconCheck size={18} aria-hidden="true" /><strong>토큰이 발급됐어요</strong></div>
              <p>이 토큰은 지금 한 번만 보여요. 복사해 CLI Connector 설정에 붙여 넣으세요.</p>
              <div className={styles.issuedTokenRow}>
                <ActionButton
                  className={styles.copyButton}
                  variant="outline"
                  icon={copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                  aria-label={copied ? '토큰 복사됨' : '토큰 복사'}
                  title={copied ? '토큰 복사됨' : '토큰 복사'}
                  onClick={() => void copyToken()}
                />
                <code className={styles.token}>{issued.token}</code>
              </div>
              <div className={styles.connectorHint}><IconLink size={16} aria-hidden="true" /><span>Connector 설정에서 이 토큰을 사용하면 작업 시작·완료 보고가 보드에 반영됩니다.</span></div>
            </section>
          ) : (
            <section className={styles.formSection}>
              <div className={styles.sectionTitle}><span className={styles.step}>01</span><div><strong>연결 이름</strong><p>나중에 어떤 기기에서 발급했는지 알아보기 쉽게 적어 주세요.</p></div></div>
              <input className={styles.input} value={name} maxLength={100} onChange={(event) => setName(event.target.value)} placeholder="예: MacBook Codex" />
              <label className={styles.label} htmlFor="agent-token-expiry">만료 기간</label>
              <select id="agent-token-expiry" className={styles.input} value={expiresInDays} onChange={(event) => setExpiresInDays(event.target.value)}>
                <option value="30">30일</option><option value="90">90일</option><option value="">만료 없음</option>
              </select>
              <ActionButton className={styles.submit} isLoading={isSubmitting} loadingLabel="발급 중…" icon={<IconKey size={16} />} onClick={() => void issueToken()}>토큰 발급</ActionButton>
            </section>
          )}

          <section className={styles.existing}>
            <div className={styles.sectionHeading}><div><strong>발급된 토큰</strong><span> {tokens.length}</span></div></div>
            {isLoading ? <p className={styles.muted}>토큰 목록을 불러오는 중…</p> : tokens.length === 0 ? <p className={styles.muted}>아직 연결된 에이전트가 없어요.</p> : (
              <ul className={styles.tokenList}>
                {tokens.map((token) => (
                  <li key={token.id} className={styles.tokenRow}>
                    <div>
                      <strong>{token.name}</strong>
                      <span className={styles.tokenSummary}>
                        <span className={styles.tokenPrefix}>{token.tokenPrefix.slice(0, 8)}</span>
                        <span className={styles.tokenMask}>••••••••</span>
                        <span className={styles.tokenSuffix}>{token.tokenSuffix}</span>
                      </span>
                      <span className={styles.tokenExpiry}>{formatDate(token.expiresAt)}</span>
                    </div>
                    {token.revokedAt ? <em>폐기됨</em> : <button type="button" className={styles.revoke} onClick={() => void revoke(token)}><IconTrash size={15} aria-hidden="true" /></button>}
                  </li>
                ))}
              </ul>
            )}
          </section>
          {errorMessage && <p className={styles.error} role="alert">{errorMessage}</p>}
        </div>
      </section>
    </dialog>
  )
}
