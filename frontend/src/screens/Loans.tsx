/**
 * Loans: request list, request flow, approval/disbursal workflows,
 * repayment schedule + repayments, and the optional credit estimate
 * (always labeled an estimate, never a decision).
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { identity, loans, newIdempotencyKey } from '../api/resources'
import type { Loan } from '../api/types'
import { useFormDraft } from '../ai/AssistantContext'
import { useAuth } from '../auth/AuthContext'
import { useGroups, useSelectedGroup } from '../groups/GroupContext'
import { formatDate, parseAmountInput } from '../components/format'
import {
  AccessDenied,
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

export function Loans(): React.JSX.Element {
  const { selected, loading } = useGroups()
  const draft = useFormDraft('loan')
  const [requesting, setRequesting] = useState(false)

  if (loading) return <LoadingState label="Loading…" />
  if (!selected) return <EmptyState title="Choose a group first" hint="Loans belong to a savings group." />
  return <LoansForGroup key={selected.id} prefill={draft} requesting={requesting || draft !== null} setRequesting={setRequesting} />
}

function LoansForGroup({
  prefill,
  requesting,
  setRequesting,
}: {
  prefill: Record<string, string> | null
  requesting: boolean
  setRequesting: (v: boolean) => void
}): React.JSX.Element {
  const { group } = useSelectedGroup()
  const { profile } = useAuth()
  const [page, setPage] = useState(0)
  const list = useAsync(() => loans.list(group.id, page), [group.id, page])

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Loans</h1>
          <p className="muted">{group.name} · {group.currency}</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setRequesting(!requesting)}>
          {requesting ? 'Close' : 'Request a loan'}
        </button>
      </div>
      {requesting ? (
        <LoanRequestForm prefill={prefill} onDone={() => { setRequesting(false); list.reload() }} />
      ) : null}
      <CreditEstimateCard />
      <section className="card" aria-label="Loan requests">
        <h2>Loan requests</h2>
        {list.loading ? <p className="muted">Loading…</p> : null}
        {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
        {list.data && list.data.content.length === 0 && !list.loading ? (
          <EmptyState title="No loans yet" hint="Loan requests and their decisions will appear here." />
        ) : null}
        <ul className="list">
          {(list.data?.content ?? []).map((l) => (
            <li key={l.id}>
              <Link to={`/loans/${l.id}`}>
                <Money amount={l.principal} currency={l.currency} /> over {l.termMonths} months
              </Link>{' '}
              <StatusPill status={l.status} />
              <br />
              <span className="muted small">
                {l.borrowerUserId === profile?.id ? 'Your loan' : 'Another member’s loan'} · outstanding{' '}
                <Money amount={l.outstandingBalance} currency={l.currency} /> · requested{' '}
                {formatDate(l.requestedAt)}
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

function LoanRequestForm({ prefill, onDone }: { prefill: Record<string, string> | null; onDone: () => void }): React.JSX.Element {
  const { group } = useSelectedGroup()
  const [principal, setPrincipal] = useState(prefill?.principal ?? '')
  const [interestRate, setInterestRate] = useState(prefill?.interestRate ?? '')
  const [termMonths, setTermMonths] = useState(prefill?.termMonths ?? '12')
  const [purpose, setPurpose] = useState(prefill?.purpose ?? '')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reviewing, setReviewing] = useState(false)

  const parsed = (): { principal: number; interestRate?: number; termMonths: number } | null => {
    const errors: Record<string, string> = {}
    const p = parseAmountInput(principal)
    if (!p.ok) errors.principal = p.error
    let rate: number | undefined
    if (interestRate.trim()) {
      rate = Number(interestRate)
      if (!Number.isFinite(rate) || rate < 0 || rate > 1) errors.interestRate = 'Enter a rate between 0 and 1 (for example 0.1 for 10% flat).'
    }
    const term = Number(termMonths)
    if (!Number.isInteger(term) || term < 1 || term > 60) errors.termMonths = 'Term must be 1–60 months.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0 || !p.ok) return null
    return { principal: p.value, interestRate: rate, termMonths: term }
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
      await loans.request(
        group.id,
        { principal: values.principal, interestRate: values.interestRate, termMonths: values.termMonths, purpose: purpose.trim() || undefined },
        newIdempotencyKey(),
      )
      onDone()
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setFieldErrors(err.fieldErrors)
      setError(err instanceof ApiError ? err.message : 'Could not submit the loan request.')
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
      <h3>Request a loan</h3>
      {prefill ? <p className="muted small">Prefilled from your assistant conversation — review before submitting.</p> : null}
      <div className="form-row">
        <Field label={`Principal (${group.currency})`} error={fieldErrors.principal} required>
          <input className="input" type="text" inputMode="decimal" value={principal} onChange={(e) => setPrincipal(e.target.value)} />
        </Field>
        <Field label="Flat interest rate (optional)" hint="0 to 1, for example 0.1 means 10%." error={fieldErrors.interestRate}>
          <input className="input" type="text" inputMode="decimal" value={interestRate} onChange={(e) => setInterestRate(e.target.value)} />
        </Field>
        <Field label="Term (months)" error={fieldErrors.termMonths} required>
          <input className="input input-short" type="number" min={1} max={60} value={termMonths} onChange={(e) => setTermMonths(e.target.value)} />
        </Field>
      </div>
      <Field label="Purpose (optional)" error={fieldErrors.purpose}>
        <input className="input" type="text" value={purpose} onChange={(e) => setPurpose(e.target.value)} maxLength={500} />
      </Field>
      <p className="muted small">
        The total due is locked when you submit: principal × (1 + rate). A group approver who is
        not you reviews the request — no score or estimate decides it automatically.
      </p>
      <FormError message={error} />
      <button type="submit" className="btn btn-primary">Review request</button>
      {reviewing ? (
        <ConfirmDialog title="Submit this loan request?" confirmLabel="Submit request" busy={busy} onCancel={() => setReviewing(false)} onConfirm={() => submit()}>
          <dl className="kv">
            <div className="kv-row"><dt>Principal</dt><dd>{principal} {group.currency}</dd></div>
            <div className="kv-row"><dt>Rate</dt><dd>{interestRate || '0 (no interest)'}</dd></div>
            <div className="kv-row"><dt>Term</dt><dd>{termMonths} months</dd></div>
            {purpose ? <div className="kv-row"><dt>Purpose</dt><dd>{purpose}</dd></div> : null}
          </dl>
        </ConfirmDialog>
      ) : null}
    </form>
  )
}

function CreditEstimateCard(): React.JSX.Element {
  const { group } = useSelectedGroup()
  const [asked, setAsked] = useState(false)
  const estimate = useAsync(
    () => (asked ? identity.creditEstimate(group.id) : Promise.resolve(null)),
    [group.id, asked],
  )
  return (
    <section className="card" aria-label="Credit estimate">
      <h2>Credit estimate <span className="muted small">(optional)</span></h2>
      {!asked ? (
        <>
          <p className="muted small">
            An estimate computed from your recorded group history. It is informational only — no
            loan is ever approved or denied automatically because of it.
          </p>
          <button type="button" className="btn btn-secondary btn-small" onClick={() => setAsked(true)}>
            Show my estimate
          </button>
        </>
      ) : estimate.loading ? (
        <p className="muted">Loading…</p>
      ) : estimate.error ? (
        <p className="muted" role="status">
          {estimate.error.toLowerCase().includes('unavailable') || estimate.error.toLowerCase().includes('not configured')
            ? 'Credit scoring is not configured for this installation.'
            : estimate.error}
        </p>
      ) : estimate.data ? (
        <div>
          {estimate.data.insufficientHistory || estimate.data.score === null ? (
            <p role="status">Not enough recorded history for an estimate yet.</p>
          ) : (
            <>
              <p>
                <strong>Estimate: {estimate.data.score} / 100</strong> <StatusPill status="ESTIMATE" tone="info" />
              </p>
              <ul className="list">
                {estimate.data.factors.map((f) => (
                  <li key={f.name}>{f.name}: {f.value}</li>
                ))}
              </ul>
            </>
          )}
          <p className="muted small">
            Based on {formatDate(estimate.data.dataPeriodStart)} – {formatDate(estimate.data.dataPeriodEnd)} ·
            estimated <DateTime iso={estimate.data.estimatedAt} /> · {estimate.data.label}
            If a source record is wrong, ask a treasurer to correct it, or request a human review
            from your group admin.
          </p>
        </div>
      ) : null}
    </section>
  )
}

export function LoanDetail(): React.JSX.Element {
  const { loanId = '' } = useParams()
  const { group, can } = useSelectedGroup()
  const { profile } = useAuth()
  const detail = useAsync(() => loans.detail(group.id, loanId), [group.id, loanId])
  const schedule = useAsync(
    () => (detail.data ? loans.schedule(group.id, loanId) : Promise.reject(new Error('loading'))),
    [group.id, loanId, detail.data?.id],
  )
  const repayments = useAsync(() => loans.repayments(group.id, loanId, 0), [group.id, loanId])
  const [action, setAction] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [repaymentAmount, setRepaymentAmount] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (detail.loading) return <LoadingState label="Loading loan…" />
  if (detail.error) {
    if (detail.error.toLowerCase().includes('denied')) return <AccessDenied hint="Only the borrower and authorized approvers can view this loan." />
    return <ErrorState message={detail.error} onRetry={detail.reload} />
  }
  const loan: Loan | null = detail.data
  if (!loan) return <EmptyState title="Loan not found" />
  const mine = loan.borrowerUserId === profile?.id
  const approver = can('LOANS_APPROVE') && !mine

  const run = async (fn: () => Promise<unknown>): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      setAction(null)
      setReason('')
      setRepaymentAmount('')
      detail.reload()
      repayments.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The action could not be completed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <p><Link to="/loans">← Back to loans</Link></p>
      <h1>Loan <Money amount={loan.principal} currency={loan.currency} /></h1>
      <p><StatusPill status={loan.status} /></p>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <section className="card" aria-label="Loan details">
        <dl className="kv kv-grid">
          <div className="kv-row"><dt>Borrower</dt><dd>{mine ? 'You' : 'Another member'}</dd></div>
          <div className="kv-row"><dt>Total due</dt><dd><Money amount={loan.totalDue} currency={loan.currency} /></dd></div>
          <div className="kv-row"><dt>Outstanding</dt><dd><Money amount={loan.outstandingBalance} currency={loan.currency} /></dd></div>
          <div className="kv-row"><dt>Term</dt><dd>{loan.termMonths} months</dd></div>
          <div className="kv-row"><dt>Requested</dt><dd><DateTime iso={loan.requestedAt} /></dd></div>
          {loan.purpose ? <div className="kv-row"><dt>Purpose</dt><dd>{loan.purpose}</dd></div> : null}
          {loan.decidedAt ? <div className="kv-row"><dt>Decided</dt><dd><DateTime iso={loan.decidedAt} /></dd></div> : null}
          {loan.decisionReason ? <div className="kv-row"><dt>Decision reason</dt><dd>{loan.decisionReason}</dd></div> : null}
          {loan.disbursedAt ? <div className="kv-row"><dt>Disbursed (recorded)</dt><dd><DateTime iso={loan.disbursedAt} /></dd></div> : null}
        </dl>
        <div className="row-actions">
          {loan.status === 'REQUESTED' && approver ? (
            <>
              <button type="button" className="btn btn-primary btn-small" onClick={() => setAction('approve')}>Approve</button>
              <button type="button" className="btn btn-secondary btn-small" onClick={() => setAction('reject')}>Reject</button>
            </>
          ) : null}
          {loan.status === 'REQUESTED' && mine ? (
            <button type="button" className="btn btn-ghost btn-small" onClick={() => setAction('cancel')}>Cancel request</button>
          ) : null}
          {loan.status === 'APPROVED' && approver ? (
            <button type="button" className="btn btn-primary btn-small" onClick={() => setAction('disburse')}>Record disbursal</button>
          ) : null}
          {loan.status === 'ACTIVE' && approver ? (
            <button type="button" className="btn btn-secondary btn-small" onClick={() => setAction('default')}>Mark defaulted</button>
          ) : null}
          {loan.status === 'DEFAULTED' && approver ? (
            <button type="button" className="btn btn-secondary btn-small" onClick={() => setAction('reactivate')}>Reactivate</button>
          ) : null}
          {(loan.status === 'ACTIVE' || loan.status === 'DEFAULTED') && (mine || can('CONTRIBUTIONS_CONFIRM')) ? (
            <button type="button" className="btn btn-primary btn-small" onClick={() => setAction('repay')}>Record repayment</button>
          ) : null}
        </div>
      </section>

      <section className="card" aria-label="Repayment schedule">
        <h2>Repayment schedule</h2>
        {schedule.loading ? <p className="muted">Loading…</p> : null}
        {schedule.error && !schedule.loading ? <p className="muted">The schedule appears after disbursal.</p> : null}
        {(schedule.data?.length ?? 0) > 0 ? (
          <ul className="list">
            {schedule.data!.map((i) => (
              <li key={i.installmentNo}>
                #{i.installmentNo} · due {formatDate(i.dueDate)} · <Money amount={i.amountDue} currency={loan.currency} /> ·{' '}
                paid <Money amount={i.amountPaid} currency={loan.currency} /> <StatusPill status={i.status} />
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <section className="card" aria-label="Repayments">
        <h2>Repayments</h2>
        {repayments.loading ? <p className="muted">Loading…</p> : null}
        <ul className="list">
          {(repayments.data?.content ?? []).map((r) => (
            <li key={r.id}>
              <Money amount={r.amount} currency={r.currency} /> <StatusPill status={r.status} />
              <br />
              <span className="muted small">
                Recorded {formatDate(r.createdAt)}
                {r.confirmedAt ? ` · confirmed ${formatDate(r.confirmedAt)}` : ''}
                {r.rejectionReason ? ` · ${r.rejectionReason}` : ''}
              </span>
              {r.status === 'SUBMITTED' && can('CONTRIBUTIONS_CONFIRM') ? (
                <div className="row-actions">
                  <button type="button" className="btn btn-primary btn-small" disabled={busy} onClick={() => run(() => loans.confirmRepayment(group.id, loan.id, r.id))}>
                    Confirm
                  </button>
                  <button type="button" className="btn btn-secondary btn-small" disabled={busy} onClick={() => run(() => loans.rejectRepayment(group.id, loan.id, r.id))}>
                    Reject
                  </button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
        {(repayments.data?.content.length ?? 0) === 0 && !repayments.loading ? (
          <p className="muted">No repayments recorded yet.</p>
        ) : null}
      </section>

      {action === 'approve' ? (
        <ConfirmDialog title="Approve this loan?" confirmLabel="Approve" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => loans.approve(group.id, loan.id))}>
          <p>Approve <Money amount={loan.principal} currency={loan.currency} /> for the borrower. Disbursal is a separate recorded step.</p>
        </ConfirmDialog>
      ) : null}
      {action === 'reject' ? (
        <ConfirmDialog title="Reject this loan?" confirmLabel="Reject" danger busy={busy} onCancel={() => setAction(null)} onConfirm={() => {
          if (!reason.trim()) { setError('A reason is required to reject a loan.'); return }
          void run(() => loans.reject(group.id, loan.id, reason.trim()))
        }}>
          <Field label="Reason" required>
            <input className="input" type="text" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          </Field>
        </ConfirmDialog>
      ) : null}
      {action === 'cancel' ? (
        <ConfirmDialog title="Cancel this request?" confirmLabel="Cancel request" danger busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => loans.cancel(group.id, loan.id))}>
          <p>Withdraw your loan request. This cannot be undone.</p>
        </ConfirmDialog>
      ) : null}
      {action === 'disburse' ? (
        <ConfirmDialog title="Record disbursal?" confirmLabel="Record disbursal" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => loans.disburse(group.id, loan.id))}>
          <p>
            This records the group’s decision to disburse and generates the repayment schedule. It
            does not move real money — the actual payout happens outside Nuru.
          </p>
        </ConfirmDialog>
      ) : null}
      {action === 'default' ? (
        <ConfirmDialog title="Mark this loan defaulted?" confirmLabel="Mark defaulted" danger busy={busy} onCancel={() => setAction(null)} onConfirm={() => {
          if (!reason.trim()) { setError('A reason is required.'); return }
          void run(() => loans.markDefaulted(group.id, loan.id, reason.trim()))
        }}>
          <Field label="Reason" required>
            <input className="input" type="text" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          </Field>
        </ConfirmDialog>
      ) : null}
      {action === 'reactivate' ? (
        <ConfirmDialog title="Reactivate this loan?" confirmLabel="Reactivate" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => loans.reactivate(group.id, loan.id))}>
          <p>Return the loan to active status.</p>
        </ConfirmDialog>
      ) : null}
      {action === 'repay' ? (
        <ConfirmDialog title="Record a repayment" confirmLabel="Save repayment" busy={busy} onCancel={() => setAction(null)} onConfirm={() => {
          const parsed = parseAmountInput(repaymentAmount)
          if (!parsed.ok) { setError(parsed.error); return }
          void run(() => loans.submitRepayment(group.id, loan.id, parsed.value, newIdempotencyKey()))
        }}>
          <p className="muted">
            Outstanding: <Money amount={loan.outstandingBalance} currency={loan.currency} />. The
            repayment is submitted for treasurer confirmation.
          </p>
          <Field label={`Amount (${loan.currency})`} required>
            <input className="input" type="text" inputMode="decimal" value={repaymentAmount} onChange={(e) => setRepaymentAmount(e.target.value)} />
          </Field>
        </ConfirmDialog>
      ) : null}
    </div>
  )
}
