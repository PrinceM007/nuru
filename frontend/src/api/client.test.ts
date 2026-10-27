import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  AUTH_EXPIRED_EVENT,
  apiFetch,
  clearTokens,
  get,
  post,
  storeTokens,
} from './client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  clearTokens()
  localStorage.clear()
})

describe('ApiError', () => {
  it('exposes field errors from validation responses', () => {
    const err = new ApiError(400, { status: 400, details: { email: 'must be an email' } }, 'Bad request')
    expect(err.fieldErrors).toEqual({ email: 'must be an email' })
  })

  it('prefers the backend message', () => {
    const err = new ApiError(422, { status: 422, message: 'Only pending requests can be approved' }, 'fallback')
    expect(err.message).toBe('Only pending requests can be approved')
  })
})

describe('apiFetch', () => {
  it('attaches the access token', async () => {
    storeTokens('access-1', 'refresh-1')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    await get<{ ok: boolean }>('/api/v1/users/me')
    expect(fetchMock).toHaveBeenCalledOnce()
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer access-1')
  })

  it('sends the idempotency key on money writes', async () => {
    storeTokens('access-1', 'refresh-1')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'c1' }, 201))
    vi.stubGlobal('fetch', fetchMock)
    await post('/api/v1/groups/g/contributions', { amount: 10 }, { idempotencyKey: 'key-123' })
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>)['Idempotency-Key']).toBe('key-123')
  })

  it('refreshes once on 401 and retries', async () => {
    storeTokens('old-access', 'refresh-1')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'new-access', refreshToken: 'new-refresh' }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    const result = await get<{ ok: boolean }>('/api/v1/users/me')
    expect(result).toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(localStorage.getItem('nuru.accessToken')).toBe('new-access')
  })

  it('notifies auth expiry when refresh fails', async () => {
    storeTokens('old-access', 'bad-refresh')
    const events: Event[] = []
    const listener = (e: Event): void => {
      events.push(e)
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, listener)
    try {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(new Response(null, { status: 401 }))
        .mockResolvedValueOnce(new Response(null, { status: 401 }))
      vi.stubGlobal('fetch', fetchMock)
      await expect(get('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)
      expect(events).toHaveLength(1)
      expect(localStorage.getItem('nuru.accessToken')).toBeNull()
    } finally {
      window.removeEventListener(AUTH_EXPIRED_EVENT, listener)
    }
  })

  it('maps 403 to an access-denied message', async () => {
    storeTokens('access-1', 'refresh-1')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 403, message: 'Access denied' }, 403)))
    const err = await get('/api/v1/groups/other').catch((e: unknown) => e as ApiError)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(403)
  })

  it('returns undefined for 204 responses', async () => {
    storeTokens('access-1', 'refresh-1')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(apiFetch<void>('/api/v1/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('reports network failures without leaking internals', async () => {
    storeTokens('access-1', 'refresh-1')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('socket hang up')))
    const err = await get('/api/v1/users/me').catch((e: unknown) => e as ApiError)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).status).toBe(0)
    expect((err as ApiError).message).not.toContain('socket hang up')
  })
})
