import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { AUTH_EXPIRED_EVENT, clearTokens, getAccessToken, getRefreshToken, storeTokens } from '../api/client'
import { login as apiLogin, logout as apiLogout, register as apiRegister, users } from '../api/resources'
import type { Profile } from '../api/types'

interface AuthState {
  profile: Profile | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<void>
  signUp: (input: { email: string; password: string; displayName: string; phone?: string }) => Promise<void>
  signOut: () => Promise<void>
  refreshProfile: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }): React.JSX.Element {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [loading, setLoading] = useState(true)

  const refreshProfile = useCallback(async () => {
    if (!getAccessToken()) {
      setProfile(null)
      return
    }
    const me = await users.me()
    setProfile(me)
  }, [])

  useEffect(() => {
    refreshProfile()
      .catch(() => setProfile(null))
      .finally(() => setLoading(false))
    const onExpired = (): void => setProfile(null)
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired)
  }, [refreshProfile])

  const signIn = useCallback(
    async (email: string, password: string) => {
      const tokens = await apiLogin({ email, password })
      storeTokens(tokens.accessToken, tokens.refreshToken)
      await refreshProfile()
    },
    [refreshProfile],
  )

  const signUp = useCallback(
    async (input: { email: string; password: string; displayName: string; phone?: string }) => {
      const tokens = await apiRegister(input)
      storeTokens(tokens.accessToken, tokens.refreshToken)
      await refreshProfile()
    },
    [refreshProfile],
  )

  const signOut = useCallback(async () => {
    const refreshToken = getRefreshToken()
    clearTokens()
    setProfile(null)
    if (refreshToken) {
      try {
        await apiLogout(refreshToken)
      } catch {
        // Session is already cleared locally; server revocation is best-effort.
      }
    }
  }, [])

  const value = useMemo(
    () => ({ profile, loading, signIn, signUp, signOut, refreshProfile }),
    [profile, loading, signIn, signUp, signOut, refreshProfile],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
