import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// Vitest runs with `globals: false`, so register RTL cleanup explicitly.
afterEach(() => {
  cleanup()
})
