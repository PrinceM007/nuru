# Nuru frontend (React + TypeScript)

Visual interface for the Nuru savings-group platform. Built against
[`docs/API_CONTRACT.md`](../docs/API_CONTRACT.md) — every screen talks to the
real backend; nothing here fabricates balances, members, votes, or delivery
states.

## Run it

```bash
npm install     # uses .npmrc (legacy-peer-deps: npm 10.8.2 crashes otherwise)
npm run dev     # http://localhost:5173, /api proxied to http://localhost:8080
npm test        # Vitest suite
npm run build   # type-check + production build
```

The backend must be running (see `backend/README.md`). To point at another
backend host, copy `.env.example` to `.env` and set `VITE_API_BASE_URL`.

## What is here

- `src/api/` — typed client (token attach, single-flight refresh on 401,
  idempotency keys for money writes, consistent `ApiError`) plus one resource
  module covering every v1 endpoint.
- `src/auth/`, `src/groups/`, `src/theme/` — session, current-group +
  permission derivation, and light/dark monochrome theme state.
- `src/components/` — reusable states (loading/empty/error/denied/unavailable),
  accessible dialogs, form fields, pagination, money/date formatting.
- `src/screens/` — sign-in/registration, dashboard, groups + admin detail,
  contributions/ledger, loans, meetings, votes, statements, notifications,
  profile/consents/verification.
- `src/ai/` — integrated assistant panel: conversations bound to the current
  group, executed-tool facts, proposal cards with review → confirm/reject, and
  “edit in form” handoff that preserves details between chat and forms.
- `src/styles.css` — hand-rolled monochrome design, responsive sidebar →
  mobile nav, visible focus, reduced-motion support.

## Honest-UI rules followed

- “Recorded” for manual entries, “simulated” for sandbox/dev adapters,
  “estimate” for credit, “unavailable — not configured” for missing providers.
- Success is shown only from a confirmed backend response (e.g. proposals
  report only what `POST …/confirm` returns; notifications show `DELIVERED`
  only when the backend says so).
- Secret ballots never render voter identities; permission-gated buttons still
  rely on backend authorization.
- Proposal args are shown read-only because the backend confirm endpoint
  accepts no edited payload — edits happen via “edit in form”, which preserves
  the details and submits through the normal validated flow.

## Known limitations

- Tokens live in `localStorage` (httpOnly cookie hosting is a deployment
  decision, per the API contract).
- Voice input appears only when the browser exposes SpeechRecognition; the
  transcript is always shown for review before sending, and audio is not stored.
- `BrowserRouter` is used: serving deep links from any static host needs an
  SPA fallback to `index.html`.
