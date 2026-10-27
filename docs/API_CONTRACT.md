# Nuru API Contract (v1) — for the React UI

Base URL: `http://<host>:<port>` · API prefix: `/api/v1` · OpenAPI: `/v3/api-docs`
· Swagger UI: `/swagger-ui.html`. All timestamps are ISO-8601 with offset (UTC
persisted). Money is a JSON number mapped to `BigDecimal` — the UI must never use
binary floating point for amounts. Groups are single-currency (ISO 3-letter code).

## 1. Conventions

### Authentication
- `POST /api/v1/auth/register|login` → `{accessToken, refreshToken, expiresInSeconds}`.
- Send `Authorization: Bearer <accessToken>` on every authenticated call.
- `POST /api/v1/auth/refresh {refreshToken}` rotates the pair (old refresh token
  dies). `POST /api/v1/auth/logout {refreshToken}` revokes it (204).
- Access tokens expire (default 30 min). On 401, refresh once, then retry; if the
  refresh fails, send the user to login.

### Errors (consistent shape, never stack traces)
```json
{
  "timestamp": "2026-10-27T10:00:00+02:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Only pending requests can be approved",
  "path": "/api/v1/groups/.../join-requests/.../approve",
  "correlationId": "…",
  "details": { "field": "reason" }
}
```
- 400 validation (`details` holds field errors) · 401 unauthenticated · 403
  forbidden (generic “Access denied”, also used when probing another group's IDs) ·
  404 not found · 409 conflict/duplicate · 422 valid request, illegal state/rule ·
  503 provider unavailable (AI/identity/credit) or provider timeout.
- Every response carries `X-Correlation-Id`; echo user-supplied values back.

### Pagination
List endpoints accept `page` (0-based), `size`, `sort` and return Spring `Page`:
`{content:[…], totalElements, totalPages, number, size, first, last}`.

### Idempotency
`POST …/contributions`, `POST …/loans`, `POST …/loans/{id}/repayments` accept an
optional `Idempotency-Key` header (≤64 chars). Replays return the original row
with 200 instead of 201. Always send a key from the UI for money writes.

### Roles & permissions
Group roles: `ADMIN` (all permissions, immutable), `TREASURER` (default:
`CONTRIBUTIONS_CONFIRM`, `LEDGER_VIEW_FULL`), `MEMBER` (no grants; participation
rights are implicit), plus custom roles combining only these permissions:
`GROUP_SETTINGS_MANAGE, MEMBERS_MANAGE, ROLES_MANAGE, MEETINGS_MANAGE,
VOTES_MANAGE, APPROVAL_POLICIES_MANAGE, INVITATIONS_MANAGE,
CONTRIBUTIONS_CONFIRM, LEDGER_VIEW_FULL, LOANS_APPROVE, STATEMENTS_APPROVE,
AUDIT_VIEW`. All checks run in backend services — the UI must still hide
unauthorized actions but never relies on that.

## 2. Users & consent

| Endpoint | Auth | Mutates | Notes |
|---|---|---|---|
| `GET /api/v1/users/me` | self | No | Profile. |
| `PATCH /api/v1/users/me` | self | Yes | `{displayName?, phone?}`. |
| `GET/PATCH /api/v1/users/me/preferences` | self | PATCH yes | `{emailEnabled, smsEnabled, locale}`; SMS needs a phone number. |
| `POST /api/v1/users/me/consents` | self | Yes | `{purpose: AI_ASSISTANCE\|IDENTITY_VERIFICATION, granted}`; unknown purpose → 422. |
| `GET /api/v1/users/me/consents` | self | No | Latest decision per purpose, as a map `{PURPOSE: {purpose, granted, recordedAt}}` (empty `{}` when nothing recorded). |

AI chat requires `AI_ASSISTANCE=true` (else 403); verification requires
`IDENTITY_VERIFICATION=true`.

## 3. Groups, members, invitations, roles

| Endpoint | Auth | Mutates | Notes |
|---|---|---|---|
| `POST /api/v1/groups` | user | Yes | `{name 1–160, description?, currency: /^[A-Za-z]{3}$/}`. Creator becomes ADMIN; system roles seeded. → 201. |
| `GET /api/v1/groups` | user | No | Paged groups where caller is an active member. |
| `GET /api/v1/groups/{id}` | member | No | Includes `currentUserRole`. Non-member (or bogus id) → 403. |
| `PATCH /api/v1/groups/{id}` | `GROUP_SETTINGS_MANAGE` | Yes | `{name?, description?}`; currency immutable. |
| `GET /api/v1/groups/{id}/members` | member | No | Paged; `email` only for self/admins. |
| `POST /api/v1/groups/{id}/join-requests` | user | Yes | `{message? ≤500}`; existing member → 409; duplicate pending → 409. → 201. |
| `GET /api/v1/groups/{id}/join-requests?status=` | `MEMBERS_MANAGE` | No | Paged requests. |
| `POST …/join-requests/{rid}/approve` | `MEMBERS_MANAGE` | Yes | `{roleId?}` (default MEMBER). Non-pending → 422. Notifies applicant. |
| `POST …/join-requests/{rid}/reject` | `MEMBERS_MANAGE` | Yes | `{reason?}`. Notifies applicant. |
| `DELETE …/join-requests/{rid}` | requester | Yes | Cancel own pending request. → 204. |
| `PATCH …/members/{uid}/role` | `ROLES_MANAGE` | Yes | `{roleId}`; demoting the last admin → 422. |
| `DELETE …/members/{uid}` | `MEMBERS_MANAGE` | Yes | Remove (status LEFT); self → 422, use leave; last admin → 422. → 204. |
| `DELETE …/members/me` | member | Yes | Leave group; last admin → 422 (transfer first). → 204. |
| `POST …/admin-transfer` | admin | Yes | `{newAdminUserId}`; target→ADMIN, caller→MEMBER atomically. → 204. |
| `GET /api/v1/groups/{id}/roles` | member | No | Roles + permission grants. |
| `POST /api/v1/groups/{id}/roles` | `ROLES_MANAGE` | Yes | `{name, permissions[]}`; reserved/unknown names → 422. → 201. |
| `PATCH …/roles/{roleId}` | `ROLES_MANAGE` | Yes | `{permissions[]}`; ADMIN immutable → 422. |
| `DELETE …/roles/{roleId}` | `ROLES_MANAGE` | Yes | Custom, unused roles only. → 204. |
| `POST /api/v1/groups/{id}/invitations` | `INVITATIONS_MANAGE` | Yes | `{roleId?, expiresInHours 1–720?, maxUses 1–100?}` → 201 with one-time `code` (never listed again). |
| `GET /api/v1/groups/{id}/invitations` | `INVITATIONS_MANAGE` | No | Paged, no codes. |
| `DELETE …/invitations/{iid}` | `INVITATIONS_MANAGE` | Yes | Revoke. → 204. |
| `POST /api/v1/invitations/accept` | user | Yes | `{code}` → `{groupId}`; bogus → 404; expired/revoked/used → 422; already member → 409. |

## 4. Contributions, ledger, loans

Money rules: `amount > 0`, ≤4 decimals, group currency enforced.
`recordedVia` is `MANUAL` (human bookkeeping label) or `SANDBOX` (simulation) —
neither proves real fund movement.

| Endpoint | Auth | Mutates | Notes |
|---|---|---|---|
| `POST …/contribution-schedules` | `CONTRIBUTIONS_CONFIRM` | Yes | `{title, amount, frequency: WEEKLY\|MONTHLY\|CUSTOM, dueDay?, startDate, endDate?}`. → 201. |
| `GET …/contribution-schedules` | member | No | Paged. |
| `POST …/contributions` | member* | Yes | `{memberUserId? (=self unless confirmer), scheduleId?, periodStart, periodEnd, amount, assessment, notes?}` + `Idempotency-Key?`. `assessment=true` needs `CONTRIBUTIONS_CONFIRM` → status EXPECTED, else SUBMITTED. → 201 (200 on key replay). |
| `GET …/contributions?memberUserId=&status=` | member | No | Own rows; others need `LEDGER_VIEW_FULL` (else 403). |
| `GET …/contributions/{cid}` | member* | No | Own or `LEDGER_VIEW_FULL`; includes `adjustments[]`. |
| `PATCH …/contributions/{cid}` | recorder/confirmer | Yes | Edit UNCONFIRMED only; confirmed → 422 (use adjust). |
| `POST …/contributions/{cid}/confirm` | `CONTRIBUTIONS_CONFIRM` | Yes | `{occurredAt? ≤ now}`; EXPECTED/SUBMITTED → CONFIRMED + ledger row. Repeat → 422. Notifies member. |
| `POST …/contributions/{cid}/reject` | `CONTRIBUTIONS_CONFIRM` | Yes | `{reason?}` → REJECTED. |
| `POST …/contributions/{cid}/cancel` | recorder/confirmer | Yes | Unconfirmed → CANCELLED. |
| `POST …/contributions/{cid}/adjust` | `CONTRIBUTIONS_CONFIRM` | Yes | `{amount ≠ 0 (delta, may be negative), reason!}` → 201 linked adjustment + ledger row. Original untouched. |
| `GET …/ledger/summary` | member | No | Group totals + caller's own totals + `asOf`. Reproducible from stored rows. |
| `GET …/ledger/entries` | `LEDGER_VIEW_FULL` | No | Paged immutable journal. |
| `POST …/loans` | member (self) | Yes | `{principal, interestRate? ≤1.0 flat, termMonths 1–60, purpose?}` + `Idempotency-Key?`. `totalDue = principal×(1+rate)` locked at request. → 201. |
| `GET …/loans` | member | No | Own loans; all with `LOANS_APPROVE` or `LEDGER_VIEW_FULL`. |
| `GET …/loans/{lid}` | borrower/privileged | No | Includes `outstandingBalance`. Others → 403. |
| `POST …/loans/{lid}/approve` | `LOANS_APPROVE`, not borrower | Yes | Self-approval → 403. Non-requested → 422. Notifies borrower. |
| `POST …/loans/{lid}/reject` | `LOANS_APPROVE`, not borrower | Yes | `{reason!}`. |
| `POST …/loans/{lid}/cancel` | borrower | Yes | REQUESTED only → CANCELLED. |
| `POST …/loans/{lid}/disburse` | `LOANS_APPROVE`, not borrower | Yes | APPROVED → ACTIVE; writes ledger outflow; generates schedule (parts sum exactly to `totalDue`). Records the decision — real payout happens outside Nuru. |
| `POST …/loans/{lid}/mark-defaulted` | `LOANS_APPROVE` | Yes | `{reason!}`; ACTIVE → DEFAULTED. Manual only. |
| `POST …/loans/{lid}/reactivate` | `LOANS_APPROVE` | Yes | DEFAULTED → ACTIVE. |
| `GET …/loans/{lid}/schedule` | borrower/privileged | No | Installments `{installmentNo, dueDate, amountDue, amountPaid, status}`. |
| `POST …/loans/{lid}/repayments` | borrower/confirmer | Yes | `{amount ≤ outstanding}` + `Idempotency-Key?` → SUBMITTED. Overpay → 422. |
| `GET …/loans/{lid}/repayments` | borrower/privileged | No | Paged. |
| `POST …/repayments/{rid}/confirm` | `CONTRIBUTIONS_CONFIRM` | Yes | → CONFIRMED + ledger row + schedule application; loan → REPAID at zero balance. Notifies borrower. |
| `POST …/repayments/{rid}/reject` | `CONTRIBUTIONS_CONFIRM` | Yes | `{reason?}`. |

## 5. Meetings

| Endpoint | Auth | Mutates | Notes |
|---|---|---|---|
| `POST …/meetings` | `MEETINGS_MANAGE` | Yes | `{title, agenda?, startsAt, endsAt?, timezone (IANA), location?, meetingLink?, invitedUserIds? (default: all active members)}`. Bad tz/times/invitees → 422. → 201. Notifies invitees. |
| `GET …/meetings`, `GET …/meetings/{mid}` | member | No | Paged list; detail includes participants + notes. |
| `PATCH …/meetings/{mid}` | `MEETINGS_MANAGE` | Yes | SCHEDULED only. Notifies invitees. |
| `POST …/meetings/{mid}/complete` | `MEETINGS_MANAGE` | Yes | → COMPLETED. |
| `POST …/meetings/{mid}/cancel` | `MEETINGS_MANAGE` | Yes | → CANCELLED. |
| `POST …/meetings/{mid}/attendance/{uid}` | self/manager | Yes | `{attendance: INVITED\|PRESENT\|ABSENT\|APOLOGY}`; invited only; cancelled → 422. |
| `POST …/meetings/{mid}/notes` | member | Yes | `{body! ≤20000}`. → 201. |
| `PATCH/DELETE …/notes/{nid}` | author/manager | Yes | |

## 6. Votes

Lifecycle DRAFT → OPEN → CLOSED → PUBLISHED. Eligibility snapshots active members
at open (see `eligibilityPolicy` on every vote). Ballots accepted only while OPEN
and inside `[opensAt, closesAt]`, one per eligible user (409 on double vote).
Results exist only after PUBLISHED (else 422) and are recomputed from stored rows.
SECRET results never contain voter identities (`attributableChoices: null`);
ATTRIBUTABLE results map option → voters. No “unriggable” claim — see
`docs/PRODUCTION_CHECKLIST.md` for honest limits.

| Endpoint | Auth | Mutates | Notes |
|---|---|---|---|
| `POST …/votes` | `VOTES_MANAGE` | Yes | `{question, description?, ballotPolicy: SECRET\|ATTRIBUTABLE, opensAt, closesAt>opensAt, quorum?≥1, options[2–10 distinct]}`. → 201 DRAFT. |
| `GET …/votes`, `GET …/votes/{vid}` | member | No | Includes options, `eligibleCount`, `eligibilityPolicy`, `voted`. |
| `PATCH …/votes/{vid}` | `VOTES_MANAGE` | Yes | DRAFT only. |
| `DELETE …/votes/{vid}` | `VOTES_MANAGE` | Yes | DRAFT only. → 204. |
| `POST …/votes/{vid}/open` | `VOTES_MANAGE` | Yes | Freezes eligibility; empty group → 422. Notifies eligible (email+SMS). |
| `POST …/votes/{vid}/close` | `VOTES_MANAGE` | Yes | Early close allowed. Notifies eligible. |
| `POST …/votes/{vid}/publish` | `VOTES_MANAGE` | Yes | CLOSED → PUBLISHED. |
| `POST …/votes/{vid}/ballots` | eligible active member | Yes | `{optionId}` → 201 `{voted:true, optionId?}` (`optionId` only for ATTRIBUTABLE). Ineligible → 403. |
| `GET …/votes/{vid}/my-ballot` | member | No | Own status. |
| `GET …/votes/{vid}/results` | member | No | Published only. Tallies + `totalBallots/eligibleCount/quorum/quorumMet`. |

## 7. Statements

Policies default: `PERSONAL_STATEMENT` no approval, `GROUP_SUMMARY` approval
required; admins override per group. Documents generate live from persisted rows
(period-bounded) with `currency`, `periodStart/End`, `generatedAt`. Group
summaries contain totals only — no member rows.

| Endpoint | Auth | Mutates | Notes |
|---|---|---|---|
| `GET …/statements/policies` | member | No | `[{requestType, approvalRequired}]`. |
| `PUT …/statements/policies` | `APPROVAL_POLICIES_MANAGE` | Yes | `{requestType, approvalRequired}`. |
| `POST …/statements/requests` | member | Yes | `{requestType, periodStart, periodEnd}` → PENDING or auto-APPROVED. → 201. |
| `GET …/statements/requests` | member | No | Own; all with `STATEMENTS_APPROVE`. |
| `POST …/requests/{rid}/approve` | `STATEMENTS_APPROVE` | Yes | PENDING → APPROVED. |
| `POST …/requests/{rid}/reject` | `STATEMENTS_APPROVE` | Yes | `{reason!}` → REJECTED. |
| `GET …/requests/{rid}/document` | requester/approver | No | APPROVED only (else 422). Personal docs are private to the requester + approvers. |

## 8. AI assistant

Requires `AI_ASSISTANCE` consent (403 otherwise). `GET /api/v1/ai/status` →
`{provider, available, detail}`; when unconfigured, chat returns 503
(AI_PROVIDER_UNAVAILABLE equivalent) — the UI must show the unavailable state,
never fake answers. The DEV provider (`NURU_AI_PROVIDER=DEV`) is deterministic,
labeled, and returns recorded data only.

- Conversations are private to the user. Bind to a group via `{groupId}` (must be
  a member) so tools resolve group scope; reads/writes enforce the same services
  and permissions as the REST endpoints.
- `POST /api/v1/ai/conversations/{id}/messages {text ≤4000}` →
  `{assistantMessage, executedTools:[{tool, summary, facts}], proposals:[…]}`.
  Read tools execute immediately; state-changing tools create PENDING proposals.
- `GET /api/v1/ai/proposals?status=` (default PENDING) →
  `[{id, tool, description, args, status, resultSummary, expiresAt, …}]`.
- `POST /api/v1/ai/proposals/{id}/confirm` executes with re-validated args and
  re-checked authorization → success returns CONFIRMED + result; auth failure
  returns 403 and marks the proposal FAILED; stale → 422. Report success only
  from this response.
- `POST /api/v1/ai/proposals/{id}/reject` → REJECTED, nothing changes.
- `DELETE /api/v1/ai/conversations/{id}` deletes history (retention purge also
  runs nightly, default 90 days).

Available tools: `contribution_summary`, `meeting_info`, `ledger_explain` (read);
`meeting_create`, `statement_request`, `join_request_decide`, `invitation_create`,
`vote_create`, `loan_request` (proposal + confirm). The UI confirm screen must
show `description` + editable `args`; edited args are validated server-side on
confirm (unknown tools/args → 422). Provider timeouts/refusals surface as 503
with a plain message — retry or fall back to the visual UI.

UI/assistant parity: proposals carry the same identifiers the REST API returns
(`meetingId`, `requestId`, `loanId`, …), so a task started in chat can be
finished (or inspected) in the visual UI and vice versa.

## 9. Identity, credit, notifications, audit

- `GET /api/v1/verification/me` → `{status: NOT_STARTED|PENDING|VERIFIED|FAILED|
  EXPIRED|UNAVAILABLE, provider, providerReference, verifiedAt, expiresAt, …}`.
  Unconfigured installs always report `UNAVAILABLE` — never “verified”.
- `POST /api/v1/verification/me/start|refresh` (needs consent; 503 unconfigured).
  No document contents are stored — references + status only.
- `GET /api/v1/groups/{id}/credit/estimate?memberUserId=` (own, or
  `LEDGER_VIEW_FULL` for others; 503 unconfigured) →
  `{score 0–100|null, factors:[{name,value,weight}], dataPeriodStart/End,
  estimatedAt, label}`. Labeled ESTIMATE; insufficient history returns
  `insufficientHistory:true`. Nothing auto-denies from it.
- `GET /api/v1/notifications/me` (paged own) and
  `GET /api/v1/groups/{id}/notifications` (`AUDIT_VIEW`) →
  `{channel, kind, subject, status: SENT|DELIVERED|SIMULATED|SKIPPED|FAILED|QUEUED,
  provider, providerReference, errorDetail}`. `DELIVERED` only on provider
  confirmation; dev adapters report `SIMULATED`; disabled channels/no phone →
  `SKIPPED`. Bodies are never stored or returned.
- `GET /api/v1/groups/{id}/audit` (`AUDIT_VIEW`, paged) → append-only event trail.

## 10. UI build notes (React, next stage)

- Auth flow: register/login → store tokens (httpOnly handling is a frontend
  hosting decision) → refresh on 401 → logout revokes.
- Feature areas map 1:1 to sections 2–9 above; every consequential UI action
  should mirror the assistant's confirm pattern (review → confirm → show the
  server's result, including 403/422/503 states).
- Honest UI copy: say “recorded” for manual entries (never “paid”/“received”
  unless a live provider confirms), “simulated” for sandbox/dev adapters,
  “estimate” for credit, and “unavailable — not configured” for missing providers.
- Never display secret-ballot voter links (the API never sends them); never claim
  “unriggable” or “compliant” anywhere in the product.
