/**
 * Reusable monochrome UI primitives: status labels (never color alone),
 * loading/empty/error states, accessible dialogs, form fields, pagination.
 */
import { useEffect, useId, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../api/client'
import { formatDateTime, formatMoney, humanize } from './format'

/* ---------- tiny data-fetch hook ---------- */

export interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
  reload: () => void
}

export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [nonce, setNonce] = useState(0)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    loader()
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : 'Something went wrong.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])
  return { data, loading, error, reload: () => setNonce((n) => n + 1) }
}

/* ---------- status ---------- */

type Tone = 'neutral' | 'good' | 'warn' | 'bad' | 'info'

const TONE_ICON: Record<Tone, string> = {
  neutral: '○',
  good: '●',
  warn: '▲',
  bad: '■',
  info: '◆',
}

/** Status label with text + icon so meaning never depends on color alone. */
export function StatusPill({ status, tone }: { status: string; tone?: Tone }): React.JSX.Element {
  const resolved: Tone = tone ?? toneForStatus(status)
  return (
    <span className={`pill pill-${resolved}`}>
      <span aria-hidden="true" className="pill-icon">
        {TONE_ICON[resolved]}
      </span>{' '}
      {humanize(status)}
    </span>
  )
}

function toneForStatus(status: string): Tone {
  switch (status) {
    case 'CONFIRMED':
    case 'APPROVED':
    case 'VERIFIED':
    case 'DELIVERED':
    case 'SENT':
    case 'ACTIVE':
    case 'REPAID':
    case 'PRESENT':
    case 'PUBLISHED':
    case 'OPEN':
      return 'good'
    case 'EXPECTED':
    case 'SUBMITTED':
    case 'PENDING':
    case 'REQUESTED':
    case 'SCHEDULED':
    case 'INVITED':
    case 'QUEUED':
    case 'SIMULATED':
      return 'warn'
    case 'REJECTED':
    case 'FAILED':
    case 'CANCELLED':
    case 'DEFAULTED':
    case 'ABSENT':
    case 'EXPIRED':
    case 'CLOSED':
      return 'bad'
    default:
      return 'neutral'
  }
}

/* ---------- states ---------- */

export function LoadingState({ label = 'Loading…' }: { label?: string }): React.JSX.Element {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <p>{label}</p>
    </div>
  )
}

export function EmptyState({
  title,
  hint,
  action,
}: {
  title: string
  hint?: string
  action?: ReactNode
}): React.JSX.Element {
  return (
    <div className="state">
      <p className="state-title">{title}</p>
      {hint ? <p className="muted">{hint}</p> : null}
      {action}
    </div>
  )
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string
  onRetry?: () => void
}): React.JSX.Element {
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">Something went wrong</p>
      <p className="muted">{message}</p>
      {onRetry ? (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  )
}

/** Shown when the backend answers 403 for the current user. */
export function AccessDenied({ hint }: { hint?: string }): React.JSX.Element {
  return (
    <div className="state" role="alert">
      <p className="state-title">Access denied</p>
      <p className="muted">
        {hint ?? 'You do not have permission to view this. Ask a group admin if you need access.'}
      </p>
    </div>
  )
}

/** Shown when a provider/integration is not configured (backend 503). */
export function UnavailableState({ title, detail }: { title: string; detail?: string }): React.JSX.Element {
  return (
    <div className="state" role="status">
      <p className="state-title">{title}</p>
      <p className="muted">
        {detail ?? 'This service is not configured for this installation yet.'}
      </p>
    </div>
  )
}

/* ---------- forms ---------- */

export function Field({
  label,
  error,
  hint,
  children,
  required,
}: {
  label: string
  error?: string
  hint?: string
  children: ReactNode
  required?: boolean
}): React.JSX.Element {
  const id = useId()
  const errorId = `${id}-error`
  const hintId = `${id}-hint`
  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {hint ? (
        <p className="field-hint" id={hintId}>
          {hint}
        </p>
      ) : null}
      <div
        // Associate the input with its label, hint, and error.
        ref={(node) => {
          const input = node?.querySelector('input, select, textarea')
          if (input) {
            input.id = id
            input.setAttribute('aria-describedby', `${hint ? hintId : ''} ${error ? errorId : ''}`.trim())
            if (error) input.setAttribute('aria-invalid', 'true')
          }
        }}
      >
        {children}
      </div>
      {error ? (
        <p className="field-error" id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}

export function FormError({ message }: { message: string | null }): React.JSX.Element | null {
  if (!message) return null
  return (
    <p className="form-error" role="alert">
      {message}
    </p>
  )
}

/* ---------- dialog ---------- */

export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  onConfirm,
  onCancel,
  busy,
  danger,
}: {
  title: string
  children: ReactNode
  confirmLabel: string
  onConfirm: () => void
  onCancel: () => void
  busy?: boolean
  danger?: boolean
}): React.JSX.Element {
  const dialogRef = useRef<HTMLDivElement>(null)
  const previouslyFocused = useRef<Element | null>(null)

  useEffect(() => {
    previouslyFocused.current = document.activeElement
    dialogRef.current?.querySelector<HTMLButtonElement>('button')?.focus()
    const onKey = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') onCancel()
      if (event.key === 'Tab') {
        // Simple focus trap.
        const buttons = dialogRef.current?.querySelectorAll('button, input, select, textarea, a[href]') ?? []
        if (buttons.length === 0) return
        const first = buttons[0] as HTMLElement
        const last = buttons[buttons.length - 1] as HTMLElement
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      if (previouslyFocused.current instanceof HTMLElement) previouslyFocused.current.focus()
    }
  }, [onCancel])

  return (
    <div className="dialog-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onCancel()}>
      <div
        ref={dialogRef}
        className="dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
      >
        <h2 id="confirm-title" className="dialog-title">
          {title}
        </h2>
        <div className="dialog-body">{children}</div>
        <div className="dialog-actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className={danger ? 'btn btn-danger' : 'btn btn-primary'}
            onClick={onConfirm}
            disabled={busy}
          >
            {busy ? 'Working…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

/* ---------- pagination ---------- */

export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number
  totalPages: number
  totalElements: number
  onPage: (page: number) => void
}): React.JSX.Element | null {
  if (totalPages <= 1) return null
  return (
    <nav className="pagination" aria-label="Pagination">
      <button
        type="button"
        className="btn btn-secondary btn-small"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
      >
        ← Previous
      </button>
      <span className="muted" role="status">
        Page {page + 1} of {totalPages} · {totalElements} items
      </span>
      <button
        type="button"
        className="btn btn-secondary btn-small"
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
      >
        Next →
      </button>
    </nav>
  )
}

/* ---------- display helpers ---------- */

export function Money({ amount, currency }: { amount: number; currency: string }): React.JSX.Element {
  return <span className="money">{formatMoney(amount, currency)}</span>
}

export function DateTime({ iso }: { iso: string | null | undefined }): React.JSX.Element {
  return <time dateTime={iso ?? undefined}>{formatDateTime(iso)}</time>
}
