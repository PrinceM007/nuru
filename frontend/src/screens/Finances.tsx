/**
 * Contributions and group finances: schedules, contribution records with
 * expected-vs-confirmed distinction, ledger summary + journal, and the
 * record/confirm/reject/adjust workflows with confirmation dialogs.
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { finance, groups, newIdempotencyKey } from '../api/resources'
import type { Contribution } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useGroups, useSelectedGroup } from '../groups/GroupContext'
import { formatDate, parseAmountInput, parseDeltaInput } from '../components/format'
import {
  AccessDenied,
  ConfirmDialog,
  DateTime,
  EmptyState,
  Field,
  FormError,
  LoadingState,
  Money,
  Pagination,
  StatusPill,
  useAsync,
} from '../components/ui'

function NoGroup(): React.JSX.Element {
  const { loading } = useGroups()
  if (loading) return <LoadingState label="Loading…" />
  return <EmptyState title="Choose a group first" hint="Contributions belong to a savings group." />
}

export function Finances(): React.JSX.Element {
  const { selected } = useGroups()
  if (!selected) return <NoGroup />
  return <FinancesForGroup key={selected.id} />
}

function FinancesForGroup(): React.JSX.Element {
  const { group, can } = useSelectedGroup()
  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Contributions</h1>
          <p className="muted">{group.name} · {group.currency}</p>
        </div>
      </div>
      <LedgerSummaryCard />
      <ContributionsSection />
      {can('CONTRIBUTIONS_CONFIRM') ? <SchedulesSection /> : <SchedulesReadOnly />}
      {can('LEDGER_VIEW_FULL') ? <LedgerJournal /> : null}
    </div>
  )
}

function LedgerSummaryCard(): React.JSX.Element {
  const { group } = useSelectedGroup()
  const summary = useAsync(() => finance.ledgerSummary(group.id), [group.id])
  return (
    <section className="card" aria-label="Ledger summary">
      <h2>Ledger summary</h2>
      {summary.loading ? <p className="muted">Loading…</p> : null}
      {summary.error ? <p className="form-error" role="alert">{summary.error}</p> : null}
      {summary.data ? (
        <>
          <dl className="kv kv-grid">
            <div className="kv-row"><dt>Expected (group)</dt><dd><Money amount={summary.data.expectedTotal} currency={group.currency} /></dd></div>
            <div className="kv-row"><dt>Confirmed received (group)</dt><dd><Money amount={summary.data.confirmedContributionsTotal} currency={group.currency} /></dd></div>
            <div className="kv-row"><dt>Loans disbursed</dt><dd><Money amount={summary.data.loansDisbursedTotal} currency={group.currency} /></dd></div>
            <div className="kv-row"><dt>Loans repaid</dt><dd><Money amount={summary.data.loansRepaidTotal} currency={group.currency} /></dd></div>
            <div className="kv-row"><dt>Loans outstanding</dt><dd><Money amount={summary.data.loansOutstandingTotal} currency={group.currency} /></dd></div>
            <div className="kv-row"><dt>Net balance</dt><dd><Money amount={summary.data.netBalance} currency={group.currency} /></dd></div>
            <div className="kv-row"><dt>My expected</dt><dd><Money amount={summary.data.myExpectedTotal} currency={group.currency} /></dd></div>
            <div className="kv-row"><dt>My confirmed</dt><dd><Money amount={summary.data.myConfirmedTotal} currency={group.currency} /></dd></div>
          </dl>
          <p className="muted small">As of <DateTime iso={summary.data.asOf} /> · computed from stored records.</p>
        </>
      ) : null}
    </section>
  )
}

function ContributionsSection(): React.JSX.Element {
  const { group, can } = useSelectedGroup()
  const { profile } = useAuth()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [showAll, setShowAll] = useState(false)
  const [recording, setRecording] = useState(false)
  const list = useAsync(
    () =>
      finance.contributions(group.id, {
        memberUserId: showAll ? undefined : profile?.id,
        status: status || undefined,
        page,
      }),
    [group.id, profile?.id, status, page, showAll],
  )

  return (
    <section className="card" aria-label="Contribution records">
      <div className="page-head">
        <h2>Contribution records</h2>
        <button type="button" className="btn btn-primary" onClick={() => setRecording((r) => !r)}>
          {recording ? 'Close' : 'Record contribution'}
        </button>
      </div>
      <p className="muted small">
        <StatusPill status="EXPECTED" /> means asked for but not yet received. <StatusPill status="CONFIRMED" /> means a
        treasurer recorded it as received. Manual entries record bookkeeping — they do not prove money moved.
      </p>
      {recording ? <RecordForm onDone={() => { setRecording(false); list.reload() }} /> : null}
      <div className="form-row">
        <Field label="Status">
          <select className="input input-short" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
            <option value="">All</option>
            <option value="EXPECTED">Expected</option>
            <option value="SUBMITTED">Submitted</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </Field>
        {can('LEDGER_VIEW_FULL') ? (
          <label className="check">
            <input type="checkbox" checked={showAll} onChange={(e) => { setShowAll(e.target.checked); setPage(0) }} />
            Show every member
          </label>
        ) : null}
      </div>
      {list.loading ? <p className="muted">Loading…</p> : null}
      {list.error ? (
        list.error.toLowerCase().includes('denied') ? (
          <AccessDenied hint="You can only see your own contribution records." />
        ) : (
          <p className="form-error" role="alert">{list.error}</p>
        )
      ) : null}
      {list.data && list.data.content.length === 0 && !list.loading ? (
        <EmptyState title="No contribution records" hint="Recorded contributions will appear here." />
      ) : null}
      <ul className="list">
        {(list.data?.content ?? []).map((c) => (
          <ContributionRow key={c.id} contribution={c} onChanged={list.reload} />
        ))}
      </ul>
      {list.data ? (
        <Pagination page={list.data.number} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
      ) : null}
    </section>
  )
}

function ContributionRow({ contribution: c, onChanged }: { contribution: Contribution; onChanged: () => void }): React.JSX.Element {
  const { group, can } = useSelectedGroup()
  const { profile } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [adjusting, setAdjusting] = useState(false)
  const [reason, setReason] = useState('')
  const [delta, setDelta] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const mine = profile?.id === c.memberUserId
  const unconfirmed = c.status === 'EXPECTED' || c.status === 'SUBMITTED'

  const run = async (action: () => Promise<unknown>): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await action()
      setConfirming(false)
      setRejecting(false)
      setAdjusting(false)
      setReason('')
      setDelta('')
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The action could not be completed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <li>
      <Money amount={c.amount} currency={c.currency} /> <StatusPill status={c.status} />
      <br />
      <span className="muted small">
        {formatDate(c.periodStart)} – {formatDate(c.periodEnd)}
        {mine ? ' · yours' : ' · another member'} · recorded via {c.recordedVia === 'MANUAL' ? 'manual entry' : 'sandbox simulation'}
        {c.notes ? ` · ${c.notes}` : ''}
      </span>
      {c.adjustments.length > 0 ? (
        <ul className="sub-list">
          {c.adjustments.map((a) => (
            <li key={a.id}>
              Correction: <Money amount={a.amount} currency={a.currency} /> · {a.notes ?? 'no reason given'} · <StatusPill status={a.status} />
            </li>
          ))}
        </ul>
      ) : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {unconfirmed && can('CONTRIBUTIONS_CONFIRM') ? (
        <div className="row-actions">
          <button type="button" className="btn btn-primary btn-small" onClick={() => setConfirming(true)}>
            Confirm receipt
          </button>
          <button type="button" className="btn btn-secondary btn-small" onClick={() => setRejecting(true)}>
            Reject
          </button>
        </div>
      ) : null}
      {c.status === 'CONFIRMED' && can('CONTRIBUTIONS_CONFIRM') ? (
        <div className="row-actions">
          <button type="button" className="btn btn-ghost btn-small" onClick={() => setAdjusting(true)}>
            Record correction
          </button>
        </div>
      ) : null}

      {confirming ? (
        <ConfirmDialog
          title="Confirm this contribution?"
          confirmLabel="Confirm receipt"
          busy={busy}
          onCancel={() => setConfirming(false)}
          onConfirm={() => run(() => finance.confirmContribution(group.id, c.id))}
        >
          <p>
            Confirm <Money amount={c.amount} currency={c.currency} /> for {formatDate(c.periodStart)} –{' '}
            {formatDate(c.periodEnd)} as received. This writes to the group ledger.
          </p>
        </ConfirmDialog>
      ) : null}
      {rejecting ? (
        <ConfirmDialog
          title="Reject this contribution?"
          confirmLabel="Reject"
          danger
          busy={busy}
          onCancel={() => setRejecting(false)}
          onConfirm={() => run(() => finance.rejectContribution(group.id, c.id, reason || undefined))}
        >
          <Field label="Reason (optional)">
            <input className="input" type="text" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          </Field>
        </ConfirmDialog>
      ) : null}
      {adjusting ? (
        <AdjustDialog
          busy={busy}
          delta={delta}
          setDelta={setDelta}
          reason={reason}
          setReason={setReason}
          onCancel={() => setAdjusting(false)}
          onConfirm={() => {
            const parsed = parseDeltaInput(delta)
            if (!parsed.ok) {
              setError(parsed.error)
              return
            }
            if (!reason.trim()) {
              setError('A reason is required for corrections.')
              return
            }
            void run(() => finance.adjustContribution(group.id, c.id, parsed.value, reason.trim()))
          }}
        />
      ) : null}
    </li>
  )
}

function AdjustDialog({
  busy,
  delta,
  setDelta,
  reason,
  setReason,
  onCancel,
  onConfirm,
}: {
  busy: boolean
  delta: string
  setDelta: (v: string) => void
  reason: string
  setReason: (v: string) => void
  onCancel: () => void
  onConfirm: () => void
}): React.JSX.Element {
  return (
    <ConfirmDialog title="Record a correction" confirmLabel="Save correction" busy={busy} onCancel={onCancel} onConfirm={onConfirm}>
      <p className="muted">
        Corrections are new linked entries — the original record is never overwritten.
      </p>
      <Field label="Correction amount (use a minus sign to reduce)" hint="Example: -50 or 25" required>
        <input className="input" type="text" inputMode="decimal" value={delta} onChange={(e) => setDelta(e.target.value)} />
      </Field>
      <Field label="Reason" required>
        <input className="input" type="text" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={1000} />
      </Field>
    </ConfirmDialog>
  )
}

function RecordForm({ onDone }: { onDone: () => void }): React.JSX.Element {
  const { group, can } = useSelectedGroup()
  const { profile } = useAuth()
  const schedules = useAsync(() => finance.schedules(group.id, 0), [group.id])
  const members = useAsync(
    () => (can('LEDGER_VIEW_FULL') ? groups.members(group.id, 0) : Promise.resolve(null)),
    [group.id],
  )
  const [amount, setAmount] = useState('')
  const [periodStart, setPeriodStart] = useState('')
  const [periodEnd, setPeriodEnd] = useState('')
  const [scheduleId, setScheduleId] = useState('')
  const [memberUserId, setMemberUserId] = useState(profile?.id ?? '')
  const [assessment, setAssessment] = useState(false)
  const [notes, setNotes] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reviewing, setReviewing] = useState(false)

  const validate = (): number | null => {
    const errors: Record<string, string> = {}
    const parsed = parseAmountInput(amount)
    if (!parsed.ok) errors.amount = parsed.error
    if (!periodStart) errors.periodStart = 'Choose the period start.'
    if (!periodEnd) errors.periodEnd = 'Choose the period end.'
    if (periodStart && periodEnd && periodEnd < periodStart) errors.periodEnd = 'Period end must be on or after the start.'
    if (assessment && !can('CONTRIBUTIONS_CONFIRM')) errors.assessment = 'Only a treasurer can record an assessment.'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0 && parsed.ok ? parsed.value : null
  }

  const submit = async (): Promise<void> => {
    const value = validate()
    if (value === null) {
      setReviewing(false)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await finance.recordContribution(
        group.id,
        {
          memberUserId: memberUserId || profile?.id,
          scheduleId: scheduleId || undefined,
          periodStart,
          periodEnd,
          amount: value,
          assessment,
          notes: notes.trim() || undefined,
        },
        newIdempotencyKey(),
      )
      onDone()
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setFieldErrors(err.fieldErrors)
      setError(err instanceof ApiError ? err.message : 'Could not record the contribution.')
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
        if (validate() !== null) setReviewing(true)
      }}
    >
      <h3>Record a contribution</h3>
      <p className="muted small">
        This records bookkeeping. An assessment marks money the group expects; otherwise the entry
        is submitted for a treasurer to confirm.
      </p>
      <div className="form-row">
        <Field label={`Amount (${group.currency})`} error={fieldErrors.amount} required>
          <input className="input" type="text" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label="Period start" error={fieldErrors.periodStart} required>
          <input className="input" type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
        </Field>
        <Field label="Period end" error={fieldErrors.periodEnd} required>
          <input className="input" type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
        </Field>
      </div>
      <div className="form-row">
        <Field label="Schedule (optional)" error={fieldErrors.scheduleId}>
          <select className="input" value={scheduleId} onChange={(e) => setScheduleId(e.target.value)}>
            <option value="">None</option>
            {(schedules.data?.content ?? []).map((s) => (
              <option key={s.id} value={s.id}>{s.title}</option>
            ))}
          </select>
        </Field>
        {members.data ? (
          <Field label="Member" error={fieldErrors.memberUserId}>
            <select className="input" value={memberUserId} onChange={(e) => setMemberUserId(e.target.value)}>
              {members.data.content.map((m) => (
                <option key={m.userId} value={m.userId}>{m.displayName}</option>
              ))}
            </select>
          </Field>
        ) : null}
      </div>
      {can('CONTRIBUTIONS_CONFIRM') ? (
        <label className="check">
          <input type="checkbox" checked={assessment} onChange={(e) => setAssessment(e.target.checked)} />
          Record as an assessment (money the group expects)
        </label>
      ) : null}
      {fieldErrors.assessment ? <p className="field-error" role="alert">{fieldErrors.assessment}</p> : null}
      <Field label="Notes (optional)" error={fieldErrors.notes}>
        <input className="input" type="text" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} />
      </Field>
      <FormError message={error} />
      <button type="submit" className="btn btn-primary">Review entry</button>

      {reviewing ? (
        <ConfirmDialog
          title="Save this contribution record?"
          confirmLabel="Save record"
          busy={busy}
          onCancel={() => setReviewing(false)}
          onConfirm={() => submit()}
        >
          <dl className="kv">
            <div className="kv-row"><dt>Amount</dt><dd>{amount} {group.currency}</dd></div>
            <div className="kv-row"><dt>Period</dt><dd>{periodStart} to {periodEnd}</dd></div>
            <div className="kv-row"><dt>Type</dt><dd>{assessment ? 'Assessment (expected)' : 'Submission (needs treasurer confirmation)'}</dd></div>
          </dl>
        </ConfirmDialog>
      ) : null}
    </form>
  )
}

function SchedulesSection(): React.JSX.Element {
  const { group } = useSelectedGroup()
  const list = useAsync(() => finance.schedules(group.id, 0), [group.id])
  const [creating, setCreating] = useState(false)
  return (
    <section className="card" aria-label="Contribution schedules">
      <div className="page-head">
        <h2>Contribution schedules</h2>
        <button type="button" className="btn btn-secondary" onClick={() => setCreating((c) => !c)}>
          {creating ? 'Close' : 'New schedule'}
        </button>
      </div>
      {creating ? <ScheduleForm onDone={() => { setCreating(false); list.reload() }} /> : null}
      {list.loading ? <p className="muted">Loading…</p> : null}
      {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
      <ul className="list">
        {(list.data?.content ?? []).map((s) => (
          <li key={s.id}>
            <strong>{s.title}</strong> · <Money amount={s.amount} currency={s.currency} /> · {s.frequency}
            <br />
            <span className="muted small">
              From {formatDate(s.startDate)}{s.endDate ? ` to ${formatDate(s.endDate)}` : ''}
              {s.dueDay ? ` · due day ${s.dueDay}` : ''}
            </span>
          </li>
        ))}
      </ul>
      {list.data && list.data.content.length === 0 && !list.loading ? (
        <p className="muted">No schedules yet.</p>
      ) : null}
    </section>
  )
}

function SchedulesReadOnly(): React.JSX.Element {
  const { group } = useSelectedGroup()
  const list = useAsync(() => finance.schedules(group.id, 0), [group.id])
  return (
    <section className="card" aria-label="Contribution schedules">
      <h2>Contribution schedules</h2>
      {list.loading ? <p className="muted">Loading…</p> : null}
      {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
      <ul className="list">
        {(list.data?.content ?? []).map((s) => (
          <li key={s.id}>
            <strong>{s.title}</strong> · <Money amount={s.amount} currency={s.currency} /> · {s.frequency}
          </li>
        ))}
      </ul>
      {list.data && list.data.content.length === 0 && !list.loading ? (
        <p className="muted">No schedules published yet.</p>
      ) : null}
    </section>
  )
}

function ScheduleForm({ onDone }: { onDone: () => void }): React.JSX.Element {
  const { group } = useSelectedGroup()
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState('')
  const [frequency, setFrequency] = useState('MONTHLY')
  const [dueDay, setDueDay] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const errors: Record<string, string> = {}
    if (!title.trim()) errors.title = 'Give the schedule a title.'
    const parsed = parseAmountInput(amount)
    if (!parsed.ok) errors.amount = parsed.error
    if (!startDate) errors.startDate = 'Choose a start date.'
    const due = dueDay ? Number(dueDay) : undefined
    if (due !== undefined && (!Number.isInteger(due) || due < 1 || due > 31)) errors.dueDay = 'Due day must be 1–31.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0 || !parsed.ok) return
    setBusy(true)
    setError(null)
    try {
      await finance.createSchedule(group.id, {
        title: title.trim(),
        amount: parsed.value,
        frequency,
        dueDay: due,
        startDate,
        endDate: endDate || undefined,
      })
      onDone()
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setFieldErrors(err.fieldErrors)
      setError(err instanceof ApiError ? err.message : 'Could not create the schedule.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form-card" onSubmit={submit} noValidate>
      <h3>New schedule</h3>
      <Field label="Title" error={fieldErrors.title} required>
        <input className="input" type="text" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} />
      </Field>
      <div className="form-row">
        <Field label={`Amount (${group.currency})`} error={fieldErrors.amount} required>
          <input className="input" type="text" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label="Frequency" required>
          <select className="input" value={frequency} onChange={(e) => setFrequency(e.target.value)}>
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
            <option value="CUSTOM">Custom</option>
          </select>
        </Field>
        <Field label="Due day (optional)" error={fieldErrors.dueDay}>
          <input className="input input-short" type="number" min={1} max={31} value={dueDay} onChange={(e) => setDueDay(e.target.value)} />
        </Field>
      </div>
      <div className="form-row">
        <Field label="Start date" error={fieldErrors.startDate} required>
          <input className="input" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </Field>
        <Field label="End date (optional)">
          <input className="input" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        </Field>
      </div>
      <FormError message={error} />
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {busy ? 'Creating…' : 'Create schedule'}
      </button>
    </form>
  )
}

function LedgerJournal(): React.JSX.Element {
  const { group } = useSelectedGroup()
  const [page, setPage] = useState(0)
  const list = useAsync(() => finance.ledgerEntries(group.id, page), [group.id, page])
  return (
    <section className="card" aria-label="Ledger journal">
      <h2>Ledger journal</h2>
      <p className="muted small">Immutable record of confirmed money movements.</p>
      {list.loading ? <p className="muted">Loading…</p> : null}
      {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
      <ul className="list">
        {(list.data?.content ?? []).map((e) => (
          <li key={e.id}>
            {e.kind.replaceAll('_', ' ')} · <Money amount={e.amount} currency={e.currency} />
            <br />
            <span className="muted small"><DateTime iso={e.occurredAt} /></span>
          </li>
        ))}
      </ul>
      {list.data ? (
        <Pagination page={list.data.number} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
      ) : null}
    </section>
  )
}
