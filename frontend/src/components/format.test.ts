import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatMoney,
  humanize,
  isoToLocalInput,
  localInputToIso,
  parseAmountInput,
  parseDeltaInput,
} from './format'

describe('parseAmountInput', () => {
  it('accepts positive amounts with up to 4 decimals', () => {
    expect(parseAmountInput('100')).toEqual({ ok: true, value: 100 })
    expect(parseAmountInput('99.95')).toEqual({ ok: true, value: 99.95 })
    expect(parseAmountInput('0.0001')).toEqual({ ok: true, value: 0.0001 })
  })

  it('rejects zero, negatives, and too many decimals', () => {
    expect(parseAmountInput('0').ok).toBe(false)
    expect(parseAmountInput('-5').ok).toBe(false)
    expect(parseAmountInput('10.12345').ok).toBe(false)
    expect(parseAmountInput('abc').ok).toBe(false)
    expect(parseAmountInput('').ok).toBe(false)
  })
})

describe('parseDeltaInput', () => {
  it('accepts non-zero corrections including negatives', () => {
    expect(parseDeltaInput('-50')).toEqual({ ok: true, value: -50 })
    expect(parseDeltaInput('25.5')).toEqual({ ok: true, value: 25.5 })
  })

  it('rejects zero and malformed input', () => {
    expect(parseDeltaInput('0').ok).toBe(false)
    expect(parseDeltaInput('0.00').ok).toBe(false)
    expect(parseDeltaInput('x').ok).toBe(false)
  })
})

describe('formatMoney', () => {
  it('formats with the group currency', () => {
    const formatted = formatMoney(1234.5, 'ZAR', 'en-ZA')
    expect(formatted).toContain('1')
    expect(formatted).toMatch(/R|ZAR/)
  })

  it('falls back to amount-plus-code for unknown currencies', () => {
    expect(formatMoney(10, 'XXX')).toContain('XXX')
  })
})

describe('humanize', () => {
  it('turns status codes into readable labels', () => {
    expect(humanize('EXPECTED')).toBe('Expected')
    expect(humanize('CONTRIBUTIONS_CONFIRM')).toBe('Contributions Confirm')
    expect(humanize(null)).toBe('—')
  })
})

describe('date helpers', () => {
  it('round-trips local input values', () => {
    const iso = localInputToIso('2026-06-01T10:30')
    expect(isoToLocalInput(iso)).toBe('2026-06-01T10:30')
  })

  it('formats plain dates without timezone drift display', () => {
    expect(formatDate('2026-06-01', 'en-GB')).toContain('2026')
    expect(formatDate(null)).toBe('—')
  })
})
