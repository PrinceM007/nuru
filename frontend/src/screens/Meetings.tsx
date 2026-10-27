/**
 * Meetings: list, create/edit with timezone-aware times, attendance,
 * notes, and status transitions.
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { groups, meetings } from '../api/resources'
import type { MeetingInput } from '../api/resources'
import type { Meeting } from '../api/types'
import { useFormDraft } from '../ai/AssistantContext'
import { useAuth } from '../auth/AuthContext'
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

export function Meetings(): React.JSX.Element {
  const { selected, loading } = useGroups()
  const draft = useFormDraft('meeting')
  const [creating, setCreating] = useState(false)

  if (loading) return <LoadingState label="Loading…" />
  if (!selected) return <EmptyState title="Choose a group first" hint="Meetings belong to a savings group." />
  const open = creating || draft !== null
  return (
    <div>
      <MeetingsList key={selected.id} creating={open} setCreating={setCreating} prefill={draft} />
    </div>
  )
}

function MeetingsList({
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
  const list = useAsync(() => meetings.list(group.id, page), [group.id, page])

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Meetings</h1>
          <p className="muted">{group.name} · times shown in your locale</p>
        </div>
        {can('MEETINGS_MANAGE') ? (
          <button type="button" className="btn btn-primary" onClick={() => setCreating(!creating)}>
            {creating ? 'Close' : 'Schedule meeting'}
          </button>
        ) : null}
      </div>
      {creating && can('MEETINGS_MANAGE') ? (
        <MeetingForm prefill={prefill} onDone={() => { setCreating(false); list.reload() }} />
      ) : null}
      <section className="card" aria-label="Meetings">
        {list.loading ? <p className="muted">Loading…</p> : null}
        {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
        {list.data && list.data.content.length === 0 && !list.loading ? (
          <EmptyState title="No meetings yet" hint="Scheduled meetings will appear here." />
        ) : null}
        <ul className="list">
          {(list.data?.content ?? []).map((m) => (
            <li key={m.id}>
              <Link to={`/meetings/${m.id}`}>{m.title}</Link> <StatusPill status={m.status} />
              <br />
              <span className="muted small">
                <DateTime iso={m.startsAt} /> · {m.timezone}
                {m.location ? ` · ${m.location}` : ''}
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

export function MeetingForm({
  prefill,
  initial,
  onDone,
}: {
  prefill?: Record<string, string> | null
  initial?: Meeting
  onDone: () => void
}): React.JSX.Element {
  const { group } = useSelectedGroup()
  const members = useAsync(() => groups.members(group.id, 0), [group.id])
  const [title, setTitle] = useState(initial?.title ?? prefill?.title ?? '')
  const [agenda, setAgenda] = useState(initial?.agenda ?? prefill?.agenda ?? '')
  const [startsAt, setStartsAt] = useState(
    initial ? isoToLocalInput(initial.startsAt) : prefill?.startsAt ? toLocalInput(prefill.startsAt) : '',
  )
  const [endsAt, setEndsAt] = useState(
    initial?.endsAt ? isoToLocalInput(initial.endsAt) : prefill?.endsAt ? toLocalInput(prefill.endsAt) : '',
  )
  const [timezone, setTimezone] = useState(
    initial?.timezone ?? prefill?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
  )
  const [location, setLocation] = useState(initial?.location ?? prefill?.location ?? '')
  const [meetingLink, setMeetingLink] = useState(initial?.meetingLink ?? prefill?.meetingLink ?? '')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [reviewing, setReviewing] = useState(false)

  const parsed = (): MeetingInput | null => {
    const errors: Record<string, string> = {}
    if (!title.trim()) errors.title = 'Give the meeting a title.'
    if (!startsAt) errors.startsAt = 'Choose a start date and time.'
    if (!timezone.trim()) errors.timezone = 'Enter the timezone (IANA name).'
    try {
      if (timezone.trim()) Intl.DateTimeFormat(undefined, { timeZone: timezone.trim() })
    } catch {
      errors.timezone = 'Enter a valid IANA timezone, for example Africa/Johannesburg.'
    }
    if (startsAt && endsAt && new Date(endsAt) <= new Date(startsAt)) {
      errors.endsAt = 'The end must be after the start.'
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return null
    return {
      title: title.trim(),
      agenda: agenda.trim() || undefined,
      startsAt: localInputToIso(startsAt),
      endsAt: endsAt ? localInputToIso(endsAt) : undefined,
      timezone: timezone.trim(),
      location: location.trim() || undefined,
      meetingLink: meetingLink.trim() || undefined,
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
      if (initial) await meetings.update(group.id, initial.id, values)
      else await meetings.create(group.id, values)
      onDone()
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setFieldErrors(err.fieldErrors)
      setError(err instanceof ApiError ? err.message : 'Could not save the meeting.')
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
      <h3>{initial ? 'Edit meeting' : 'Schedule a meeting'}</h3>
      {prefill && !initial ? <p className="muted small">Prefilled from your assistant conversation — review before saving.</p> : null}
      <Field label="Title" error={fieldErrors.title} required>
        <input className="input" type="text" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
      </Field>
      <Field label="Agenda (optional)" error={fieldErrors.agenda}>
        <textarea className="input" value={agenda} onChange={(e) => setAgenda(e.target.value)} rows={3} maxLength={2000} />
      </Field>
      <div className="form-row">
        <Field label="Starts" error={fieldErrors.startsAt} required>
          <input className="input" type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} />
        </Field>
        <Field label="Ends (optional)" error={fieldErrors.endsAt}>
          <input className="input" type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} />
        </Field>
        <Field label="Timezone" hint="IANA name, e.g. Africa/Johannesburg." error={fieldErrors.timezone} required>
          <input className="input" type="text" value={timezone} onChange={(e) => setTimezone(e.target.value)} maxLength={64} />
        </Field>
      </div>
      <div className="form-row">
        <Field label="Location (optional)" error={fieldErrors.location}>
          <input className="input" type="text" value={location} onChange={(e) => setLocation(e.target.value)} maxLength={500} />
        </Field>
        <Field label="Meeting link (optional)" error={fieldErrors.meetingLink}>
          <input className="input" type="url" value={meetingLink} onChange={(e) => setMeetingLink(e.target.value)} maxLength={500} />
        </Field>
      </div>
      <p className="muted small">
        {members.data ? `All ${members.data.totalElements} active members are invited by default.` : 'Loading member count…'}
      </p>
      <FormError message={error} />
      <button type="submit" className="btn btn-primary">Review meeting</button>
      {reviewing ? (
        <ConfirmDialog
          title={initial ? 'Save these changes?' : 'Schedule this meeting?'}
          confirmLabel={initial ? 'Save changes' : 'Schedule meeting'}
          busy={busy}
          onCancel={() => setReviewing(false)}
          onConfirm={() => submit()}
        >
          <dl className="kv">
            <div className="kv-row"><dt>Title</dt><dd>{title}</dd></div>
            <div className="kv-row"><dt>Starts</dt><dd>{startsAt.replace('T', ' ')} ({timezone})</dd></div>
            {endsAt ? <div className="kv-row"><dt>Ends</dt><dd>{endsAt.replace('T', ' ')}</dd></div> : null}
            {location ? <div className="kv-row"><dt>Location</dt><dd>{location}</dd></div> : null}
          </dl>
          <p className="muted">Invitees are notified by email and SMS where configured.</p>
        </ConfirmDialog>
      ) : null}
    </form>
  )
}

function toLocalInput(iso: string): string {
  try {
    return isoToLocalInput(iso)
  } catch {
    return ''
  }
}

export function MeetingDetail(): React.JSX.Element {
  const { meetingId = '' } = useParams()
  const { group, can } = useSelectedGroup()
  const { profile } = useAuth()
  const detail = useAsync(() => meetings.detail(group.id, meetingId), [group.id, meetingId])
  const [editing, setEditing] = useState(false)
  const [action, setAction] = useState<string | null>(null)
  const [noteBody, setNoteBody] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (detail.loading) return <LoadingState label="Loading meeting…" />
  if (detail.error) return <ErrorState message={detail.error} onRetry={detail.reload} />
  const meeting = detail.data
  if (!meeting) return <EmptyState title="Meeting not found" />
  const myAttendance = meeting.participants.find((p) => p.userId === profile?.id)?.attendance

  const run = async (fn: () => Promise<unknown>): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      setAction(null)
      setNoteBody('')
      detail.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The action could not be completed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <p><Link to="/meetings">← Back to meetings</Link></p>
      <h1>{meeting.title}</h1>
      <p><StatusPill status={meeting.status} /></p>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {editing && can('MEETINGS_MANAGE') && meeting.status === 'SCHEDULED' ? (
        <MeetingForm initial={meeting} onDone={() => { setEditing(false); detail.reload() }} />
      ) : (
        <section className="card" aria-label="Meeting details">
          <dl className="kv">
            <div className="kv-row"><dt>Starts</dt><dd><DateTime iso={meeting.startsAt} /> ({meeting.timezone})</dd></div>
            {meeting.endsAt ? <div className="kv-row"><dt>Ends</dt><dd><DateTime iso={meeting.endsAt} /></dd></div> : null}
            {meeting.location ? <div className="kv-row"><dt>Location</dt><dd>{meeting.location}</dd></div> : null}
            {meeting.meetingLink ? (
              <div className="kv-row"><dt>Link</dt><dd><a href={meeting.meetingLink} target="_blank" rel="noreferrer">{meeting.meetingLink}</a></dd></div>
            ) : null}
            {meeting.agenda ? <div className="kv-row"><dt>Agenda</dt><dd>{meeting.agenda}</dd></div> : null}
          </dl>
          {can('MEETINGS_MANAGE') && meeting.status === 'SCHEDULED' ? (
            <div className="row-actions">
              <button type="button" className="btn btn-secondary btn-small" onClick={() => setEditing(true)}>Edit</button>
              <button type="button" className="btn btn-secondary btn-small" onClick={() => setAction('complete')}>Mark completed</button>
              <button type="button" className="btn btn-ghost btn-small" onClick={() => setAction('cancel')}>Cancel meeting</button>
            </div>
          ) : null}
        </section>
      )}

      <section className="card" aria-label="Attendance">
        <h2>Attendance</h2>
        <ul className="list">
          {meeting.participants.map((p) => (
            <li key={p.userId}>
              {p.displayName} <StatusPill status={p.attendance} />
            </li>
          ))}
        </ul>
        {meeting.status !== 'CANCELLED' ? (
          <div className="row-actions">
            <span className="muted small">Your attendance{myAttendance ? `: ${myAttendance}` : ''} —</span>
            {(['PRESENT', 'ABSENT', 'APOLOGY'] as const).map((value) => (
              <button
                key={value}
                type="button"
                className="btn btn-ghost btn-small"
                disabled={busy}
                onClick={() => run(() => meetings.attendance(group.id, meeting.id, profile?.id ?? '', value))}
              >
                {value === 'PRESENT' ? 'I’ll attend' : value === 'ABSENT' ? 'Can’t attend' : 'Send apology'}
              </button>
            ))}
          </div>
        ) : null}
      </section>

      <section className="card" aria-label="Meeting notes">
        <h2>Notes</h2>
        {(meeting.notes ?? []).length === 0 ? <p className="muted">No notes yet.</p> : null}
        <ul className="list">
          {(meeting.notes ?? []).map((n) => (
            <li key={n.id}>
              <p>{n.body}</p>
              <p className="muted small">
                {n.authorDisplayName} · <DateTime iso={n.createdAt} />
                {(n.authorUserId === profile?.id || can('MEETINGS_MANAGE')) && (
                  <button
                    type="button"
                    className="btn btn-ghost btn-small"
                    disabled={busy}
                    onClick={() => run(() => meetings.deleteNote(group.id, meeting.id, n.id))}
                  >
                    Delete
                  </button>
                )}
              </p>
            </li>
          ))}
        </ul>
        <form
          onSubmit={(e: FormEvent) => {
            e.preventDefault()
            if (noteBody.trim()) void run(() => meetings.addNote(group.id, meeting.id, noteBody.trim()))
          }}
        >
          <Field label="Add a note">
            <textarea className="input" rows={3} value={noteBody} onChange={(e) => setNoteBody(e.target.value)} maxLength={20000} />
          </Field>
          <button type="submit" className="btn btn-secondary btn-small" disabled={busy || !noteBody.trim()}>
            Add note
          </button>
        </form>
      </section>

      {action === 'complete' ? (
        <ConfirmDialog title="Mark this meeting completed?" confirmLabel="Mark completed" busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => meetings.complete(group.id, meeting.id))}>
          <p>The meeting moves to completed. Attendance can still be corrected afterwards.</p>
        </ConfirmDialog>
      ) : null}
      {action === 'cancel' ? (
        <ConfirmDialog title="Cancel this meeting?" confirmLabel="Cancel meeting" danger busy={busy} onCancel={() => setAction(null)} onConfirm={() => run(() => meetings.cancel(group.id, meeting.id))}>
          <p>Invitees are notified of the cancellation.</p>
        </ConfirmDialog>
      ) : null}
    </div>
  )
}
