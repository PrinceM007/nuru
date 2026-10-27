/**
 * Profile: account details, consent records, and identity verification
 * status. Verification is shown exactly as the backend reports it — never
 * "verified" unless a configured provider confirms it.
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { identity, users } from '../api/resources'
import { useAuth } from '../auth/AuthContext'
import { DateTime, Field, FormError, StatusPill, useAsync } from '../components/ui'

export function Profile(): React.JSX.Element {
  return (
    <div>
      <h1>Profile</h1>
      <ProfileCard />
      <ConsentCard />
      <VerificationCard />
    </div>
  )
}

function ProfileCard(): React.JSX.Element {
  const { profile, refreshProfile } = useAuth()
  const [displayName, setDisplayName] = useState(profile?.displayName ?? '')
  const [phone, setPhone] = useState(profile?.phone ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  if (!profile) return <p className="muted">Not signed in.</p>

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    if (!displayName.trim()) {
      setError('Enter the name other members will see.')
      return
    }
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await users.updateMe({ displayName: displayName.trim(), phone: phone.trim() || undefined })
      await refreshProfile()
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your profile.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" aria-label="Account details">
      <h2>Account details</h2>
      <form onSubmit={submit} noValidate>
        <Field label="Email">
          <input className="input" type="email" value={profile.email} disabled readOnly />
        </Field>
        <Field label="Full name" required>
          <input className="input" type="text" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={120} />
        </Field>
        <Field label="Phone (optional)" hint="Needed for SMS notifications.">
          <input className="input" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} maxLength={40} />
        </Field>
        <p className="muted small">
          Member since <DateTime iso={profile.createdAt} />
        </p>
        <FormError message={error} />
        {saved ? <p role="status">Profile saved.</p> : null}
        <button type="submit" className="btn btn-secondary" disabled={busy}>
          {busy ? 'Saving…' : 'Save profile'}
        </button>
      </form>
    </section>
  )
}

function ConsentCard(): React.JSX.Element {
  const consents = useAsync(() => users.consents(), [])
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const set = async (purpose: string, granted: boolean): Promise<void> => {
    setBusy(purpose)
    setError(null)
    try {
      await users.setConsent(purpose, granted)
      consents.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save consent.')
    } finally {
      setBusy(null)
    }
  }

  const granted = (purpose: string): boolean =>
    Object.values(consents.data ?? {}).some((c) => c.purpose === purpose && c.granted)

  return (
    <section className="card" aria-label="Consent">
      <h2>Consent</h2>
      <p className="muted small">
        Optional processing is off unless you allow it. Withdrawing consent stops future use.
      </p>
      {consents.loading ? <p className="muted">Loading…</p> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <ul className="list">
        <li>
          <strong>AI assistance</strong> — let the assistant read your authorized group information
          to answer questions and prepare actions for your review.
          <div className="row-actions">
            <span role="status">{granted('AI_ASSISTANCE') ? 'Allowed' : 'Not allowed'}</span>
            <button
              type="button"
              className="btn btn-ghost btn-small"
              disabled={busy === 'AI_ASSISTANCE'}
              onClick={() => set('AI_ASSISTANCE', !granted('AI_ASSISTANCE'))}
            >
              {granted('AI_ASSISTANCE') ? 'Withdraw' : 'Allow'}
            </button>
          </div>
        </li>
        <li>
          <strong>Identity verification</strong> — allow an identity check with the configured
          verification provider.
          <div className="row-actions">
            <span role="status">{granted('IDENTITY_VERIFICATION') ? 'Allowed' : 'Not allowed'}</span>
            <button
              type="button"
              className="btn btn-ghost btn-small"
              disabled={busy === 'IDENTITY_VERIFICATION'}
              onClick={() => set('IDENTITY_VERIFICATION', !granted('IDENTITY_VERIFICATION'))}
            >
              {granted('IDENTITY_VERIFICATION') ? 'Withdraw' : 'Allow'}
            </button>
          </div>
        </li>
      </ul>
    </section>
  )
}

function VerificationCard(): React.JSX.Element {
  const status = useAsync(() => identity.status(), [])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const run = async (fn: () => Promise<unknown>): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      status.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The verification service could not be reached.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" aria-label="Identity verification">
      <h2>Identity verification</h2>
      {status.loading ? <p className="muted">Loading…</p> : null}
      {status.error ? <p className="form-error" role="alert">{status.error}</p> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {status.data ? (
        <>
          <p>
            <StatusPill status={status.data.status} tone={status.data.status === 'VERIFIED' ? 'good' : 'neutral'} />
          </p>
          {status.data.status === 'UNAVAILABLE' ? (
            <p className="muted" role="status">
              Identity verification is not configured for this installation. No identity is
              verified here.
            </p>
          ) : null}
          {status.data.status === 'NOT_STARTED' ? (
            <p className="muted">You have not started verification.</p>
          ) : null}
          {status.data.status === 'VERIFIED' ? (
            <p className="muted">
              Confirmed by {status.data.provider}
              {status.data.verifiedAt ? <> on <DateTime iso={status.data.verifiedAt} /></> : null}.
              Nuru stores the provider’s reference and status only — never document contents.
            </p>
          ) : null}
          {status.data.failureReason ? <p className="muted">{status.data.failureReason}</p> : null}
          <div className="row-actions">
            {status.data.status === 'NOT_STARTED' || status.data.status === 'FAILED' || status.data.status === 'EXPIRED' ? (
              <button type="button" className="btn btn-secondary btn-small" disabled={busy} onClick={() => run(() => identity.start())}>
                Start verification
              </button>
            ) : null}
            {status.data.status === 'PENDING' || status.data.status === 'VERIFIED' ? (
              <button type="button" className="btn btn-ghost btn-small" disabled={busy} onClick={() => run(() => identity.refresh())}>
                Refresh status
              </button>
            ) : null}
          </div>
        </>
      ) : null}
    </section>
  )
}
