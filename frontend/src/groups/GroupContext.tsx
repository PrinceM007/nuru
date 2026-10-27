import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../api/client'
import { groups } from '../api/resources'
import type { Group, GroupRole } from '../api/types'
import { useAuth } from '../auth/AuthContext'

const SELECTED_KEY = 'nuru.selectedGroupId'

interface GroupState {
  groupsList: Group[]
  selected: Group | null
  roles: GroupRole[]
  /** Permission names granted to the current user in the selected group. */
  permissions: Set<string>
  loading: boolean
  error: string | null
  selectGroup: (groupId: string) => void
  refresh: () => Promise<void>
  can: (permission: string) => boolean
  isAdmin: boolean
}

const GroupContext = createContext<GroupState | null>(null)

export function GroupProvider({ children }: { children: ReactNode }): React.JSX.Element {
  const { profile } = useAuth()
  const [groupsList, setGroupsList] = useState<Group[]>([])
  const [selected, setSelected] = useState<Group | null>(null)
  const [roles, setRoles] = useState<GroupRole[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    if (!profile) {
      setGroupsList([])
      setSelected(null)
      setRoles([])
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const page = await groups.list(0)
      setGroupsList(page.content)
      const stored = localStorage.getItem(SELECTED_KEY)
      const target = page.content.find((g) => g.id === stored) ?? page.content[0] ?? null
      if (!target) {
        setSelected(null)
        setRoles([])
        localStorage.removeItem(SELECTED_KEY)
        return
      }
      localStorage.setItem(SELECTED_KEY, target.id)
      const [detail, roleList] = await Promise.all([
        groups.detail(target.id),
        groups.roles(target.id),
      ])
      setSelected(detail)
      setRoles(roleList)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load your groups.')
    } finally {
      setLoading(false)
    }
  }, [profile])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const selectGroup = useCallback(
    (groupId: string) => {
      localStorage.setItem(SELECTED_KEY, groupId)
      const target = groupsList.find((g) => g.id === groupId)
      if (!target) {
        void refresh()
        return
      }
      setLoading(true)
      setError(null)
      Promise.all([groups.detail(groupId), groups.roles(groupId)])
        .then(([detail, roleList]) => {
          setSelected(detail)
          setRoles(roleList)
        })
        .catch((err: unknown) => {
          setError(err instanceof ApiError ? err.message : 'Could not load this group.')
        })
        .finally(() => setLoading(false))
    },
    [groupsList, refresh],
  )

  const permissions = useMemo(() => {
    if (!selected) return new Set<string>()
    if (selected.currentUserRole === 'ADMIN') {
      // ADMIN holds every permission; enumerate the known set for `can()`.
      return new Set<string>(roles.flatMap((r) => [...r.permissions]))
    }
    const mine = roles.find((r) => r.name === selected.currentUserRole)
    return new Set<string>(mine?.permissions ?? [])
  }, [selected, roles])

  const can = useCallback((permission: string) => permissions.has(permission), [permissions])

  const value = useMemo<GroupState>(
    () => ({
      groupsList,
      selected,
      roles,
      permissions,
      loading,
      error,
      selectGroup,
      refresh,
      can,
      isAdmin: selected?.currentUserRole === 'ADMIN',
    }),
    [groupsList, selected, roles, permissions, loading, error, selectGroup, refresh, can],
  )
  return <GroupContext.Provider value={value}>{children}</GroupContext.Provider>
}

export function useGroups(): GroupState {
  const ctx = useContext(GroupContext)
  if (!ctx) throw new Error('useGroups must be used within GroupProvider')
  return ctx
}

/** Requires a selected group; screens render a "choose a group" state otherwise. */
export function useSelectedGroup(): { group: Group; can: (p: string) => boolean; isAdmin: boolean } {
  const { selected, can, isAdmin } = useGroups()
  if (!selected) throw new Error('No group selected')
  return { group: selected, can, isAdmin }
}
