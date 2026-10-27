import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Field, FormError, LoadingState } from '../components/ui'

export function RequireAuth({ children }: { children: React.JSX.Element }): React.JSX.Element {
  const { profile, loading } = useAuth()
  const location = useLocation()
  if (loading) return <LoadingState label="Checking your session…" />
  if (!profile) return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />
  return children
}

export function SignIn(): React.JSX.Element {
  const { profile, loading, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!loading && profile) return <Navigate to="/" replace />

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const errors: Record<string, string> = {}
    if (!email.trim()) errors.email = 'Enter your email address.'
    if (!password) errors.password = 'Enter your password.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    setBusy(true)
    setError(null)
    try {
      await signIn(email.trim(), password)
      const from = (location.state as { from?: string } | null)?.from ?? '/'
      navigate(from, { replace: true })
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? 'Email or password is incorrect.'
          : err instanceof ApiError
            ? err.message
            : 'Could not sign you in.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand brand-large">
          <span className="brand-mark" aria-hidden="true">
            N
          </span>
          <span className="brand-name">Nuru</span>
        </div>
        <h1>Sign in</h1>
        <p className="muted">Manage your savings group with confidence.</p>
        <form onSubmit={submit} noValidate>
          <Field label="Email" error={fieldErrors.email} required>
            <input
              className="input"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <Field label="Password" error={fieldErrors.password} required>
            <input
              className="input"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
          <FormError message={error} />
          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <p className="muted">
          New to Nuru? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </div>
  )
}

export function Register(): React.JSX.Element {
  const { profile, loading, signUp } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!loading && profile) return <Navigate to="/" replace />

  const submit = async (e: FormEvent): Promise<void> => {
    e.preventDefault()
    const errors: Record<string, string> = {}
    if (!email.trim()) errors.email = 'Enter your email address.'
    if (!displayName.trim()) errors.displayName = 'Enter the name other members will see.'
    if (password.length < 8) errors.password = 'Use at least 8 characters.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    setBusy(true)
    setError(null)
    try {
      await signUp({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
        phone: phone.trim() || undefined,
      })
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setFieldErrors(err.fieldErrors)
      }
      setError(
        err instanceof ApiError && err.status === 409
          ? 'An account with this email already exists. Try signing in.'
          : err instanceof ApiError
            ? err.message
            : 'Could not create your account.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand brand-large">
          <span className="brand-mark" aria-hidden="true">
            N
          </span>
          <span className="brand-name">Nuru</span>
        </div>
        <h1>Create your account</h1>
        <form onSubmit={submit} noValidate>
          <Field label="Full name" error={fieldErrors.displayName} required>
            <input
              className="input"
              type="text"
              autoComplete="name"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </Field>
          <Field label="Email" error={fieldErrors.email} required>
            <input
              className="input"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <Field label="Phone (optional)" hint="Needed for SMS notifications." error={fieldErrors.phone}>
            <input
              className="input"
              type="tel"
              autoComplete="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </Field>
          <Field label="Password" hint="At least 8 characters." error={fieldErrors.password} required>
            <input
              className="input"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
          <FormError message={error} />
          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Creating account…' : 'Create account'}
          </button>
        </form>
        <p className="muted">
          Already have an account? <Link to="/sign-in">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
