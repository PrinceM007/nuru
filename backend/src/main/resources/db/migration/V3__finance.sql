-- Nuru V3: contribution schedules, contributions, ledger journal, loans, repayments.
-- Money is DECIMAL(19,4); groups are single-currency (currency must match the group).

CREATE TABLE contribution_schedules (
  id         UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id   UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  title      NVARCHAR(160) NOT NULL,
  amount     DECIMAL(19,4) NOT NULL,
  currency   NVARCHAR(3) NOT NULL,
  frequency  VARCHAR(20) NOT NULL,
  due_day    INT NULL,
  start_date DATE NOT NULL,
  end_date   DATE NULL,
  created_by UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_schedules_group ON contribution_schedules (group_id);

CREATE TABLE contributions (
  id                UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id          UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  schedule_id       UNIQUEIDENTIFIER NULL REFERENCES contribution_schedules(id),
  member_user_id    UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  period_start      DATE NOT NULL,
  period_end        DATE NOT NULL,
  amount            DECIMAL(19,4) NOT NULL,
  currency          NVARCHAR(3) NOT NULL,
  status            VARCHAR(20) NOT NULL,
  recorded_by       UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  recorded_via      VARCHAR(20) NOT NULL,
  payment_reference NVARCHAR(120) NULL,
  confirmed_by      UNIQUEIDENTIFIER NULL REFERENCES users(id),
  confirmed_at      DATETIMEOFFSET NULL,
  rejection_reason  NVARCHAR(500) NULL,
  correction_of     UNIQUEIDENTIFIER NULL REFERENCES contributions(id),
  notes             NVARCHAR(1000) NULL,
  idempotency_key   NVARCHAR(64) NULL,
  created_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_contributions_idem ON contributions (group_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE INDEX IX_contributions_group_member ON contributions (group_id, member_user_id, status);
CREATE INDEX IX_contributions_correction ON contributions (correction_of)
  WHERE correction_of IS NOT NULL;

-- Immutable journal of confirmed money movements. Never updated or deleted by the app.
CREATE TABLE ledger_entries (
  id             BIGINT IDENTITY(1,1) PRIMARY KEY,
  group_id       UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  kind           VARCHAR(40) NOT NULL,
  reference_id   UNIQUEIDENTIFIER NOT NULL,
  member_user_id UNIQUEIDENTIFIER NULL REFERENCES users(id),
  amount         DECIMAL(19,4) NOT NULL,
  currency       NVARCHAR(3) NOT NULL,
  occurred_at    DATETIMEOFFSET NOT NULL,
  created_at     DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_ledger_group_kind ON ledger_entries (group_id, kind);
CREATE INDEX IX_ledger_group_time ON ledger_entries (group_id, occurred_at DESC);
CREATE INDEX IX_ledger_reference ON ledger_entries (kind, reference_id);

CREATE TABLE loans (
  id               UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id         UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  borrower_user_id UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  amount_principal DECIMAL(19,4) NOT NULL,
  currency         NVARCHAR(3) NOT NULL,
  interest_rate    DECIMAL(9,6) NOT NULL DEFAULT 0,
  total_due        DECIMAL(19,4) NOT NULL,
  term_months      INT NOT NULL,
  purpose          NVARCHAR(500) NULL,
  status           VARCHAR(20) NOT NULL,
  requested_by     UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  requested_at     DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  decided_by       UNIQUEIDENTIFIER NULL REFERENCES users(id),
  decided_at       DATETIMEOFFSET NULL,
  decision_reason  NVARCHAR(500) NULL,
  disbursed_at     DATETIMEOFFSET NULL,
  idempotency_key  NVARCHAR(64) NULL,
  created_at       DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at       DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_loans_idem ON loans (group_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE INDEX IX_loans_group_borrower ON loans (group_id, borrower_user_id, status);

CREATE TABLE repayment_schedules (
  id             UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  loan_id        UNIQUEIDENTIFIER NOT NULL REFERENCES loans(id),
  installment_no INT NOT NULL,
  due_date       DATE NOT NULL,
  amount_due     DECIMAL(19,4) NOT NULL,
  amount_paid    DECIMAL(19,4) NOT NULL DEFAULT 0,
  status         VARCHAR(20) NOT NULL
);
CREATE UNIQUE INDEX UX_schedule_loan_no ON repayment_schedules (loan_id, installment_no);

CREATE TABLE repayments (
  id                UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  loan_id           UNIQUEIDENTIFIER NOT NULL REFERENCES loans(id),
  group_id          UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  amount            DECIMAL(19,4) NOT NULL,
  currency          NVARCHAR(3) NOT NULL,
  payer_user_id     UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  recorded_by       UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  recorded_via      VARCHAR(20) NOT NULL,
  payment_reference NVARCHAR(120) NULL,
  status            VARCHAR(20) NOT NULL,
  rejection_reason  NVARCHAR(500) NULL,
  confirmed_by      UNIQUEIDENTIFIER NULL REFERENCES users(id),
  confirmed_at      DATETIMEOFFSET NULL,
  idempotency_key   NVARCHAR(64) NULL,
  created_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_repayments_idem ON repayments (loan_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE INDEX IX_repayments_loan ON repayments (loan_id, status);
