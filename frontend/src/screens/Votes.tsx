/**
 * Votes: open/upcoming/closed lists, creation with quorum and ballot policy,
 * one-vote-per-member casting with confirmation, and published results.
 * Secret ballots never show voter identities (the API never sends them).
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { votes } from '../api/resources'
import type { VoteInput } from '../api/resources'
import { useFormDraft } from '../ai/AssistantContext'
import { useGroups, useSelectedGroup } from '../groups/GroupContext'
import { isoToLocalInput, localInputToIso } from '../components/format'
import {
  ConfirmDialog,
  DateTime,
  EmptyState,
  ErrorState,
  Field,
  FormError,
  LoadingState,
  Pagination,
  StatusPill,
  useAsync,
} from '../components/ui'

export function Votes(): React.JSX.Element {
  const { selected, loading } = useGroups()
  const draft = useFormDraft('vote')
  const [creating, setCreating] = useState(false)

  if (loading) return <LoadingState label="Loading…" />
  if (!selected) return <EmptyState title="Choose a group first" hint="Votes belong to a savings group." />
  const open = creating || draft !== null
  return (
    <div>
      <VotesList key={selected.id} creating={open} setCreating={setCreating} prefill={draft} />
    </div>
  )
}

function VotesList({
  creating,
  setCreating,
  prefill,
}: {
  creating: boolean
  setCreating: (v: boolean) => void
  prefill: Record<string, string> | null
}): React.JSX.Element {
  const { group, can } = useSelectedGroup()
  const [page, setPage] = useState(0)
  const list = useAsync(() => votes.list(group.id, page), [group.id, page])
  const content = list.data?.content ?? []
  const open = content.filter((v) => v.status === 'OPEN')
  const upcoming = content.filter((v) => v.status === 'DRAFT')
  const closed = content.filter((v) => v.status === 'CLOSED' || v.status === 'PUBLISHED')

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Votes</h1>
          <p className="muted">{group.name}</p>
        </div>
        {can('VOTES_MANAGE') ? (
          <button type="button" className="btn btn-primary" onClick={() => setCreating(!creating)}>
            {creating ? 'Close' : 'Create vote'}
          </button>
        ) : null}
      </div>
      {creating && can('VOTES_MANAGE') ? (
        <VoteForm prefill={prefill} onDone={() => { setCreating(false); list.reload() }} />
      ) : null}
      {list.loading ? <p className="muted">Loading…</p> : null}
      {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
      <VoteSection title="Open votes" hint="Cast your vote before the closing time." votes={open} empty="No votes are open." />
      <VoteSection title="Upcoming (draft)" hint="Not open yet — eligibility freezes when voting opens." votes={upcoming} empty="No upcoming votes." />
      <VoteSection title="Closed and published" hint="Results appear only after publication." votes={closed} empty="No closed votes." />
      {list.data ? (
        <Pagination page={list.data.number} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
      ) : null}
    </div>
  )
}

function VoteSection({
  title,
  hint,
  votes: items,
  empty,
}: {
  title: string
  hint: string
  votes: { id: string; question: string; status: string; closesAt: string; voted: boolean }[]
  empty: string
}): React.JSX.Element {
  return (
    <section className="card" aria-label={title}>
      <h2>{title}</h2>
      <p className="muted small">{hint}</p>
      {items.length === 0 ? <p className="muted">{empty}</p> : null}
      <ul className="list">
        {items.map((v) => (
          <li key={v.id}>
            <Link to={`/votes/${v.id}`}>{v.question}</Link> <StatusPill status={v.status} />
            <br />
            <span className="muted small">
              Closes <DateTime iso={v.closesAt} /> · {v.voted ? 'You voted' : 'You have not voted'}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function VoteForm({ prefill, onDone }: { prefill: Record<string, string> | null; onDone: () => void }): React.JSX.Element {
  const { group } = useSelectedGroup()
  const [question, setQuestion] = useState(prefill?.question ?? '')
  const [description, setDescription] = useState(prefill?.description ?? '')
  const [ballotPolicy, setBallotPolicy] = useState(prefill?.ballotPolicy || 'SECRET')
  const [opensAt, setOpensAt] = useState(prefill?.opensAt ? safeLocal(prefill.opensAt) : '')
  const [closesAt, setClosesAt] = useState(prefill?.closesAt ? safeLocal(prefill.closesAt) : '')
  const [quorum, setQuorum] = useState(prefill?.quorum ?? '')
  const [optionsText, setOptionsText] = useState(prefill?.options ?? '')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reviewing, setReviewing] = useState(false)

  const parsed = (): VoteInput | null => {
    const errors: Record<string, string> = {}
    if (!question.trim()) errors.question = 'Write the question members will vote on.'
    if (!opensAt) errors.opensAt = 'Choose when voting opens.'
    if (!closesAt) errors.closesAt = 'Choose when voting closes.'
    if (opensAt && closesAt && new Date(closesAt) <= new Date(opensAt)) {
      errors.closesAt = 'Closing time must be after opening time.'
    }
    const options = optionsText.split('\n').map((o) => o.trim()).filter(Boolean)
    const unique = [...new Set(options)]
    if (unique.length < 2) errors.options = 'Enter at least 2 distinct options, one per line.'
    if (unique.length > 10) errors.options = 'At most 10 options are allowed.'
    let quorumValue: number | undefined
    if (quorum.trim()) {
      quorumValue = Number(quorum)
      if (!Number.isInteger(quorumValue) || quorumValue < 1) errors.quorum = 'Quorum must be at least 1.'
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return null
    return {
      question: question.trim(),
      description: description.trim() || undefined,
      ballotPolicy,
      opensAt: localInputToIso(opensAt),
      closesAt: localInputToIso(closesAt),
      quorum: quorumValue,
      options: unique,
    }
  }

  const submit = async (): Promise<void> => {
    const values = parsed()
    if (!values) {
      setReviewing(false)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await votes.create(group.id, values)
      onDone()
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setFieldErrors(err.fieldErrors)
      setError(err instanceof ApiError ? err.message : 'Could not create the vote.')
      setReviewing(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form
      className="form-card"
      noValidate
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        if (parsed()) setReviewing(true)
      }}
    >
      <h3>Create a vote</h3>
      {prefill ? <p className="muted small">Prefilled from your assistant conversation — review before creating.</p> : null}
      <Field label="Question" error={fieldErrors.question} required>
        <input className="input" type="text" value={question} onChange={(e) => setQuestion(e.target.value)} maxLength={500} />
      </Field>
      <Field label="Description (optional)">
        <textarea className="input" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} />
      </Field>
      <div className="form-row">
        <Field label="Ballot policy" hint="Secret hides who voted for what — even from admins." required>
          <select className="input" value={ballotPolicy} onChange={(e) => setBallotPolicy(e.target.value)}>
            <option value="SECRET">Secret ballot</option>
            <option value="ATTRIBUTABLE">Attributable (choices visible)</option>
          </select>
        </Field>
        <Field label="Quorum (optional)" hint="Minimum ballots for a valid result." error={fieldErrors.quorum}>
          <input className="input input-short" type="number" min={1} value={quorum} onChange={(e) => setQuorum(e.target.value)} />
        </Field>
      </div>
      <div className="form-row">
        <Field label="Opens" error={fieldErrors.opensAt} required>
          <input className="input" type="datetime-local" value={opensAt} onChange={(e) => setOpensAt(e.target.value)} />
        </Field>
        <Field label="Closes" error={fieldErrors.closesAt} required>
          <input className="input" type="datetime-local" value={closesAt} onChange={(e) => setClosesAt(e.target.value)} />
        </Field>
      </div>
      <Field label="Options (one per line, 2–10)" error={fieldErrors.options} required>
        <textarea className="input" rows={4} value={optionsText} onChange={(e) => setOptionsText(e.target.value)} />
      </Field>
      <FormError message={error} />
      <button type="submit" className="btn btn-primary">Review vote</button>
      {reviewing ? (
        <ConfirmDialog title="Create this vote?" confirmLabel="Create vote" busy={busy} onCancel={() => setReviewing(false)} onConfirm={() => submit()}>
          <dl className="kv">
            <div className="kv-row"><dt>Question</dt><dd>{question}</dd></div>
            <div className="kv-row"><dt>Ballot</dt><dd>{ballotPolicy === 'SECRET' ? 'Secret' : 'Attributable'}</dd></div>
            <div className="kv-row"><dt>Window</dt><dd>{opensAt.replace('T', ' ')} → {closesAt.replace('T', ' ')}</dd></div>
          </dl>
          <p className="muted">The vote is created as a draft; opening it freezes eligibility and notifies members.</p>
        </ConfirmDialog>
      ) : null}
    </form>
  )
}

function safeLocal(iso: string): string {
  try {
    return isoToLocalInput(iso)
  } catch {
    return ''
  }
}

export function VoteDetail(): React.JSX.Element {
  const { voteId = '' } = useParams()
  const { group, can } = useSelectedGroup()
  const detail = useAsync(() => votes.detail(group.id, voteId), [group.id, voteId])
  const results = useAsync(
    () => (detail.data?.status === 'PUBLISHED' ? votes.results(group.id, voteId) : Promise.resolve(null)),
    [group.id, voteId, detail.data?.status],
  )
  const [chosen, setChosen] = useState('')
  const [casting, setCasting] = useState(false)
  const [action, setAction] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (detail.loading) return <LoadingState label="Loading vote…" />
  if (detail.error) return <ErrorState message={detail.error} onRetry={detail.reload} />
  const vote = detail.data
  if (!vote) return <EmptyState title="Vote not found" />
  const manager = can('VOTES_MANAGE')

  const run = async (fn: () => Promise<unknown>): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      setAction(null)
      setCasting(false)
      detail.reload()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('Your vote was already recorded — each member votes once.')
      } else {
        setError(err instanceof ApiError ? err.message : 'The action could not be completed.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <p><Link to="/votes">← Back to votes</Link></p>
      <h1>{vote.question}</h1>
      <p>
        <StatusPill status={vote.status} />{' '}
        <span className="muted">{vote.ballotPolicy === 'SECRET' ? 'Secret ballot' : 'Attributable ballot'}</span>
      </p>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="card" aria-label="Vote details">
        <dl className="kv">
          {vote.description ? <div className="kv-row"><dt>About</dt><dd>{vote.description}</dd></div> : null}
          <div className="kv-row"><dt>Opens</dt><dd><DateTime iso={vote.opensAt} /></dd></div>
          <div className="kv-row"><dt>Closes</dt><dd><DateTime iso={vote.closesAt} /></dd></div>
          <div className="kv-row"><dt>Eligible voters</dt><dd>{vote.eligibleCount}</dd></div>
          <div className="kv-row"><dt>Eligibility</dt><dd>{vote.eligibilityPolicy}</dd></div>
          {vote.quorum ? <div className="kv-row"><dt>Quorum</dt><dd>{vote.quorum}</dd></div> : null}
          <div className="kv-row"><dt>Your ballot</dt><dd>{vote.voted ? 'Recorded' : 'Not yet cast'}</dd></div>
        </dl>
        {vote.status === 'OPEN' && !vote.voted ? (
          <form
            onSubmit={(e: FormEvent) => {
              e.preventDefault()
              if (chosen) setCasting(true)
              else setError('Choose an option first.')
            }}
          >
            <fieldset>
              <legend>Your vote</legend>
              {vote.options.map((o) => (
                <label key={o.id} className="check">
                  <input type="radio" name="vote-option" value={o.id} checked={chosen === o.id} onChange={() => setChosen(o.id)} />
                  {o.label}
                </label>
              ))}
            </fieldset>
            <button type="submit" className="btn btn-primary">Cast vote</button>
          </form>
        ) : null}
        {vote.status === 'OPEN' && vote.voted ? (
          <p role="status">Your vote is recorded. It cannot be changed after the vote closes.</p>
        ) : null}
        {manager ? (
          <div className="row-actions">
            {vote.status === 'DRAFT' ? (
              <button type="button" className="btn btn-primary btn-small" onClick={() => setAction('open')}>Open voting</button>
            ) : null}
            {vote.status === 'OPEN' ? (
              <button type="button" className="btn btn-secondary btn-small" onClick={() => setAction('close')}>Close now</button>
            ) : null}
            {vote.status === 'CLOSED' ? (
              <button type="button" className="btn btn-primary btn-small" onClick={() => setAction('publish')}>Publish results</button>
            ) : null}
          </div>
        ) : null}
      </section>

      {vote.status === 'PUBLISHED' ? (
        <section className="card" aria-label="Results">
          <h2>Results</h2>
          {results.loading ? <p className="muted">Loading…</p> : null}
          {results.error ? <p className="form-error" role="alert">{results.error}</p> : null}
          {results.data ? (
            <>
              <ul className="list">
                {results.data.tallies.map((t) => (
                  <li key={t.optionId}>
                    <strong>{t.label}</strong>: {t.votes} vote{t.votes === 1 ? '' : 's'}
                    {results.data!.attributableChoices?.[t.optionId] ? (
                      <span className="muted small">
                        {' '}— {results.data!.attributableChoices[t.optionId].map((v) => v.displayName).join(', ')}
                      </span>
                    ) : null}
                  </li>
                ))}
              </ul>
              <p className="muted small">
                {results.data.totalBallots} of {results.data.eligibleCount} eligible voted
                {results.data.quorum ? ` · quorum ${results.data.quorum} ${results.data.quorumMet ? 'met' : 'not met'}` : ''}
                {results.data.publishedAt ? <> · published <DateTime iso={results.data.publishedAt} /></> : null}
              </p>
              {results.data.ballotPolicy === 'SECRET' ? (
                <p className="muted small">Secret ballot: individual choices are never shown.</p>
              ) : null}
            </>
          ) : null}
        </section>
      ) : vote.status === 'CLOSED' ? (
        <section className="card" aria-label="Results">
          <h2>Results</h2>
          <p className="muted">Voting has closed. Results will appear here after publication.</p>
        </section>
      ) : null}

      {casting ? (
        <ConfirmDialog title="Cast this vote?" confirmLabel="Cast vote" busy={busy} onCancel={() => setCasting(false)} onConfirm={() => run(() => votes.castBallot(group.id, vote.id, chosen))}>
          <p>
            You are voting for <strong>{vote.options.find((o) => o.id === chosen)?.label}</strong>.
            Each member votes once{vote.ballotPolicy === 'SECRET' ? ', and your choice stays secret' : ''}.
          </p>
        </ConfirmDialog>
      ) : null}
      {action === 'open' ? (
        <ConfirmDialog title="Open voting now?" confirmLabel="Open voting" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => votes.open(group.id, vote.id))}>
          <p>Eligibility freezes at the current active members, and eligible voters are notified.</p>
        </ConfirmDialog>
      ) : null}
      {action === 'close' ? (
        <ConfirmDialog title="Close voting now?" confirmLabel="Close voting" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => votes.close(group.id, vote.id))}>
          <p>No further ballots will be accepted. Results stay hidden until published.</p>
        </ConfirmDialog>
      ) : null}
      {action === 'publish' ? (
        <ConfirmDialog title="Publish results?" confirmLabel="Publish results" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => votes.publish(group.id, vote.id))}>
          <p>Results become visible to all members{vote.ballotPolicy === 'SECRET' ? ' as totals only' : ''}.</p>
        </ConfirmDialog>
      ) : null}
    </div>
  )
}
