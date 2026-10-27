# Nuru — Production Checklist & Data Safety Notes

## 1. What this backend guarantees (and how)

- **Authentication**: BCrypt-hashed passwords (Spring Security, no custom crypto),
  short-lived signed JWT access tokens + opaque, hashed, revocable refresh tokens.
- **Group isolation**: every group-scoped service enforces membership + permission
  through `GroupAuth`. Membership is checked before group existence, so ID probing
  returns 403 without revealing whether a group exists.
- **Money**: `BigDecimal` only (never float/double), DECIMAL(19,4) storage, explicit
  per-group currency. Groups are single-currency; mismatches are rejected.
- **Confirmed history is immutable**: confirmed contributions accept only linked
  adjustment rows; the ledger journal, ballots, vote markers, eligibility snapshots,
  and audit rows are additionally protected by SQL Server AFTER triggers
  (migration V8) that roll back any UPDATE/DELETE touching real rows.
- **Secret ballots**: choice rows carry no voter reference, so no application
  response — and no read-only database access — can link a secret choice to a voter.
- **Safe errors**: consistent JSON error bodies with correlation IDs; no stack
  traces, SQL, or secrets in responses. Correlation IDs ride every request/response.
- **Safe logging**: no passwords, tokens, secrets, message bodies, document contents,
  or full AI prompts in logs. Email/SMS adapters log recipients + subjects only.

## 2. Honest limits (read before real financial use)

- **NOT “unriggable.”** Protections cover application rules, authorization, and
  read-level data secrecy. They do not defend against a compromised database
  server, coerced voters, timing correlation, or operators with direct write
  access. The triggers raise the bar for tampering; they do not replace
  infrastructure security, least-privilege logins, and SQL Server Audit.
- **No regulatory compliance is claimed.** Real identity verification, lending,
  credit decisions, payment processing, and production use need
  jurisdiction-specific legal review (financial services, data protection, KYC/AML,
  consumer credit, and tax rules differ across African markets).
- **Manual/sandbox payments are bookkeeping labels**, not proof of fund movement.
  A live payment provider must confirm transactions before anything claims funds moved.
- **Credit estimates** are group-history arithmetic with published factors — never
  bureau scores, never wired into decisions.
- **No hash chain** is implemented on the audit log. The triggers + append-only
  repository + (recommended) SQL Server Audit + backups are the tamper-evidence
  story. A hash chain would add cross-row tamper *detection* but still would not
  prevent a privileged database compromise — same residual risk, more complexity.

## 3. Production readiness checklist

### Secrets & configuration
- [ ] Unique `NURU_JWT_SECRET` (≥32 random bytes) per environment, from a vault —
      never in source, docs, or chat.
- [ ] `MSSQL_PASSWORD` and provider keys (`NURU_AI_API_KEY`, SMS/email, IDV) from a
      vault; `.env` files git-ignored.
- [ ] `MSSQL_TRUST_CERT=false` with a proper TLS certificate on SQL Server.
- [ ] Review `NURU_AI_PROVIDER/DEV` (dev only), payment mode, IDV/credit providers.

### Database
- [ ] Dedicated least-privilege SQL login for the app (no `sa`): grant
      SELECT/INSERT/UPDATE/DELETE only where needed; migrations run as a separate
      deploy login. Note: the V8 triggers already block writes to immutable tables
      for every login, including admins.
- [ ] Automated, tested backups + point-in-time recovery drills for SQL Server.
- [ ] Flyway migrations applied by the deploy pipeline; `ddl-auto` stays `validate`.
- [ ] Enable SQL Server Audit (or equivalent) on `audit_events`, `ledger_entries`,
      `ballots`, `contributions`, `loans`, `memberships`.
- [ ] Index review: every FK used in lookups is indexed (V1–V7 cover group/member/
      status/reference lookups). Re-check query plans under real data volumes.

### Operations
- [ ] Health/readiness probes wired to orchestration (`/actuator/health`,
      `/actuator/health/readiness`, `/actuator/health/liveness`).
- [ ] Centralized log shipping with the correlation ID (`X-Correlation-Id`) and
      retention/redaction rules; alert on 5xx spikes and auth failures.
- [ ] Metrics/tracing (Micrometer/OpenTelemetry) for latency, error rates, and
      scheduled jobs (AI retention purge, contribution reminders).
- [ ] Rate limiting / WAF in front of auth + AI endpoints.
- [ ] Clock discipline (NTP): vote windows, expiries, and loan schedules assume
      trustworthy server time (UTC persisted everywhere).

### Data & retention assumptions
- AI conversations: purged after `NURU_AI_RETENTION_DAYS` (default 90); users can
  delete their own sooner. No credentials or secrets ever enter prompts or logs.
- Refresh tokens: expiry + revocation; consider a periodic purge of expired rows.
- Notifications store subjects only, never bodies.
- Identity: status + provider reference only; document storage requires a separate
  approved design (encryption, access, retention, deletion) — not implemented.

### Release gates
- [ ] `mvn test` green against SQL Server (Testcontainers suite).
- [ ] Jurisdiction-specific legal/product review for lending, identity, credit,
      payments, and data protection.
- [ ] Pen-test / DAST (e.g., OWASP ZAP baseline) against a staging deployment.
- [ ] Backup/restore drill completed; runbook for migration failure and rollback.
