/**
 * Typed HTTP client for the Nuru v1 API.
 *
 * - Base URL defaults to same-origin (Vite proxies /api to the backend in dev).
 * - Attaches the access token, refreshes once on 401, then retries.
 * - Sends an Idempotency-Key on money writes when the caller provides one.
 * - Throws ApiError with the backend's consistent error shape; never leaks
 *   stack traces into the UI.
 */
import type { ApiErrorBody } from './types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

const ACCESS_KEY = 'nuru.accessToken'
const REFRESH_KEY = 'nuru.refreshToken'

/** Fired when the session can no longer be refreshed; AuthContext listens. */
export const AUTH_EXPIRED_EVENT = 'nuru:auth-expired'

export class ApiError extends Error {
  readonly status: number
  readonly body?: ApiErrorBody

  constructor(status: number, body: ApiErrorBody | undefined, fallback: string) {
    super(body?.message || fallback)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }

  get fieldErrors(): Record<string, string> {
    const details = this.body?.details
    if (!details || typeof details !== 'object') return {}
    const out: Record<string, string> = {}
    for (const [key, value] of Object.entries(details)) {
      if (typeof value === 'string') out[key] = value
    }
    return out
  }
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function storeTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

export interface RequestOptions {
  method?: string
  body?: unknown
  /** Send as Idempotency-Key header (money writes). */
  idempotencyKey?: string
  /** Set false for register/login/refresh/logout. */
  auth?: boolean
}

/** Single-flight refresh so concurrent 401s trigger only one rotation. */
let refreshPromise: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  if (refreshPromise) return refreshPromise
  refreshPromise = (async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) return false
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) return false
      const data = (await res.json()) as {
        accessToken: string
        refreshToken: string
      }
      storeTokens(data.accessToken, data.refreshToken)
      return true
    } catch {
      return false
    } finally {
      refreshPromise = null
    }
  })()
  return refreshPromise
}

function notifyAuthExpired(): void {
  clearTokens()
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
}

async function parseError(res: Response): Promise<ApiError> {
  let body: ApiErrorBody | undefined
  try {
    body = (await res.json()) as ApiErrorBody
  } catch {
    body = undefined
  }
  const fallback =
    res.status === 401
      ? 'Your session has expired. Please sign in again.'
      : res.status === 403
        ? 'Access denied. You do not have permission for this action.'
        : res.status === 404
          ? 'The requested item was not found.'
          : res.status === 503
            ? 'This service is unavailable right now. Please try again later.'
            : `Request failed (${res.status}).`
  return new ApiError(res.status, body, fallback)
}

async function doFetch(path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey
  if (options.auth !== false) {
    const token = getAccessToken()
    if (token) headers['Authorization'] = `Bearer ${token}`
  }
  return fetch(`${API_BASE}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
}

export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  let res: Response
  try {
    res = await doFetch(path, options)
  } catch {
    throw new ApiError(0, undefined, 'Could not reach the server. Check your connection and try again.')
  }
  if (res.status === 401 && options.auth !== false) {
    const refreshed = await tryRefresh()
    if (!refreshed) {
      notifyAuthExpired()
      throw new ApiError(401, undefined, 'Your session has expired. Please sign in again.')
    }
    try {
      res = await doFetch(path, options)
    } catch {
      throw new ApiError(0, undefined, 'Could not reach the server. Check your connection and try again.')
    }
    if (res.status === 401) {
      notifyAuthExpired()
      throw new ApiError(401, undefined, 'Your session has expired. Please sign in again.')
    }
  }
  if (res.status === 204) return undefined as T
  if (!res.ok) throw await parseError(res)
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text) as T
}

export function get<T>(path: string, options?: RequestOptions): Promise<T> {
  return apiFetch<T>(path, { ...options, method: 'GET' })
}

export function post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return apiFetch<T>(path, { ...options, method: 'POST', body })
}

export function patch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return apiFetch<T>(path, { ...options, method: 'PATCH', body })
}

export function put<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return apiFetch<T>(path, { ...options, method: 'PUT', body })
}

export function del<T>(path: string, options?: RequestOptions): Promise<T> {
  return apiFetch<T>(path, { ...options, method: 'DELETE' })
}

/** Builds a fresh idempotency key for one user-confirmed money write. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

export function pageParams(page: number, size = 20): string {
  return `page=${page}&size=${size}`
}
