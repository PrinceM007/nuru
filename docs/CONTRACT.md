# Nuru — Stage 1: Repository Discovery & Implementation Contract

## 1. Discovery findings (2026-10-27)

- Repository state: **empty** (only `.git/`). No AGENTS.md, no existing modules, build files, tests, or API contracts.
- Java: OpenJDK 21.0.8 LTS (Temurin). Build tool: **Apache Maven 3.9.11** (Gradle not installed) → Maven chosen.
- Database: Microsoft SQL Server 2022 in Docker (`sqlserver` container, host port **7862 → 1433**). Databases `nuru` (dev) and `nuru_test` (tests) created.
- Spring Boot: none present → using **Spring Boot 3.4.x** (Java 21 baseline, SQL Server + Flyway support).
- Frontend: none present. Backend-first delivery; React UI is a later stage against the Stage 10 API contract.
- Layout decision: `backend/` (Spring Boot app), `docs/` (contract + ops docs), `frontend/` reserved for later.

## 2. User roles & primary journeys

Roles (group-scoped): `ADMIN`, `TREASURER`, `MEMBER`, plus custom roles built from explicit permissions.
Platform identity: authenticated `User` + per-group `Membership`.

Primary journeys:
1. Register → create group (becomes admin) → invite members → approve join requests.
2. Treasurer/admin defines contribution schedule → members record/confirm contributions → ledger shows expected vs confirmed.
3. Member requests loan → group approves per policy → repayments tracked → balance derived from records.
4. Admin schedules meeting → invites → attendance + notes.
5. Admin opens vote → eligible members cast one ballot → close → publish reproducible results.
6. Member requests statement → approval per policy → statement generated from persisted records.
7. Member asks AI assistant → reads authorized data or proposes action → user confirms → backend executes with same rules as UI.

## 3. MVP features / out of scope

MVP (backend): auth + profiles + consent, groups + memberships + invitations, roles/permissions,
contributions + ledger, loans + repayments, meetings + attendance, votes + ballots, statement requests,
AI tools + proposal/confirm, verification-status model, notification prefs + dev adapters, audit log, OpenAPI docs.

Out of scope / provider-gated (interfaces + safe defaults, no live integration):
live AI provider, live email/SMS gateways, live identity-verification provider, live credit bureau,
live payment rails (manual/sandbox recording only, clearly labeled).

## 4. API resources & authorization (summary; full contract in `docs/API_CONTRACT.md`)

| Resource | Base path | Auth rule (enforced in services, not just controllers) |
|---|---|---|
| Auth/session | `/api/v1/auth/**` | public register/login; authenticated refresh/logout/me |
| Users | `/api/v1/users/me` | self only; consent + preferences self-managed |
| Groups | `/api/v1/groups` | member-only reads; admin writes; ID-tampering safe |
| Memberships/invites | `/api/v1/groups/{id}/members…`, `/invitations` | admin approve; treasurer per grant; last-admin protected |
| Contributions | `/api/v1/groups/{id}/contributions…` | member submit own; treasurer/admin confirm; corrections audited |
| Ledger | `/api/v1/groups/{id}/ledger` | member-scoped summaries; full ledger treasurer/admin |
| Loans | `/api/v1/groups/{id}/loans…` | member requests own; approval per policy; repayments recorded |
| Meetings | `/api/v1/groups/{id}/meetings…` | admin manage; members read invited + attendance |
| Votes | `/api/v1/groups/{id}/votes…` | admin manage; one ballot per eligible; secret ballots hidden |
| Statements | `/api/v1/groups/{id}/statements…` | own data; approval-gated types; privacy enforced |
| AI | `/api/v1/ai/**` | authenticated; per-tool permission; confirm for mutations |
| Verify | `/api/v1/verification…` | self status; provider-gated; never fake "verified" |
| Notifications | `/api/v1/notifications…` | prefs self; sends via adapters; no false "delivered" |
| Audit | `/api/v1/groups/{id}/audit` | admin-only reads; append-only |

Money: `BigDecimal` + explicit ISO currency. Errors: consistent JSON, no stack traces. Pagination on lists.

## 5. Assumptions & unconfigured providers

- AI provider: **unconfigured** → deterministic dev adapter for local dev only (reads via tools, never fabricates balances).
- Email/SMS: logging dev adapters; delivery status `SENT` only on provider confirmation (dev = `SIMULATED`).
- Identity verification: `UNAVAILABLE` until a provider is configured + legal review done.
- Credit score: optional, estimate-only, never auto-denies; unconfigured by default.
- Payments: manual recording + sandbox adapter; nothing claims real fund movement.
- Time: UTC persisted; meeting display uses stored IANA timezone.
- Jurisdiction review required before real lending/identity/payments/credit use (see `docs/PRODUCTION_CHECKLIST.md`).

## 6. Backend acceptance criteria

- AC-1 App starts, Flyway migrates SQL Server, `/actuator/health` + readiness report UP.
- AC-2 Register/login/refresh/logout work; passwords BCrypt-hashed; validation errors are consistent JSON.
- AC-3 Non-member cannot read/mutate another group by ID; last admin cannot be removed/demoted without transfer.
- AC-4 Ledger expected-vs-confirmed math correct; corrections are reversal entries, history preserved.
- AC-5 Duplicate financial submissions rejected via idempotency keys; unauthorized transitions rejected.
- AC-6 One ballot per eligible voter (DB constraint + service check); secret ballots never expose voter identity; closed votes immutable; results reproducible.
- AC-7 Statements generated from persisted records; approval policy enforced; privacy enforced.
- AC-8 AI tools authorize per user/group; mutations require confirmation; provider timeout/invalid args handled; actions audited; no fabricated facts.
- AC-9 Verification never reports verified without provider; notifications never report delivered without confirmation.
- AC-10 Audit captures membership/financial/vote/statement/AI actions; admin-only read; append-only.
- AC-11 Build + test suite green; API contract documented for the React UI.
