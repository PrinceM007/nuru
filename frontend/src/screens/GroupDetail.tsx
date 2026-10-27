/**
 * Group detail: settings, members, join requests, invitations, roles, audit.
 * Admin-only sections render only when the backend-granted permission allows;
 * the backend still enforces every action.
 */
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { audit, groups, membership } from '../api/resources'
import type { Group, GroupRole, InvitationCreated, Member } from '../api/types'
import { PERMISSION_LABELS, PERMISSIONS } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useGroups } from '../groups/GroupContext'
import { formatDateTime } from '../components/format'
import {
  AccessDenied,
  ConfirmDialog,
  DateTime,
  EmptyState,
  ErrorState,
  Field,
  FormError,
  LoadingState,
  StatusPill,
  useAsync,
} from '../components/ui'

export function GroupDetail(): React.JSX.Element {
  const { groupId = '' } = useParams()
  const navigate = useNavigate()
  const { profile } = useAuth()
  const { can, selectGroup } = useGroups()
  const detail = useAsync(() => groups.detail(groupId), [groupId])

  if (detail.loading) return <LoadingState label="Loading group…" />
  if (detail.error) {
    if (detail.error.toLowerCase().includes('denied') || detail.error.toLowerCase().includes('not found')) {
      return <AccessDenied hint="You are not a member of this group, or it does not exist." />
    }
    return <ErrorState message={detail.error} onRetry={detail.reload} />
  }
  const group = detail.data
  if (!group) return <EmptyState title="Group not found" />

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>{group.name}</h1>
          <p className="muted">
            {group.currency} · your role: {group.currentUserRole}
            {group.description ? ` · ${group.description}` : ''}
          </p>
        </div>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => {
            selectGroup(group.id)
            navigate('/')
          }}
        >
          Set as current group
        </button>
      </div>

      {can('GROUP_SETTINGS_MANAGE') ? (
        <GroupSettings group={group} onSaved={detail.reload} />
      ) : null}

      <MembersSection groupId={group.id} currentUserId={profile?.id ?? ''} />

      {can('MEMBERS_MANAGE') ? <JoinRequestsSection groupId={group.id} /> : null}
      {can('INVITATIONS_MANAGE') ? <InvitationsSection groupId={group.id} /> : null}
      {can('ROLES_MANAGE') ? <RolesSection groupId={group.id} /> : null}
      {can('AUDIT_VIEW') ? <AuditSection groupId={group.id} /> : null}

      <LeaveSection groupId={group.id} role={group.currentUserRole} />
    </div>
  )
}

function GroupSettings({ group, onSaved }: { group: Group; onSaved: () => void }): React.JSX.Element {
  const [name, setName] = useState(group.name)
  const [description, setDescription] = useState(group.description ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await groups.update(group.id, { name: name.trim(), description: description.trim() || undefined })
      setSaved(true)
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save settings.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" aria-label="Group settings">
      <h2>Group settings</h2>
      <form onSubmit={submit} noValidate>
        <Field label="Group name" required>
          <input className="input" type="text" value={name} onChange={(e) => setName(e.target.value)} maxLength={160} />
        </Field>
        <Field label="Description">
          <textarea className="input" value={description} onChange={(e) => setDescription(e.target.value)} rows={2} maxLength={1000} />
        </Field>
        <p className="muted small">Currency {group.currency} cannot be changed after creation.</p>
        <FormError message={error} />
        {saved ? <p role="status">Settings saved.</p> : null}
        <button type="submit" className="btn btn-secondary" disabled={busy}>
          {busy ? 'Saving…' : 'Save settings'}
        </button>
      </form>
    </section>
  )
}

function MembersSection({ groupId, currentUserId }: { groupId: string; currentUserId: string }): React.JSX.Element {
  const { can } = useGroups()
  const members = useAsync(() => groups.members(groupId, 0), [groupId])
  const roles = useAsync(() => (can('ROLES_MANAGE') ? groups.roles(groupId) : Promise.resolve([])), [groupId])
  const [changing, setChanging] = useState<Member | null>(null)
  const [removing, setRemoving] = useState<Member | null>(null)
  const [transferTarget, setTransferTarget] = useState<Member | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const changeRole = async (member: Member, roleId: string): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await groups.changeRole(groupId, member.userId, roleId)
      setChanging(null)
      members.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change the role.')
    } finally {
      setBusy(false)
    }
  }

  const removeMember = async (member: Member): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await groups.removeMember(groupId, member.userId)
      setRemoving(null)
      members.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove the member.')
      setRemoving(null)
    } finally {
      setBusy(false)
    }
  }

  const transferAdmin = async (member: Member): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await groups.transferAdmin(groupId, member.userId)
      setTransferTarget(null)
      members.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not transfer admin.')
      setTransferTarget(null)
    } finally {
      setBusy(false)
    }
  }

  const myRow = members.data?.content.find((m) => m.userId === currentUserId)
  const iAmAdmin = myRow?.role === 'ADMIN'

  return (
    <section className="card" aria-label="Members">
      <h2>Members</h2>
      {members.loading ? <p className="muted">Loading…</p> : null}
      {members.error ? <p className="form-error" role="alert">{members.error}</p> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {members.data ? (
        <ul className="list">
          {members.data.content.map((m) => (
            <li key={m.userId}>
              <strong>{m.displayName}</strong> · {m.role} <StatusPill status={m.status} />
              {m.email ? <span className="muted small"> · {m.email}</span> : null}
              <br />
              <span className="muted small">Joined {formatDateTime(m.joinedAt)}</span>
              {m.userId !== currentUserId ? (
                <div className="row-actions">
                  {can('ROLES_MANAGE') ? (
                    <button type="button" className="btn btn-ghost btn-small" onClick={() => setChanging(m)}>
                      Change role
                    </button>
                  ) : null}
                  {can('MEMBERS_MANAGE') ? (
                    <button type="button" className="btn btn-ghost btn-small" onClick={() => setRemoving(m)}>
                      Remove
                    </button>
                  ) : null}
                  {iAmAdmin && m.role !== 'ADMIN' ? (
                    <button type="button" className="btn btn-ghost btn-small" onClick={() => setTransferTarget(m)}>
                      Make admin (transfer)
                    </button>
                  ) : null}
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}

      {changing ? (
        <ChangeRoleDialog
          member={changing}
          roles={roles.data ?? []}
          busy={busy}
          onCancel={() => setChanging(null)}
          onConfirm={(roleId) => changeRole(changing, roleId)}
        />
      ) : null}
      {removing ? (
        <ConfirmDialog
          title="Remove member"
          confirmLabel="Remove member"
          danger
          busy={busy}
          onCancel={() => setRemoving(null)}
          onConfirm={() => removeMember(removing)}
        >
          <p>
            Remove <strong>{removing.displayName}</strong> from this group? Their recorded history
            stays in the ledger and statements.
          </p>
        </ConfirmDialog>
      ) : null}
      {transferTarget ? (
        <ConfirmDialog
          title={`Make ${transferTarget.displayName} the admin?`}
          confirmLabel="Transfer admin"
          busy={busy}
          onCancel={() => setTransferTarget(null)}
          onConfirm={() => transferAdmin(transferTarget)}
        >
          <p>
            The admin role moves to <strong>{transferTarget.displayName}</strong> and you become an
            ordinary member. This is how the last admin hands over safely.
          </p>
        </ConfirmDialog>
      ) : null}
    </section>
  )
}

function ChangeRoleDialog({
  member,
  roles,
  busy,
  onCancel,
  onConfirm,
}: {
  member: Member
  roles: GroupRole[]
  busy: boolean
  onCancel: () => void
  onConfirm: (roleId: string) => void
}): React.JSX.Element {
  const [roleId, setRoleId] = useState('')
  return (
    <ConfirmDialog
      title={`Change role for ${member.displayName}`}
      confirmLabel="Change role"
      busy={busy}
      onCancel={onCancel}
      onConfirm={() => roleId && onConfirm(roleId)}
    >
      <Field label="New role" required>
        <select className="input" value={roleId} onChange={(e) => setRoleId(e.target.value)}>
          <option value="">Choose a role…</option>
          {roles.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name} ({r.permissions.length} permissions)
            </option>
          ))}
        </select>
      </Field>
      <p className="muted small">
        Demoting the last admin is blocked. Transfer the admin role first from the member list.
      </p>
    </ConfirmDialog>
  )
}

function JoinRequestsSection({ groupId }: { groupId: string }): React.JSX.Element {
  const [status, setStatus] = useState('')
  const list = useAsync(() => membership.listRequests(groupId, status || undefined, 0), [groupId, status])
  const [busyId, setBusyId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const decide = async (id: string, approve: boolean): Promise<void> => {
    setBusyId(id)
    setError(null)
    try {
      if (approve) await membership.approve(groupId, id)
      else await membership.reject(groupId, id)
      list.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not record the decision.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="card" aria-label="Membership requests">
      <h2>Membership requests</h2>
      <Field label="Show">
        <select className="input input-short" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </Field>
      {list.loading ? <p className="muted">Loading…</p> : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {list.data && list.data.content.length === 0 ? (
        <p className="muted">No requests in this state.</p>
      ) : null}
      <ul className="list">
        {(list.data?.content ?? []).map((r) => (
          <li key={r.id}>
            <strong>{r.displayName}</strong> <StatusPill status={r.status} />
            {r.message ? <p className="muted">“{r.message}”</p> : null}
            <p className="muted small">Requested {formatDateTime(r.createdAt)}</p>
            {r.status === 'PENDING' ? (
              <div className="row-actions">
                <button
                  type="button"
                  className="btn btn-primary btn-small"
                  disabled={busyId === r.id}
                  onClick={() => decide(r.id, true)}
                >
                  Approve
                </button>
                <button
                  type="button"
                  className="btn btn-secondary btn-small"
                  disabled={busyId === r.id}
                  onClick={() => decide(r.id, false)}
                >
                  Reject
                </button>
              </div>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
  )
}

function InvitationsSection({ groupId }: { groupId: string }): React.JSX.Element {
  const list = useAsync(() => membership.listInvitations(groupId, 0), [groupId])
  const roles = useAsync(() => groups.roles(groupId), [groupId])
  const [roleId, setRoleId] = useState('')
  const [expiresInHours, setExpiresInHours] = useState('72')
  const [maxUses, setMaxUses] = useState('5')
  const [created, setCreated] = useState<InvitationCreated | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setCreated(null)
    try {
      const invitation = await membership.createInvitation(groupId, {
        roleId: roleId || undefined,
        expiresInHours: Number(expiresInHours) || undefined,
        maxUses: Number(maxUses) || undefined,
      })
      setCreated(invitation)
      list.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the invitation.')
    } finally {
      setBusy(false)
    }
  }

  const revoke = async (id: string): Promise<void> => {
    setError(null)
    try {
      await membership.revokeInvitation(groupId, id)
      list.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not revoke the invitation.')
    }
  }

  return (
    <section className="card" aria-label="Invitations">
      <h2>Invitations</h2>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <form onSubmit={create} className="form-row">
        <Field label="Role for new members">
          <select className="input" value={roleId} onChange={(e) => setRoleId(e.target.value)}>
            <option value="">Default (member)</option>
            {(roles.data ?? []).map((r) => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>
        </Field>
        <Field label="Expires in (hours)" hint="1–720">
          <input className="input input-short" type="number" min={1} max={720} value={expiresInHours} onChange={(e) => setExpiresInHours(e.target.value)} />
        </Field>
        <Field label="Max uses" hint="1–100">
          <input className="input input-short" type="number" min={1} max={100} value={maxUses} onChange={(e) => setMaxUses(e.target.value)} />
        </Field>
        <button type="submit" className="btn btn-secondary" disabled={busy}>
          {busy ? 'Creating…' : 'Create invitation'}
        </button>
      </form>
      {created ? (
        <div className="notice" role="status">
          <p>
            <strong>Share this code now — it is shown only once:</strong>
          </p>
          <p className="code">{created.code}</p>
          <p className="muted small">
            Role {created.role} · expires {formatDateTime(created.expiresAt)} · {created.maxUses} uses
          </p>
        </div>
      ) : null}
      {list.loading ? <p className="muted">Loading…</p> : null}
      <ul className="list">
        {(list.data?.content ?? []).map((inv) => (
          <li key={inv.id}>
            Role {inv.role} · {inv.useCount}/{inv.maxUses} used · expires {formatDateTime(inv.expiresAt)}
            {inv.revokedAt ? ' · revoked' : null}
            {inv.revokedAt ? null : (
              <div className="row-actions">
                <button type="button" className="btn btn-ghost btn-small" onClick={() => revoke(inv.id)}>
                  Revoke
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

function RolesSection({ groupId }: { groupId: string }): React.JSX.Element {
  const list = useAsync(() => groups.roles(groupId), [groupId])
  const [name, setName] = useState('')
  const [selectedPerms, setSelectedPerms] = useState<Set<string>>(new Set())
  const [editing, setEditing] = useState<GroupRole | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const togglePerm = (perm: string): void => {
    setSelectedPerms((prev) => {
      const next = new Set(prev)
      if (next.has(perm)) next.delete(perm)
      else next.add(perm)
      return next
    })
  }

  const create = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    if (!name.trim() || selectedPerms.size === 0) {
      setError('Give the role a name and at least one permission.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await groups.createRole(groupId, { name: name.trim(), permissions: [...selectedPerms] })
      setName('')
      setSelectedPerms(new Set())
      list.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the role.')
    } finally {
      setBusy(false)
    }
  }

  const saveEdit = async (): Promise<void> => {
    if (!editing || selectedPerms.size === 0) {
      setError('A role needs at least one permission.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await groups.updateRole(groupId, editing.id, [...selectedPerms])
      setEditing(null)
      setSelectedPerms(new Set())
      list.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update the role.')
    } finally {
      setBusy(false)
    }
  }

  const remove = async (role: GroupRole): Promise<void> => {
    setError(null)
    try {
      await groups.deleteRole(groupId, role.id)
      list.reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete the role.')
    }
  }

  return (
    <section className="card" aria-label="Roles and permissions">
      <h2>Roles and permissions</h2>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      {list.loading ? <p className="muted">Loading…</p> : null}
      <ul className="list">
        {(list.data ?? []).map((r) => (
          <li key={r.id}>
            <strong>{r.name}</strong> {r.system ? <span className="muted small">(built-in)</span> : null}
            <ul className="perm-list">
              {r.permissions.map((p) => (
                <li key={p}>{PERMISSION_LABELS[p] ?? p}</li>
              ))}
            </ul>
            {!r.system ? (
              <div className="row-actions">
                <button
                  type="button"
                  className="btn btn-ghost btn-small"
                  onClick={() => { setEditing(r); setSelectedPerms(new Set(r.permissions)) }}
                >
                  Edit
                </button>
                <button type="button" className="btn btn-ghost btn-small" onClick={() => remove(r)}>
                  Delete
                </button>
              </div>
            ) : null}
          </li>
        ))}
      </ul>
      <h3>{editing ? `Edit ${editing.name}` : 'Create a custom role'}</h3>
      <form onSubmit={editing ? (e) => { e.preventDefault(); void saveEdit() } : create}>
        {editing ? null : (
          <Field label="Role name" required>
            <input className="input" type="text" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} />
          </Field>
        )}
        <fieldset>
          <legend>Permissions</legend>
          {PERMISSIONS.map((p) => (
            <label key={p} className="check">
              <input type="checkbox" checked={selectedPerms.has(p)} onChange={() => togglePerm(p)} />
              {PERMISSION_LABELS[p]}
            </label>
          ))}
        </fieldset>
        <div className="row-actions">
          <button type="submit" className="btn btn-secondary" disabled={busy}>
            {busy ? 'Saving…' : editing ? 'Save role' : 'Create role'}
          </button>
          {editing ? (
            <button type="button" className="btn btn-ghost" onClick={() => { setEditing(null); setSelectedPerms(new Set()) }}>
              Cancel
            </button>
          ) : null}
        </div>
      </form>
    </section>
  )
}

function AuditSection({ groupId }: { groupId: string }): React.JSX.Element {
  const [page, setPage] = useState(0)
  const list = useAsync(() => audit.events(groupId, page), [groupId, page])
  return (
    <section className="card" aria-label="Audit trail">
      <h2>Audit trail</h2>
      <p className="muted small">
        Append-only record of membership, financial, vote, statement, and AI-assisted actions.
      </p>
      {list.loading ? <p className="muted">Loading…</p> : null}
      {list.error ? <p className="form-error" role="alert">{list.error}</p> : null}
      <ul className="list">
        {(list.data?.content ?? []).map((e) => (
          <li key={e.id}>
            <strong>{e.action}</strong> · {e.targetType} · {e.outcome}
            <br />
            <span className="muted small">
              <DateTime iso={e.occurredAt} /> · {e.details}
            </span>
          </li>
        ))}
      </ul>
      {list.data && list.data.totalPages > 1 ? (
        <div className="row-actions">
          <button type="button" className="btn btn-secondary btn-small" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            ← Previous
          </button>
          <span className="muted small">Page {page + 1} of {list.data.totalPages}</span>
          <button type="button" className="btn btn-secondary btn-small" disabled={page >= list.data.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
            Next →
          </button>
        </div>
      ) : null}
    </section>
  )
}

function LeaveSection({ groupId, role }: { groupId: string; role: string }): React.JSX.Element {
  const navigate = useNavigate()
  const { refresh } = useGroups()
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const leave = async (): Promise<void> => {
    setBusy(true)
    setError(null)
    try {
      await groups.leave(groupId)
      await refresh()
      navigate('/groups')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not leave the group.')
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card" aria-label="Leave group">
      <h2>Leave group</h2>
      {role === 'ADMIN' ? (
        <p className="muted">
          If you are the last admin, use “Make admin (transfer)” on another member above before
          leaving.
        </p>
      ) : null}
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <button type="button" className="btn btn-danger" onClick={() => setConfirming(true)}>
        Leave this group
      </button>
      {confirming ? (
        <ConfirmDialog
          title="Leave this group?"
          confirmLabel="Leave group"
          danger
          busy={busy}
          onCancel={() => setConfirming(false)}
          onConfirm={() => leave()}
        >
          <p>You will lose access to this group’s information. Your recorded history stays.</p>
        </ConfirmDialog>
      ) : null}
    </section>
  )
}
