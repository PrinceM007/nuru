/**
 * Application shell: monochrome sidebar/top-bar navigation, current group
 * context with group switcher, account menu, and the assistant entry point.
 */
import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useGroups } from '../groups/GroupContext'
import { useTheme } from '../theme/ThemeContext'
import { useAssistant } from '../ai/AssistantContext'
import { AssistantPanel } from '../ai/AssistantPanel'

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/groups', label: 'Groups' },
  { to: '/contributions', label: 'Contributions' },
  { to: '/loans', label: 'Loans' },
  { to: '/meetings', label: 'Meetings' },
  { to: '/votes', label: 'Votes' },
  { to: '/statements', label: 'Statements' },
  { to: '/notifications', label: 'Notifications' },
]

export function AppShell(): React.JSX.Element {
  const { profile, signOut } = useAuth()
  const { groupsList, selected, loading, selectGroup } = useGroups()
  const { theme, toggle } = useTheme()
  const { setOpen } = useAssistant()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const handleSignOut = async (): Promise<void> => {
    await signOut()
    navigate('/sign-in')
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to main content
      </a>
      <header className="topbar">
        <button
          type="button"
          className="icon-btn nav-toggle"
          aria-expanded={menuOpen}
          aria-controls="primary-nav"
          aria-label={menuOpen ? 'Close navigation' : 'Open navigation'}
          onClick={() => setMenuOpen((o) => !o)}
        >
          ☰
        </button>
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            N
          </span>
          <span className="brand-name">Nuru</span>
        </div>
        <div className="topbar-group">
          <label className="visually-hidden" htmlFor="group-switcher">
            Current group
          </label>
          <select
            id="group-switcher"
            className="input group-switcher"
            value={selected?.id ?? ''}
            disabled={loading || groupsList.length === 0}
            onChange={(e) => selectGroup(e.target.value)}
          >
            {groupsList.length === 0 ? <option value="">No group yet</option> : null}
            {groupsList.map((g) => (
              <option key={g.id} value={g.id}>
                {g.name} · {g.currency}
              </option>
            ))}
          </select>
        </div>
        <div className="topbar-actions">
          <button
            type="button"
            className="btn btn-secondary btn-small"
            onClick={toggle}
            aria-label={theme === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
          >
            {theme === 'light' ? '◐ Dark' : '◑ Light'}
          </button>
          <button type="button" className="btn btn-primary btn-small" onClick={() => setOpen(true)}>
            Ask assistant
          </button>
          <div className="account">
            <span className="account-name" title={profile?.email ?? ''}>
              {profile?.displayName ?? 'Account'}
            </span>
            <NavLink to="/profile" className="btn btn-ghost btn-small">
              Profile
            </NavLink>
            <button type="button" className="btn btn-ghost btn-small" onClick={handleSignOut}>
              Sign out
            </button>
          </div>
        </div>
      </header>
      <div className="shell-body">
        <nav id="primary-nav" className={`sidebar${menuOpen ? ' sidebar-open' : ''}`} aria-label="Primary">
          <ul>
            {NAV.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => `nav-link${isActive ? ' nav-link-active' : ''}`}
                  onClick={() => setMenuOpen(false)}
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
          {selected ? (
            <p className="sidebar-context muted">
              Group: <strong>{selected.name}</strong>
              <br />
              Your role: {selected.currentUserRole}
            </p>
          ) : null}
        </nav>
        <main id="main" className="main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
      <AssistantPanel />
    </div>
  )
}
