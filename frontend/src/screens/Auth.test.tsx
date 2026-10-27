import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { Register, SignIn } from './Auth'

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

function renderAuth(route: string): void {
  render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        {route === '/register' ? <Register /> : <SignIn />}
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('SignIn', () => {
  it('shows field-level validation and preserves input after errors', async () => {
    const user = userEvent.setup()
    renderAuth('/sign-in')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter your email address.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()

    await user.type(screen.getByLabelText(/Email/), 'member@example.org')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(screen.queryByText('Enter your email address.')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/Email/)).toHaveValue('member@example.org')
  })

  it('signs in and stores the session', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 1800 }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ id: 'u1', email: 'm@example.org', displayName: 'Member', phone: null, createdAt: '2026-01-01T00:00:00Z' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    renderAuth('/sign-in')
    await user.type(screen.getByLabelText(/Email/), 'm@example.org')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(localStorage.getItem('nuru.accessToken')).toBe('a'))
  })
})

describe('Register', () => {
  it('requires a name and a sufficiently long password', async () => {
    const user = userEvent.setup()
    renderAuth('/register')
    await user.type(screen.getByLabelText(/Email/), 'new@example.org')
    await user.type(screen.getByLabelText('Password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Enter the name other members will see.')).toBeInTheDocument()
    expect(screen.getByText('Use at least 8 characters.')).toBeInTheDocument()
  })
})
