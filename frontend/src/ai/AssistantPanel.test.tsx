import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AssistantProvider, useAssistant } from './AssistantContext'
import { ProposalCard } from './AssistantPanel'
import type { AiProposal } from '../api/types'

afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

function OpenPanel({ children }: { children: React.ReactNode }): React.JSX.Element {
  const { setOpen } = useAssistant()
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        open assistant
      </button>
      {children}
    </>
  )
}

describe('AssistantPanel', () => {
  it('shows an honest unavailable state when no provider is configured', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn((url: string | URL | Request) => {
      const path = String(url)
      if (path.endsWith('/api/v1/ai/status')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({ provider: 'UNCONFIGURED', available: false, detail: 'No AI provider configured' }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        )
      }
      return Promise.resolve(
        new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } }),
      )
    })
    vi.stubGlobal('fetch', fetchMock)
    const { AssistantPanel } = await import('./AssistantPanel')
    render(
      <MemoryRouter>
        <AssistantProvider>
          <OpenPanel>
            <AssistantPanel />
          </OpenPanel>
        </AssistantProvider>
      </MemoryRouter>,
    )
    await user.click(screen.getByRole('button', { name: 'open assistant' }))
    expect(await screen.findByText('Assistant unavailable')).toBeInTheDocument()
    expect(screen.getByText(/No AI provider configured/)).toBeInTheDocument()
    // The fallback path is always named.
    expect(screen.getByText(/visual interface/)).toBeInTheDocument()
  })

  it('asks for consent before chatting when consent is missing', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn((url: string | URL | Request) => {
      const path = String(url)
      if (path.endsWith('/api/v1/ai/status')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({ provider: 'DEV', available: true, detail: 'Development adapter' }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        )
      }
      return Promise.resolve(
        new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } }),
      )
    })
    vi.stubGlobal('fetch', fetchMock)
    const { AssistantPanel } = await import('./AssistantPanel')
    render(
      <MemoryRouter>
        <AssistantProvider>
          <OpenPanel>
            <AssistantPanel />
          </OpenPanel>
        </AssistantProvider>
      </MemoryRouter>,
    )
    await user.click(screen.getByRole('button', { name: 'open assistant' }))
    expect(await screen.findByText('Consent needed')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Allow AI assistance' })).toBeInTheDocument()
  })
})

const PROPOSAL: AiProposal = {
  id: 'p1',
  conversationId: 'c1',
  groupId: 'g1',
  tool: 'meeting_create',
  description: 'Schedule “Budget review” for the group',
  args: { title: 'Budget review', startsAt: '2026-06-01T10:00:00+02:00', timezone: 'Africa/Johannesburg' },
  status: 'PENDING',
  resultSummary: null,
  createdAt: '2026-05-01T00:00:00Z',
  expiresAt: '2026-05-02T00:00:00Z',
  decidedAt: null,
}

describe('ProposalCard', () => {
  it('shows the proposal details and offers confirm, edit, and reject', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    const onReject = vi.fn()
    const onEdit = vi.fn()
    render(
      <ProposalCard proposal={PROPOSAL} busy={false} onConfirm={onConfirm} onReject={onReject} onEdit={onEdit} />,
    )
    expect(screen.getByText(/Schedule “Budget review”/)).toBeInTheDocument()
    expect(screen.getByText('Budget review')).toBeInTheDocument()
    expect(screen.getByText('Africa/Johannesburg')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Review & confirm' }))
    expect(onConfirm).toHaveBeenCalledOnce()
    await user.click(screen.getByRole('button', { name: 'Edit in form' }))
    expect(onEdit).toHaveBeenCalledOnce()
    await user.click(screen.getByRole('button', { name: 'Reject' }))
    expect(onReject).toHaveBeenCalledOnce()
  })
})
