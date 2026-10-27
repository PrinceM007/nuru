/**
 * Statements: request a personal statement or group summary, track approval,
 * review requests as an approver, and view generated documents with their
 * period, currency, and generation time.
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { statements } from '../api/resources'
import { useFormDraft } from '../ai/AssistantContext'
import { useAuth } from '../auth/AuthContext'
import { useGroups, useSelectedGroup } from '../groups/GroupContext'
import { formatDate } from '../components/format'
import {
  ConfirmDialog,
  DateTime,
  EmptyState,
  ErrorState,
  Field,
  FormError,
  LoadingState,
  Money,
  Pagination,
  StatusPill,
  useAsync,
} from '../components/ui'

export function Statements(): React.JSX.Element {
  const { selected, loading } = useGroups()
  const draft = useFormDraft('statement')
  const [requesting, setRequesting] = useState(false)

  if (loading) return <LoadingState label="Loading…" />
  if (!selected) return <EmptyState title="Choose a group first" hint="Statements belong to a savings group." />
  const open = requesting || draft !== null
  return (
    <div>
      <StatementsBody key={selected.id} requesting={open} setRequesting={setRequesting} prefill={draft} />
    </div>
  )
}

function StatementsBody({
  requesting,
  setRequesting,
  prefill,
}: {
  requesting: boolean
  setRequesting: (v: boolean) => void
  prefill: Record<string, string> | null
}): React.JSX.Element {
  const { group, can } = useSelectedGroup()
  const { profile } = useAuth()
  const [page, setPage] = useState(0)
  const list = useAsync(() => statements.list(group.id, page), [group.id, page])
  const policies = useAsync(() => statements.policies(group.id), [group.id])
  const approver = can('STATEMENTS_APPROVE')

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Statements</h1>
          <p className="muted">{group.name}</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setRequesting(!requesting)}>
          {requesting ? 'Close' : 'Request statement'}
        </button>
      </div>
      {requesting ? (
        <StatementRequestForm
          prefill={prefill}
          policies={policies.data ?? []}
          onDone={() => { setRequesting(false); list.reload() }}
        />
      ) : null}
      {approver ? <PolicyCard onChanged={() => policies.reload()} /> : null}
      <section className="card" aria-label="Statement requests">
        <h2>{approver ? 'All requests' : 'My requests'}</h2>
        {list.loading ? <p className="muted">Loading…</p> : null}
        {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
        {list.data && list.data.content.length === 0 && !list.loading ? (
          <EmptyState title="No statement requests" hint="Request your statement to see its status here." />
        ) : null}
        <ul className="list">
          {(list.data?.content ?? []).map((r) => (
            <RequestRow
              key={r.id}
              requestId={r.id}
              requestType={r.requestType}
              status={r.status}
              periodStart={r.periodStart}
              periodEnd={r.periodEnd}
              mine={r.requesterUserId === profile?.id}
              approver={approver}
              onChanged={list.reload}
            />
          ))}
        </ul>
        {list.data ? (
          <Pagination page={list.data.number} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
        ) : null}
      </section>
    </div>
  )
}

function StatementRequestForm({
  prefill,
  policies,
  onDone,
}: {
  prefill: Record<string, string> | null
  policies: { requestType: string; approvalRequired: boolean }[]
  onDone: () => void
}): React.JSX.Element {
  const { group } = useSelectedGroup()
  const [requestType, setRequestType] = useState(prefill?.requestType || 'PERSONAL_STATEMENT')
  const [periodStart, setPeriodStart] = useState(prefill?.periodStart ?? '')
  const [periodEnd, setPeriodEnd] = useState(prefill?.periodEnd ?? '')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const policy = policies.find((p) => p.requestType === requestType)

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const errors: Record<string, string> = {}
    if (!periodStart) errors.periodStart = 'Choose the period start.'
    if (!periodEnd) errors.periodEnd = 'Choose the period end.'
    if (periodStart && periodEnd && periodEnd < periodStart) errors.periodEnd = 'Period end must be on or after the start.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    setBusy(true)
    setError(null)
    try {
      await statements.request(group.id, { requestType, periodStart, periodEnd })
      onDone()
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setFieldErrors(err.fieldErrors)
      setError(err instanceof ApiError ? err.message : 'Could not submit the request.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form-card" onSubmit={submit} noValidate>
      <h3>Request a statement</h3>
      {prefill ? <p className="muted small">Prefilled from your assistant conversation — review before submitting.</p> : null}
      <Field label="Statement type" required>
        <select className="input" value={requestType} onChange={(e) => setRequestType(e.target.value)}>
          <option value="PERSONAL_STATEMENT">Personal statement (my records only)</option>
          <option value="GROUP_SUMMARY">Group summary (totals only, no member rows)</option>
        </select>
      </Field>
      {policy ? (
        <p className="muted small" role="status">
          {policy.approvalRequired
            ? 'This request type needs admin approval before the document is generated.'
            : 'This request type is approved automatically.'}
        </p>
      ) : null}
      <div className="form-row">
        <Field label="Period start" error={fieldErrors.periodStart} required>
          <input className="input" type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
        </Field>
        <Field label="Period end" error={fieldErrors.periodEnd} required>
          <input className="input" type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
        </Field>
      </div>
      <FormError message={error} />
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {busy ? 'Submitting…' : 'Submit request'}
      </button>
    </form>
  )
}

function PolicyCard({ onChanged }: { onChanged: () => void }): React.JSX.Element {
  const { group } = useSelectedGroup()
  const policies = useAsync(() => statements.policies(group.id), [group.id])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const toggle = async (requestType: string, approvalRequired: boolean): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await statements.setPolicy(group.id, requestType, approvalRequired)
      policies.reload()
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the policy.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" aria-label="Approval policies">
      <h2>Approval policies</h2>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {policies.loading ? <p className="muted">Loading…</p> : null}
      <ul className="list">
        {(policies.data ?? []).map((p) => (
          <li key={p.requestType}>
            {p.requestType === 'PERSONAL_STATEMENT' ? 'Personal statements' : 'Group summaries'}:{' '}
            {p.approvalRequired ? 'approval required' : 'automatic'}
            <div className="row-actions">
              <button
                type="button"
                className="btn btn-ghost btn-small"
                disabled={busy}
                onClick={() => toggle(p.requestType, !p.approvalRequired)}
              >
                {p.approvalRequired ? 'Make automatic' : 'Require approval'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

function RequestRow({
  requestId,
  requestType,
  status,
  periodStart,
  periodEnd,
  mine,
  approver,
  onChanged,
}: {
  requestId: string
  requestType: string
  status: string
  periodStart: string
  periodEnd: string
  mine: boolean
  approver: boolean
  onChanged: () => void
}): React.JSX.Element {
  const { group } = useSelectedGroup()
  const [action, setAction] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const run = async (fn: () => Promise<unknown>): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      setAction(null)
      setReason('')
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The action could not be completed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <li>
      {requestType === 'PERSONAL_STATEMENT' ? 'Personal statement' : 'Group summary'}{' '}
      <StatusPill status={status} />
      <br />
      <span className="muted small">
        {formatDate(periodStart)} – {formatDate(periodEnd)}
        {mine ? ' · yours' : ''}
      </span>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <div className="row-actions">
        {status === 'APPROVED' && (mine || approver) ? (
          <Link to={`/statements/${requestId}`} className="btn btn-ghost btn-small">View document</Link>
        ) : null}
        {status === 'PENDING' && approver ? (
          <>
            <button type="button" className="btn btn-primary btn-small" onClick={() => setAction('approve')}>Approve</button>
            <button type="button" className="btn btn-secondary btn-small" onClick={() => setAction('reject')}>Reject</button>
          </>
        ) : null}
      </div>
      {action === 'approve' ? (
        <ConfirmDialog title="Approve this request?" confirmLabel="Approve" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => statements.approve(group.id, requestId))}>
          <p>The document generates live from persisted records when viewed.</p>
        </ConfirmDialog>
      ) : null}
      {action === 'reject' ? (
        <ConfirmDialog title="Reject this request?" confirmLabel="Reject" danger busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => statements.reject(group.id, requestId, reason.trim() || undefined))}>
          <Field label="Reason (optional)">
            <input className="input" type="text" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          </Field>
        </ConfirmDialog>
      ) : null}
    </li>
  )
}

export function StatementDocumentView(): React.JSX.Element {
  const { requestId = '' } = useParams()
  const { group } = useSelectedGroup()
  const doc = useAsync(() => statements.document(group.id, requestId), [group.id, requestId])

  if (doc.loading) return <LoadingState label="Generating document…" />
  if (doc.error) return <ErrorState message={doc.error} onRetry={doc.reload} />
  const d = doc.data
  if (!d) return <EmptyState title="Document not available" />

  return (
    <div>
      <p><Link to="/statements">← Back to statements</Link></p>
      <h1>{d.title}</h1>
      <p className="muted">
        {d.currency} · {formatDate(d.periodStart)} – {formatDate(d.periodEnd)} · generated{' '}
        <DateTime iso={d.generatedAt} />
      </p>
      <section className="card" aria-label="Statement totals">
        <h2>Totals</h2>
        <dl className="kv kv-grid">
          <div className="kv-row"><dt>Expected</dt><dd><Money amount={d.expectedTotal} currency={d.currency} /></dd></div>
          <div className="kv-row"><dt>Confirmed</dt><dd><Money amount={d.confirmedTotal} currency={d.currency} /></dd></div>
          <div className="kv-row"><dt>Borrowed</dt><dd><Money amount={d.borrowedTotal} currency={d.currency} /></dd></div>
          <div className="kv-row"><dt>Loans outstanding</dt><dd><Money amount={d.loansOutstandingTotal} currency={d.currency} /></dd></div>
        </dl>
      </section>
      {d.contributions.length > 0 ? (
        <section className="card" aria-label="Contribution lines">
          <h2>Contributions</h2>
          <ul className="list">
            {d.contributions.map((c, i) => (
              <li key={i}>
                <Money amount={c.amount} currency={d.currency} /> <StatusPill status={c.status} />
                <br />
                <span className="muted small">
                  {formatDate(c.periodStart)} – {formatDate(c.periodEnd)} · {c.recordedVia === 'MANUAL' ? 'manual entry' : 'sandbox simulation'}
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {d.loans.length > 0 ? (
        <section className="card" aria-label="Loan lines">
          <h2>Loans</h2>
          <ul className="list">
            {d.loans.map((l) => (
              <li key={l.loanId}>
                Principal <Money amount={l.principal} currency={d.currency} /> · outstanding{' '}
                <Money amount={l.outstanding} currency={d.currency} /> <StatusPill status={l.status} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {d.groupTotals ? (
        <section className="card" aria-label="Group totals">
          <h2>Group totals</h2>
          <dl className="kv kv-grid">
            <div className="kv-row"><dt>Expected</dt><dd><Money amount={d.groupTotals.expectedTotal} currency={d.currency} /></dd></div>
            <div className="kv-row"><dt>Confirmed</dt><dd><Money amount={d.groupTotals.confirmedContributionsTotal} currency={d.currency} /></dd></div>
            <div className="kv-row"><dt>Disbursed</dt><dd><Money amount={d.groupTotals.loansDisbursedTotal} currency={d.currency} /></dd></div>
            <div className="kv-row"><dt>Repaid</dt><dd><Money amount={d.groupTotals.loansRepaidTotal} currency={d.currency} /></dd></div>
            <div className="kv-row"><dt>Outstanding</dt><dd><Money amount={d.groupTotals.loansOutstandingTotal} currency={d.currency} /></dd></div>
            <div className="kv-row"><dt>Net</dt><dd><Money amount={d.groupTotals.netBalance} currency={d.currency} /></dd></div>
            <div className="kv-row"><dt>Active members</dt><dd>{d.groupTotals.activeMembers}</dd></div>
          </dl>
        </section>
      ) : null}
    </div>
  )
}
