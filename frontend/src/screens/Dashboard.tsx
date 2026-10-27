/**
 * Dashboard: authorized-only summary of the selected group — upcoming
 * meetings, open votes, the member's own contribution position, pending
 * requests, and quick actions. Every figure comes from the backend.
 */
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { finance, meetings, membership, statements, votes } from '../api/resources'
import { useAuth } from '../auth/AuthContext'
import { useGroups } from '../groups/GroupContext'
import { formatDate, formatDateTime } from '../components/format'
import {
  DateTime,
  EmptyState,
  ErrorState,
  LoadingState,
  Money,
  StatusPill,
  useAsync,
} from '../components/ui'
import { useAssistant } from '../ai/AssistantContext'

export function Dashboard(): React.JSX.Element {
  const { profile } = useAuth()
  const { selected, loading: groupsLoading, error: groupsError, refresh, can } = useGroups()
  const { setOpen } = useAssistant()

  const ledger = useAsync(
    () => (selected ? finance.ledgerSummary(selected.id) : Promise.reject(new Error('no group'))),
    [selected?.id],
  )
  const upcomingMeetings = useAsync(
    () =>
      selected
        ? meetings.list(selected.id, 0).then((p) =>
            p.content
              .filter((m) => m.status === 'SCHEDULED' && new Date(m.startsAt) >= new Date())
              .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
              .slice(0, 3),
          )
        : Promise.reject(new Error('no group')),
    [selected?.id],
  )
  const openVotes = useAsync(
    () =>
      selected
        ? votes.list(selected.id, 0).then((p) => p.content.filter((v) => v.status === 'OPEN').slice(0, 3))
        : Promise.reject(new Error('no group')),
    [selected?.id],
  )
  const myContributions = useAsync(
    () =>
      selected && profile
        ? finance
            .contributions(selected.id, { memberUserId: profile.id, page: 0 })
            .then((p) => p.content.slice(0, 5))
        : Promise.reject(new Error('no group')),
    [selected?.id, profile?.id],
  )
  const pendingRequests = useAsync(
    () =>
      selected && can('MEMBERS_MANAGE')
        ? membership.listRequests(selected.id, 'PENDING', 0).then((p) => p.content.slice(0, 5))
        : Promise.resolve([]),
    [selected?.id, can],
  )
  const myStatements = useAsync(
    () =>
      selected && profile
        ? statements
            .list(selected.id, 0)
            .then((p) => p.content.filter((r) => r.requesterUserId === profile.id).slice(0, 3))
        : Promise.reject(new Error('no group')),
    [selected?.id, profile?.id],
  )

  if (groupsLoading) return <LoadingState label="Loading your groups…" />
  if (groupsError) return <ErrorState message={groupsError} onRetry={() => void refresh()} />
  if (!selected) {
    return (
      <div>
        <h1>Welcome, {profile?.displayName}</h1>
        <EmptyState
          title="You are not in a savings group yet"
          hint="Create a group to get started, or accept an invitation from your group admin."
          action={<Link to="/groups" className="btn btn-primary">Go to Groups</Link>}
        />
      </div>
    )
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>{selected.name}</h1>
          <p className="muted">
            {selected.currency} · Your role: {selected.currentUserRole}
          </p>
        </div>
        <button type="button" className="btn btn-secondary" onClick={() => setOpen(true)}>
          Ask the assistant about this group
        </button>
      </div>

      <div className="card-grid">
        <section className="card" aria-label="My contribution position">
          <h2>My contributions</h2>
          {ledger.loading ? (
            <p className="muted">Loading…</p>
          ) : ledger.error ? (
            <p className="form-error" role="alert">{ledger.error}</p>
          ) : ledger.data ? (
            <dl className="kv">
              <div className="kv-row">
                <dt>Expected of me</dt>
                <dd><Money amount={ledger.data.myExpectedTotal} currency={ledger.data.currency} /></dd>
              </div>
              <div className="kv-row">
                <dt>Confirmed as received</dt>
                <dd><Money amount={ledger.data.myConfirmedTotal} currency={ledger.data.currency} /></dd>
              </div>
              <div className="kv-row">
                <dt>Group confirmed total</dt>
                <dd><Money amount={ledger.data.confirmedContributionsTotal} currency={ledger.data.currency} /></dd>
              </div>
              <div className="kv-row">
                <dt>As of</dt>
                <dd><DateTime iso={ledger.data.asOf} /></dd>
              </div>
            </dl>
          ) : null}
          <p className="muted small">
            “Expected” is what the group asked for; “confirmed” is what a treasurer recorded as
            received. Only a live payment provider could prove money moved — Nuru records decisions.
          </p>
          <Link to="/contributions" className="btn btn-ghost btn-small">View contributions</Link>
        </section>

        <section className="card" aria-label="Upcoming meetings">
          <h2>Upcoming meetings</h2>
          {upcomingMeetings.loading ? (
            <p className="muted">Loading…</p>
          ) : upcomingMeetings.error ? (
            <p className="form-error" role="alert">{upcomingMeetings.error}</p>
          ) : upcomingMeetings.data && upcomingMeetings.data.length > 0 ? (
            <ul className="list">
              {upcomingMeetings.data.map((m) => (
                <li key={m.id}>
                  <Link to={`/meetings/${m.id}`}>{m.title}</Link>
                  <br />
                  <span className="muted small">
                    <DateTime iso={m.startsAt} /> · {m.timezone}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No upcoming meetings scheduled.</p>
          )}
          <Link to="/meetings" className="btn btn-ghost btn-small">View meetings</Link>
        </section>

        <section className="card" aria-label="Open votes">
          <h2>Open votes</h2>
          {openVotes.loading ? (
            <p className="muted">Loading…</p>
          ) : openVotes.error ? (
            <p className="form-error" role="alert">{openVotes.error}</p>
          ) : openVotes.data && openVotes.data.length > 0 ? (
            <ul className="list">
              {openVotes.data.map((v) => (
                <li key={v.id}>
                  <Link to={`/votes/${v.id}`}>{v.question}</Link>
                  <br />
                  <span className="muted small">
                    Closes <DateTime iso={v.closesAt} /> · {v.voted ? 'You voted' : 'You have not voted'}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No votes are open right now.</p>
          )}
          <Link to="/votes" className="btn btn-ghost btn-small">View votes</Link>
        </section>

        <section className="card" aria-label="My recent contributions">
          <h2>My recent contributions</h2>
          {myContributions.loading ? (
            <p className="muted">Loading…</p>
          ) : myContributions.error ? (
            <p className="form-error" role="alert">{myContributions.error}</p>
          ) : myContributions.data && myContributions.data.length > 0 ? (
            <ul className="list">
              {myContributions.data.map((c) => (
                <li key={c.id}>
                  <Money amount={c.amount} currency={c.currency} /> · <StatusPill status={c.status} />
                  <br />
                  <span className="muted small">
                    {formatDate(c.periodStart)} – {formatDate(c.periodEnd)} · recorded via{' '}
                    {c.recordedVia === 'MANUAL' ? 'manual entry' : 'sandbox simulation'}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No contributions recorded for you yet.</p>
          )}
        </section>

        {can('MEMBERS_MANAGE') ? (
          <section className="card" aria-label="Pending membership requests">
            <h2>Membership requests</h2>
            {pendingRequests.loading ? (
              <p className="muted">Loading…</p>
            ) : pendingRequests.data && pendingRequests.data.length > 0 ? (
              <ul className="list">
                {pendingRequests.data.map((r) => (
                  <li key={r.id}>
                    {r.displayName} <StatusPill status={r.status} />
                    <br />
                    <span className="muted small">
                      Requested {formatDateTime(r.createdAt)}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted">No pending requests.</p>
            )}
            <Link to="/groups" className="btn btn-ghost btn-small">Review in Groups</Link>
          </section>
        ) : null}

        <section className="card" aria-label="My statement requests">
          <h2>My statements</h2>
          {myStatements.loading ? (
            <p className="muted">Loading…</p>
          ) : myStatements.error ? (
            myStatements.error.includes('denied') ? (
              <p className="muted">Statement history is not available to you.</p>
            ) : (
              <p className="form-error" role="alert">{myStatements.error}</p>
            )
          ) : myStatements.data && myStatements.data.length > 0 ? (
            <ul className="list">
              {myStatements.data.map((r) => (
                <li key={r.id}>
                  {r.requestType === 'PERSONAL_STATEMENT' ? 'Personal statement' : 'Group summary'}{' '}
                  <StatusPill status={r.status} />
                  <br />
                  <span className="muted small">
                    {formatDate(r.periodStart)} – {formatDate(r.periodEnd)}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No statement requests yet.</p>
          )}
          <Link to="/statements" className="btn btn-ghost btn-small">Request a statement</Link>
        </section>
      </div>
    </div>
  )
}

export function requestErrorMessage(err: unknown): string {
  return err instanceof ApiError ? err.message : 'Something went wrong.'
}
