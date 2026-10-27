/**
 * Groups list: create a group, request to join, accept an invitation.
 * Detail (members, requests, invitations, roles, audit) lives in GroupDetail.
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { groups, membership } from '../api/resources'
import { useGroups } from '../groups/GroupContext'
import {
  EmptyState,
  ErrorState,
  Field,
  FormError,
  useAsync,
} from '../components/ui'

export function Groups(): React.JSX.Element {
  const { refresh: refreshContext, selectGroup } = useGroups()
  const [creating, setCreating] = useState(false)
  const list = useAsync(() => groups.list(0), [])

  return (
    <div>
      <div className="page-head">
        <h1>Groups</h1>
        <button type="button" className="btn btn-primary" onClick={() => setCreating((c) => !c)}>
          {creating ? 'Close' : 'Create group'}
        </button>
      </div>
      {creating ? (
        <CreateGroupForm
          onDone={(id) => {
            setCreating(false)
            list.reload()
            void refreshContext().then(() => selectGroup(id))
          }}
        />
      ) : null}
      <div className="card-grid">
        <section className="card" aria-label="Your groups">
          <h2>Your groups</h2>
          {list.loading ? (
            <p className="muted">Loading…</p>
          ) : list.error ? (
            <p className="form-error" role="alert">{list.error}</p>
          ) : list.data && list.data.content.length > 0 ? (
            <ul className="list">
              {list.data.content.map((g) => (
                <li key={g.id}>
                  <Link to={`/groups/${g.id}`} onClick={() => selectGroup(g.id)}>
                    {g.name}
                  </Link>
                  <br />
                  <span className="muted small">
                    {g.currency} · your role: {g.currentUserRole}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="No groups yet" hint="Create one above, or join with an invitation code." />
          )}
        </section>
        <JoinWithCode onJoined={() => { list.reload(); void refreshContext() }} />
      </div>
      {list.error && !list.data ? <ErrorState message={list.error} onRetry={list.reload} /> : null}
    </div>
  )
}

function CreateGroupForm({ onDone }: { onDone: (groupId: string) => void }): React.JSX.Element {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [currency, setCurrency] = useState('ZAR')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const errors: Record<string, string> = {}
    if (!name.trim()) errors.name = 'Give the group a name.'
    if (!/^[A-Za-z]{3}$/.test(currency.trim())) errors.currency = 'Use a 3-letter code such as ZAR.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    setBusy(true)
    setError(null)
    try {
      const group = await groups.create({
        name: name.trim(),
        description: description.trim() || undefined,
        currency: currency.trim().toUpperCase(),
      })
      onDone(group.id)
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setFieldErrors(err.fieldErrors)
      setError(err instanceof ApiError ? err.message : 'Could not create the group.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" aria-label="Create a group">
      <h2>Create a group</h2>
      <p className="muted">
        You become the group admin. The currency cannot be changed later, so choose carefully.
      </p>
      <form onSubmit={submit} noValidate>
        <Field label="Group name" error={fieldErrors.name} required>
          <input className="input" type="text" value={name} onChange={(e) => setName(e.target.value)} maxLength={160} />
        </Field>
        <Field label="Description (optional)" error={fieldErrors.description}>
          <textarea className="input" value={description} onChange={(e) => setDescription(e.target.value)} rows={3} maxLength={1000} />
        </Field>
        <Field label="Currency" hint="3-letter code, for example ZAR, NGN, KES." error={fieldErrors.currency} required>
          <input
            className="input input-short"
            type="text"
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            maxLength={3}
          />
        </Field>
        <FormError message={error} />
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Creating…' : 'Create group'}
        </button>
      </form>
    </section>
  )
}

function JoinWithCode({ onJoined }: { onJoined: () => void }): React.JSX.Element {
  const { selectGroup } = useGroups()
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    if (!code.trim()) {
      setError('Paste the invitation code your admin shared.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const result = await membership.acceptInvitation(code.trim())
      setCode('')
      onJoined()
      selectGroup(result.groupId)
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 404
          ? 'That code was not recognized. Check it and try again.'
          : err instanceof ApiError
            ? err.message
            : 'Could not accept the invitation.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" aria-label="Join with an invitation code">
      <h2>Join with a code</h2>
      <p className="muted">Admins share one-time codes that may expire or run out of uses.</p>
      <form onSubmit={submit} noValidate>
        <Field label="Invitation code" required>
          <input
            className="input"
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            autoComplete="off"
            spellCheck={false}
          />
        </Field>
        <FormError message={error} />
        <button type="submit" className="btn btn-secondary" disabled={busy}>
          {busy ? 'Joining…' : 'Join group'}
        </button>
      </form>
    </section>
  )
}
