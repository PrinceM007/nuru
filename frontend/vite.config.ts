import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
// NOTE: vitest 3 bundles its own vite whose Plugin type differs from the
// project's vite 8. The cast below is config-only; at runtime both `vite`
// and `vitest` receive the same plugin object they each understand.
export default defineConfig({
  plugins: [react() as never],
  server: {
    // Same-origin /api calls during local development.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: false,
  },
})
