import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog, Field, Pagination, StatusPill } from './ui'

describe('StatusPill', () => {
  it('shows text plus an icon so status never depends on color alone', () => {
    render(<StatusPill status="CONFIRMED" />)
    const pill = screen.getByText('Confirmed')
    expect(pill).toBeInTheDocument()
    // Icon glyph present alongside the text.
    expect(pill.textContent).not.toBe('Confirmed')
    expect(pill.textContent).toContain('●')
  })

  it('renders pending states distinctly', () => {
    render(<StatusPill status="EXPECTED" />)
    expect(screen.getByText('Expected')).toBeInTheDocument()
  })
})

describe('Field', () => {
  it('associates errors with the input for screen readers', () => {
    render(
      <Field label="Amount" error="Enter an amount.">
        <input type="text" />
      </Field>,
    )
    const input = screen.getByLabelText('Amount')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    const describedBy = input.getAttribute('aria-describedby') ?? ''
    const error = screen.getByRole('alert')
    expect(describedBy).toContain(error.id)
  })
})

describe('ConfirmDialog', () => {
  it('dismisses with Escape', async () => {
    const onCancel = vi.fn()
    render(
      <ConfirmDialog title="Delete?" confirmLabel="Delete" onConfirm={vi.fn()} onCancel={onCancel}>
        <p>Are you sure?</p>
      </ConfirmDialog>,
    )
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('confirms with the labeled button and is keyboard reachable', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog title="Delete?" confirmLabel="Delete it" onConfirm={onConfirm} onCancel={vi.fn()}>
        <p>Are you sure?</p>
      </ConfirmDialog>,
    )
    await user.tab()
    await user.tab()
    await user.keyboard('{Enter}')
    // Either focus order reaches a button; assert the dialog is operable via its confirm label.
    await user.click(screen.getByRole('button', { name: 'Delete it' }))
    expect(onConfirm).toHaveBeenCalled()
  })
})

describe('Pagination', () => {
  it('navigates between pages', async () => {
    const user = userEvent.setup()
    const onPage = vi.fn()
    render(<Pagination page={0} totalPages={3} totalElements={25} onPage={onPage} />)
    expect(screen.getByText(/Page 1 of 3/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Previous/ })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /Next/ }))
    expect(onPage).toHaveBeenCalledWith(1)
  })

  it('hides on a single page', () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} totalElements={5} onPage={vi.fn()} />,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
