/**
 * Money and date formatting helpers.
 *
 * Money arrives from the API as decimal-safe JSON numbers. The UI never does
 * arithmetic on them — it only formats server-computed values for display.
 */
export function formatMoney(amount: number, currency: string, locale?: string): string {
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 4,
    }).format(amount)
  } catch {
    return `${amount} ${currency}`
  }
}

/** Client-side amount validation mirroring backend rules: > 0, max 4 decimals. */
export function parseAmountInput(value: string): { ok: true; value: number } | { ok: false; error: string } {
  const trimmed = value.trim()
  if (!/^\d+(\.\d{1,4})?$/.test(trimmed)) {
    return { ok: false, error: 'Enter an amount greater than zero with at most 4 decimal places.' }
  }
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return { ok: false, error: 'Enter an amount greater than zero.' }
  }
  return { ok: true, value: parsed }
}

/** Adjustment delta: may be negative, must not be zero, max 4 decimals. */
export function parseDeltaInput(value: string): { ok: true; value: number } | { ok: false; error: string } {
  const trimmed = value.trim()
  if (!/^-?\d+(\.\d{1,4})?$/.test(trimmed)) {
    return { ok: false, error: 'Enter a non-zero correction with at most 4 decimal places.' }
  }
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed) || parsed === 0) {
    return { ok: false, error: 'The correction must not be zero.' }
  }
  return { ok: true, value: parsed }
}

export function formatDateTime(iso: string | null | undefined, locale?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function formatDate(iso: string | null | undefined, locale?: string): string {
  if (!iso) return '—'
  const date = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date)
}

/** Converts a datetime-local input value to an ISO-8601 string with offset. */
export function localInputToIso(value: string): string {
  return new Date(value).toISOString()
}

/** Converts an ISO instant to a datetime-local input value in the user locale. */
export function isoToLocalInput(iso: string): string {
  const date = new Date(iso)
  const pad = (n: number): string => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

/** Human-readable status: EXPECTED -> "Expected". */
export function humanize(status: string | null | undefined): string {
  if (!status) return '—'
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}
