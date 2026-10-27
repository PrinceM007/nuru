import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { App } from './App'
import { AssistantProvider } from './ai/AssistantContext'
import { AuthProvider } from './auth/AuthContext'
import { GroupProvider } from './groups/GroupContext'
import { ThemeProvider } from './theme/ThemeContext'

afterEach(() => {
  localStorage.clear()
})

function renderApp(route: string): void {
  render(
    <MemoryRouter initialEntries={[route]}>
      <ThemeProvider>
        <AuthProvider>
          <GroupProvider>
            <AssistantProvider>
              <App />
            </AssistantProvider>
          </GroupProvider>
        </AuthProvider>
      </ThemeProvider>
    </MemoryRouter>,
  )
}

describe('App routing', () => {
  it('renders the sign-in screen with accessible labels', async () => {
    renderApp('/sign-in')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Email/)).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
  })

  it('redirects unauthenticated visitors to sign-in', async () => {
    renderApp('/loans')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('renders the registration screen', async () => {
    renderApp('/register')
    expect(await screen.findByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
  })
})
