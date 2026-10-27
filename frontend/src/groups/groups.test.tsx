import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { GroupProvider, useGroups } from './GroupContext'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function pageOf<T>(content: T[]): object {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20, first: true, last: true }
}

const ROLES = [
  { id: 'r-admin', name: 'ADMIN', system: true, permissions: ['GROUP_SETTINGS_MANAGE', 'MEMBERS_MANAGE'] },
  { id: 'r-treasurer', name: 'TREASURER', system: true, permissions: ['CONTRIBUTIONS_CONFIRM', 'LEDGER_VIEW_FULL'] },
  { id: 'r-member', name: 'MEMBER', system: true, permissions: [] },
]

function Probe(): React.JSX.Element {
  const { selected, loading, can } = useGroups()
  if (loading) return <p>loading groups</p>
  return (
    <div>
      <p>group:{selected?.name ?? 'none'}</p>
      <p>confirm:{String(can('CONTRIBUTIONS_CONFIRM'))}</p>
      <p>ledger:{String(can('LEDGER_VIEW_FULL'))}</p>
    </div>
  )
}

function mockBackend(role: string): void {
  const group = { id: 'g1', name: 'Ubuntu Stokvel', description: null, currency: 'ZAR', createdAt: '2026-01-01T00:00:00Z', currentUserRole: role }
  const fetchMock = vi.fn((url: string | URL | Request) => {
    const path = String(url)
    if (path.endsWith('/api/v1/users/me')) {
      return Promise.resolve(jsonResponse({ id: 'u1', email: 'm@example.org', displayName: 'Member', phone: null, createdAt: '2026-01-01T00:00:00Z' }))
    }
    if (path.includes('/api/v1/groups?')) return Promise.resolve(jsonResponse(pageOf([group])))
    if (path.endsWith('/api/v1/groups/g1/roles')) return Promise.resolve(jsonResponse(ROLES))
    if (path.endsWith('/api/v1/groups/g1')) return Promise.resolve(jsonResponse(group))
    return Promise.resolve(new Response(null, { status: 404 }))
  })
  vi.stubGlobal('fetch', fetchMock)
  localStorage.setItem('nuru.accessToken', 'test-access')
  localStorage.setItem('nuru.refreshToken', 'test-refresh')
}

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('GroupProvider permissions', () => {
  it('grants a treasurer only their explicit permissions', async () => {
    mockBackend('TREASURER')
    render(
      <AuthProvider>
        <GroupProvider>
          <Probe />
        </GroupProvider>
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByText('group:Ubuntu Stokvel')).toBeInTheDocument())
    expect(screen.getByText('confirm:true')).toBeInTheDocument()
    expect(screen.getByText('ledger:true')).toBeInTheDocument()
  })

  it('grants an ordinary member no management permissions', async () => {
    mockBackend('MEMBER')
    render(
      <AuthProvider>
        <GroupProvider>
          <Probe />
        </GroupProvider>
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByText('group:Ubuntu Stokvel')).toBeInTheDocument())
    expect(screen.getByText('confirm:false')).toBeInTheDocument()
    expect(screen.getByText('ledger:false')).toBeInTheDocument()
  })

  it('persists the selected group for the next visit', async () => {
    mockBackend('ADMIN')
    render(
      <AuthProvider>
        <GroupProvider>
          <Probe />
        </GroupProvider>
      </AuthProvider>,
    )
    await waitFor(() => expect(localStorage.getItem('nuru.selectedGroupId')).toBe('g1'))
  })
})
