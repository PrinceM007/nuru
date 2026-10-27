/**
 * Notifications: the user's own notification history with honest delivery
 * states. Success is shown only when the backend/provider reports it.
 */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { notifications, users } from '../api/resources'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime, humanize } from '../components/format'
import {
  EmptyState,
  Field,
  FormError,
  Pagination,
  StatusPill,
  useAsync,
} from '../components/ui'
import { ApiError } from '../api/client'

export function Notifications(): React.JSX.Element {
  const [page, setPage] = useState(0)
  const list = useAsync(() => notifications.mine(page), [page])
  const prefs = useAsync(() => users.preferences(), [])

  return (
    <div>
      <h1>Notifications</h1>
      <section className="card" aria-label="Notification preferences">
        <h2>Your preferences</h2>
        {prefs.loading ? <p className="muted">Loading…</p> : null}
        {prefs.error ? <p className="form-error" role="alert">{prefs.error}</p> : null}
        {prefs.data ? <PreferencesForm key={`${prefs.data.emailEnabled}-${prefs.data.smsEnabled}-${prefs.data.locale}`} initial={prefs.data} onSaved={prefs.reload} /> : null}
      </section>
      <section className="card" aria-label="Notification history">
        <h2>History</h2>
        <p className="muted small">
          Message contents are never stored — only the subject, channel, and delivery outcome.
        </p>
        {list.loading ? <p className="muted">Loading…</p> : null}
        {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
        {list.data && list.data.content.length === 0 && !list.loading ? (
          <EmptyState title="No notifications yet" hint="Reminders and updates will appear here." />
        ) : null}
        <ul className="list">
          {(list.data?.content ?? []).map((n) => (
            <li key={n.id}>
              <strong>{n.subject}</strong> <StatusPill status={n.status} />
              <br />
              <span className="muted small">
                {humanize(n.channel)} · {humanize(n.kind)} · {formatDateTime(n.createdAt)}
                {' '}· {deliveryNote(n.status, n.provider, n.errorDetail)}
              </span>
            </li>
          ))}
        </ul>
        {list.data ? (
          <Pagination page={list.data.number} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
        ) : null}
      </section>
    </div>
  )
}

function deliveryNote(status: string, provider: string, errorDetail: string | null): string {
  switch (status) {
    case 'DELIVERED':
      return `confirmed delivered by ${provider}`
    case 'SENT':
      return `handed to ${provider}; delivery not confirmed`
    case 'SIMULATED':
      return 'simulated by the development adapter — nothing was actually sent'
    case 'SKIPPED':
      return 'skipped (channel disabled or missing phone number)'
    case 'QUEUED':
      return 'queued for delivery'
    case 'FAILED':
      return `failed${errorDetail ? `: ${errorDetail}` : ''}`
    default:
      return provider
  }
}

function PreferencesForm({
  initial,
  onSaved,
}: {
  initial: { emailEnabled: boolean; smsEnabled: boolean; locale: string }
  onSaved: () => void
}): React.JSX.Element {
  const { profile } = useAuth()
  const [emailEnabled, setEmailEnabled] = useState(initial.emailEnabled)
  const [smsEnabled, setSmsEnabled] = useState(initial.smsEnabled)
  const [locale, setLocale] = useState(initial.locale)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const submit = async (e: React.FormEvent): Promise<void> => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await users.updatePreferences({ emailEnabled, smsEnabled, locale: locale.trim() || undefined })
      setSaved(true)
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save preferences.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <label className="check">
        <input type="checkbox" checked={emailEnabled} onChange={(e) => setEmailEnabled(e.target.checked)} />
        Email notifications
      </label>
      <label className="check">
        <input type="checkbox" checked={smsEnabled} onChange={(e) => setSmsEnabled(e.target.checked)} />
        SMS notifications{profile?.phone ? '' : ' (add a phone number in your profile first)'}
      </label>
      <Field label="Locale">
        <input className="input input-short" type="text" value={locale} onChange={(e) => setLocale(e.target.value)} maxLength={10} />
      </Field>
      <FormError message={error} />
      {saved ? <p role="status">Preferences saved.</p> : null}
      <button type="submit" className="btn btn-secondary btn-small" disabled={busy}>
        {busy ? 'Saving…' : 'Save preferences'}
      </button>
      <p className="muted small">
        <Link to="/profile">Manage consent</Link> for AI assistance and identity verification in your profile.
      </p>
    </form>
  )
}
