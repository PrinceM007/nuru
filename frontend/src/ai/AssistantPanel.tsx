/**
 * Integrated AI assistant panel.
 *
 * Talks to the backend AI API only: reads execute immediately through the same
 * authorized services as the UI, and state-changing tools return PENDING
 * proposals that the user confirms or rejects here. Confirms call the
 * backend's confirm endpoint — success is reported only from its response.
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { ai, users } from '../api/resources'
import type { AiConversation, AiProposal, ConversationDetail, ExecutedTool } from '../api/types'
import { useGroups } from '../groups/GroupContext'
import { ConfirmDialog, ErrorState, LoadingState, StatusPill } from '../components/ui'
import { formatDateTime, humanize } from '../components/format'
import { useAssistant } from './AssistantContext'

interface TranscriptItem {
  key: string
  role: 'user' | 'assistant'
  content: string
  tools?: ExecutedTool[]
  proposals?: AiProposal[]
}

const CONVERSATION_KEY = 'nuru.aiConversationId'

export function AssistantPanel(): React.JSX.Element | null {
  const { open, setOpen } = useAssistant()
  const [statusChecked, setStatusChecked] = useState(false)
  const [available, setAvailable] = useState(false)
  const [statusDetail, setStatusDetail] = useState('')
  const [consentGranted, setConsentGranted] = useState<boolean | null>(null)
  const [statusError, setStatusError] = useState<string | null>(null)

  useEffect(() => {
    if (!open || statusChecked) return
    Promise.all([ai.status(), users.consents()])
      .then(([status, consents]) => {
        setAvailable(status.available)
        setStatusDetail(status.detail)
        setConsentGranted(consents.some((c) => c.purpose === 'AI_ASSISTANCE' && c.granted))
        setStatusChecked(true)
      })
      .catch((err: unknown) => {
        setStatusError(err instanceof ApiError ? err.message : 'Could not reach the assistant service.')
        setStatusChecked(true)
      })
  }, [open, statusChecked])

  if (!open) return null

  return (
    <div className="assistant-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setOpen(false)}>
      <section
        className="assistant-panel"
        role="dialog"
        aria-modal="true"
        aria-label="Nuru assistant"
      >
        <header className="assistant-header">
          <h2>Nuru assistant</h2>
          <button type="button" className="icon-btn" onClick={() => setOpen(false)} aria-label="Close assistant">
            ✕
          </button>
        </header>
        {!statusChecked ? (
          <LoadingState label="Checking assistant availability…" />
        ) : statusError ? (
          <ErrorState message={statusError} onRetry={() => setStatusChecked(false)} />
        ) : !available ? (
          <div className="state" role="status">
            <p className="state-title">Assistant unavailable</p>
            <p className="muted">
              {statusDetail || 'No AI provider is configured for this installation.'} You can still
              complete every task through the visual interface.
            </p>
          </div>
        ) : consentGranted === false ? (
          <ConsentPrompt onGranted={() => setConsentGranted(true)} />
        ) : (
          <AssistantChat />
        )}
      </section>
    </div>
  )
}

function ConsentPrompt({ onGranted }: { onGranted: () => void }): React.JSX.Element {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const grant = async (): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await users.setConsent('AI_ASSISTANCE', true)
      onGranted()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save consent.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="state">
      <p className="state-title">Consent needed</p>
      <p className="muted">
        The assistant reads your authorized group information to answer questions and prepares
        actions for your review. It never acts without your confirmation. You can withdraw this
        consent any time from your profile.
      </p>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <button type="button" className="btn btn-primary" onClick={grant} disabled={busy}>
        {busy ? 'Saving…' : 'Allow AI assistance'}
      </button>
    </div>
  )
}

function AssistantChat(): React.JSX.Element {
  const { selected } = useGroups()
  const { setDraft, setOpen } = useAssistant()
  const navigate = useNavigate()
  const [conversationId, setConversationId] = useState<string | null>(() =>
    localStorage.getItem(CONVERSATION_KEY),
  )
  const [transcript, setTranscript] = useState<TranscriptItem[]>([])
  const [pending, setPending] = useState<AiProposal[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState<AiProposal | null>(null)
  const [deciding, setDeciding] = useState(false)
  const [listening, setListening] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [conversations, setConversations] = useState<AiConversation[]>([])
  const logRef = useRef<HTMLDivElement>(null)

  const speechSupported =
    typeof window !== 'undefined' &&
    ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window)

  const refreshPending = useCallback(async () => {
    try {
      const list = await ai.proposals('PENDING')
      setPending(list)
    } catch {
      // Pending proposals are supplementary; the chat still works.
    }
  }, [])

  // Load history for the stored conversation, if any.
  useEffect(() => {
    setLoadingHistory(true)
    const load = async (): Promise<void> => {
      try {
        if (conversationId) {
          const detail: ConversationDetail = await ai.conversation(conversationId)
          setTranscript(
            detail.messages.map((m) => ({
              key: `h-${m.id}`,
              role: m.role === 'USER' ? 'user' : 'assistant',
              content: m.content,
            })),
          )
        }
      } catch {
        // A deleted or expired conversation just starts fresh.
        localStorage.removeItem(CONVERSATION_KEY)
        setConversationId(null)
        setTranscript([])
      } finally {
        setLoadingHistory(false)
      }
    }
    void load()
    void refreshPending()
  }, [conversationId, refreshPending])

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight })
  }, [transcript])

  const startNewConversation = (): void => {
    localStorage.removeItem(CONVERSATION_KEY)
    setConversationId(null)
    setTranscript([])
    setError(null)
  }

  const send = async (): Promise<void> => {
    const text = input.trim()
    if (!text || sending) return
    setSending(true)
    setError(null)
    setInput('')
    setTranscript((t) => [...t, { key: `u-${Date.now()}`, role: 'user', content: text }])
    try {
      let id = conversationId
      if (!id) {
        const created = await ai.createConversation(selected?.id, text.slice(0, 80))
        id = created.id
        localStorage.setItem(CONVERSATION_KEY, id)
        setConversationId(id)
      }
      const response = await ai.sendMessage(id, text)
      setTranscript((t) => [
        ...t,
        {
          key: `a-${Date.now()}`,
          role: 'assistant',
          content: response.assistantMessage,
          tools: response.executedTools,
          proposals: response.proposals,
        },
      ])
      await refreshPending()
    } catch (err) {
      if (err instanceof ApiError && err.status === 503) {
        setError(
          'The AI provider is unavailable right now. Your message was not processed — please retry later or use the visual interface.',
        )
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not send your message.')
      }
    } finally {
      setSending(false)
    }
  }

  const confirmProposal = async (proposal: AiProposal): Promise<void> => {
    setDeciding(true)
    try {
      const result = await ai.confirmProposal(proposal.id)
      setConfirming(null)
      setTranscript((t) => [
        ...t,
        {
          key: `c-${Date.now()}`,
          role: 'assistant',
          content: result.resultSummary ?? `Confirmed: ${result.description}`,
        },
      ])
      await refreshPending()
    } catch (err) {
      setConfirming(null)
      setTranscript((t) => [
        ...t,
        {
          key: `c-${Date.now()}`,
          role: 'assistant',
          content: `That action could not be completed: ${err instanceof ApiError ? err.message : 'unknown error'}. Nothing was changed.`,
        },
      ])
      await refreshPending()
    } finally {
      setDeciding(false)
    }
  }

  const rejectProposal = async (proposal: AiProposal): Promise<void> => {
    setDeciding(true)
    try {
      await ai.rejectProposal(proposal.id)
      await refreshPending()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reject the proposal.')
    } finally {
      setDeciding(false)
    }
  }

  /** Route a proposal to its visual form with details preserved. */
  const editInForm = (proposal: AiProposal): void => {
    const args = proposal.args ?? {}
    const str = (v: unknown): string => (v === null || v === undefined ? '' : String(v))
    if (proposal.tool === 'meeting_create') {
      setDraft({
        form: 'meeting',
        values: {
          title: str(args.title),
          agenda: str(args.agenda),
          startsAt: str(args.startsAt),
          endsAt: str(args.endsAt),
          timezone: str(args.timezone),
          location: str(args.location),
          meetingLink: str(args.meetingLink),
        },
      })
      setOpen(false)
      navigate('/meetings')
    } else if (proposal.tool === 'statement_request') {
      setDraft({
        form: 'statement',
        values: {
          requestType: str(args.requestType),
          periodStart: str(args.periodStart),
          periodEnd: str(args.periodEnd),
        },
      })
      setOpen(false)
      navigate('/statements')
    } else if (proposal.tool === 'loan_request') {
      setDraft({
        form: 'loan',
        values: {
          principal: str(args.principal),
          interestRate: str(args.interestRate),
          termMonths: str(args.termMonths),
          purpose: str(args.purpose),
        },
      })
      setOpen(false)
      navigate('/loans')
    } else if (proposal.tool === 'vote_create') {
      setDraft({
        form: 'vote',
        values: {
          question: str(args.question),
          description: str(args.description),
          ballotPolicy: str(args.ballotPolicy),
          opensAt: str(args.opensAt),
          closesAt: str(args.closesAt),
          quorum: str(args.quorum),
          options: Array.isArray(args.options) ? args.options.map(String).join('\n') : str(args.options),
        },
      })
      setOpen(false)
      navigate('/votes')
    }
  }

  const toggleListening = (): void => {
    if (listening) {
      stopRecognition()
      return
    }
    const Recognition =
      (window as unknown as Record<string, new () => SpeechRecognitionLike>).SpeechRecognition ??
      (window as unknown as Record<string, new () => SpeechRecognitionLike>).webkitSpeechRecognition
    if (!Recognition) return
    const recognition = new Recognition()
    recognition.lang = 'en'
    recognition.interimResults = false
    recognition.onresult = (event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => {
      const transcriptText = event.results[0]?.[0]?.transcript ?? ''
      if (transcriptText) setInput((prev) => (prev ? `${prev} ${transcriptText}` : transcriptText))
    }
    recognition.onend = () => setListening(false)
    recognition.onerror = () => setListening(false)
    recognitionRef.current = recognition
    recognition.start()
    setListening(true)
  }

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null)
  const stopRecognition = (): void => {
    try {
      recognitionRef.current?.stop()
    } catch {
      // Already stopped.
    }
    setListening(false)
  }

  useEffect(() => () => stopRecognition(), [])

  const openHistory = async (): Promise<void> => {
    setShowHistory((s) => !s)
    if (!showHistory) {
      try {
        const page = await ai.conversations(0)
        setConversations(page.content)
      } catch {
        setConversations([])
      }
    }
  }

  if (loadingHistory) return <LoadingState label="Loading conversation…" />

  return (
    <div className="assistant-chat">
      <div className="assistant-context muted" role="status">
        {selected ? (
          <>
            Using group <strong>{selected.name}</strong> ({selected.currency}) · your role{' '}
            {selected.currentUserRole}
          </>
        ) : (
          'No group selected — answers are limited to general help.'
        )}
      </div>

      {pending.length > 0 ? (
        <section aria-label="Proposals awaiting your confirmation" className="proposal-list">
          <h3>Awaiting your confirmation ({pending.length})</h3>
          {pending.map((p) => (
            <ProposalCard
              key={p.id}
              proposal={p}
              busy={deciding}
              onConfirm={() => setConfirming(p)}
              onReject={() => rejectProposal(p)}
              onEdit={
                ['meeting_create', 'statement_request', 'loan_request', 'vote_create'].includes(p.tool)
                  ? () => editInForm(p)
                  : undefined
              }
            />
          ))}
        </section>
      ) : null}

      <div className="chat-log" ref={logRef} aria-live="polite" aria-label="Conversation">
        {transcript.length === 0 ? (
          <div className="state">
            <p className="muted">
              Ask about your contributions, meetings, or statements — or ask me to prepare an action
              such as a meeting draft. I will always show it for your review first.
            </p>
          </div>
        ) : null}
        {transcript.map((item) => (
          <article key={item.key} className={`chat-message chat-${item.role}`}>
            <p className="chat-role">{item.role === 'user' ? 'You' : 'Assistant'}</p>
            <p className="chat-content">{item.content}</p>
            {item.tools?.map((t, i) => (
              <details key={i} className="chat-tool">
                <summary>
                  Used: {humanize(t.tool)} — {t.summary}
                </summary>
                <dl className="kv">
                  {Object.entries(t.facts ?? {}).map(([k, v]) => (
                    <div key={k} className="kv-row">
                      <dt>{humanize(k)}</dt>
                      <dd>{String(v ?? '—')}</dd>
                    </div>
                  ))}
                </dl>
              </details>
            ))}
            {item.proposals?.map((p) =>
              p.status === 'PENDING' ? (
                <ProposalCard
                  key={p.id}
                  proposal={p}
                  busy={deciding}
                  onConfirm={() => setConfirming(p)}
                  onReject={() => rejectProposal(p)}
                  onEdit={
                    ['meeting_create', 'statement_request', 'loan_request', 'vote_create'].includes(p.tool)
                      ? () => editInForm(p)
                      : undefined
                  }
                />
              ) : (
                <p key={p.id} className="muted">
                  Proposal {humanize(p.status).toLowerCase()}
                  {p.resultSummary ? `: ${p.resultSummary}` : ''}
                </p>
              ),
            )}
          </article>
        ))}
        {sending ? <p className="muted">Assistant is thinking…</p> : null}
      </div>

      {error ? (
        <p className="form-error" role="alert">
          {error}
        </p>
      ) : null}

      <form
        className="chat-input"
        onSubmit={(e) => {
          e.preventDefault()
          void send()
        }}
      >
        <label className="visually-hidden" htmlFor="assistant-text">
          Message the assistant
        </label>
        <input
          id="assistant-text"
          className="input"
          type="text"
          value={input}
          maxLength={4000}
          onChange={(e) => setInput(e.target.value)}
          placeholder={listening ? 'Listening… speak now' : 'Type your message…'}
          disabled={sending}
          autoComplete="off"
        />
        {speechSupported ? (
          <button
            type="button"
            className={`icon-btn${listening ? ' icon-btn-active' : ''}`}
            onClick={toggleListening}
            aria-label={listening ? 'Stop voice input' : 'Start voice input'}
            title="Voice input uses your browser's speech recognition. The transcript appears above for your review before anything is sent; audio is not stored by Nuru."
          >
            {listening ? '■' : '🎙'}
          </button>
        ) : null}
        <button type="submit" className="btn btn-primary" disabled={sending || !input.trim()} aria-label="Send message">
          {sending ? '…' : 'Send'}
        </button>
      </form>
      <div className="assistant-footer">
        <button type="button" className="btn btn-ghost btn-small" onClick={startNewConversation}>
          New conversation
        </button>
        <button type="button" className="btn btn-ghost btn-small" onClick={() => void openHistory()}>
          {showHistory ? 'Hide history' : 'History'}
        </button>
      </div>
      {showHistory ? (
        <ul className="assistant-history">
          {conversations.length === 0 ? (
            <li className="muted">No past conversations.</li>
          ) : (
            conversations.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => {
                    localStorage.setItem(CONVERSATION_KEY, c.id)
                    setConversationId(c.id)
                    setShowHistory(false)
                  }}
                >
                  {c.title || 'Untitled conversation'} · {formatDateTime(c.updatedAt)}
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}

      {confirming ? (
        <ConfirmDialog
          title="Confirm this action"
          confirmLabel="Confirm and submit"
          onConfirm={() => confirmProposal(confirming)}
          onCancel={() => setConfirming(null)}
          busy={deciding}
        >
          <p>
            <strong>{confirming.description}</strong>
          </p>
          <dl className="kv">
            {Object.entries(confirming.args ?? {}).map(([k, v]) => (
              <div key={k} className="kv-row">
                <dt>{humanize(k)}</dt>
                <dd>{String(v ?? '—')}</dd>
              </div>
            ))}
          </dl>
          <p className="muted">
            The backend re-checks your permission and validates every detail before anything
            changes. Expires {formatDateTime(confirming.expiresAt)}.
          </p>
        </ConfirmDialog>
      ) : null}
    </div>
  )
}

export function ProposalCard({
  proposal,
  busy,
  onConfirm,
  onReject,
  onEdit,
}: {
  proposal: AiProposal
  busy: boolean
  onConfirm: () => void
  onReject: () => void
  onEdit?: () => void
}): React.JSX.Element {
  return (
    <div className="proposal-card">
      <div className="proposal-head">
        <StatusPill status={proposal.status} />
        <span className="muted">Proposed action · {humanize(proposal.tool)}</span>
      </div>
      <p>
        <strong>{proposal.description}</strong>
      </p>
      <dl className="kv">
        {Object.entries(proposal.args ?? {}).map(([k, v]) => (
          <div key={k} className="kv-row">
            <dt>{humanize(k)}</dt>
            <dd>{Array.isArray(v) ? v.map(String).join(', ') : String(v ?? '—')}</dd>
          </div>
        ))}
      </dl>
      <div className="proposal-actions">
        <button type="button" className="btn btn-primary btn-small" onClick={onConfirm} disabled={busy}>
          Review & confirm
        </button>
        {onEdit ? (
          <button type="button" className="btn btn-secondary btn-small" onClick={onEdit} disabled={busy}>
            Edit in form
          </button>
        ) : null}
        <button type="button" className="btn btn-ghost btn-small" onClick={onReject} disabled={busy}>
          Reject
        </button>
      </div>
    </div>
  )
}

/** Minimal typing for the browser SpeechRecognition API (used only if present). */
interface SpeechRecognitionLike {
  lang: string
  interimResults: boolean
  onresult: ((event: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null
  onend: (() => void) | null
  onerror: (() => void) | null
  start: () => void
  stop: () => void
}
