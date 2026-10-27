-- Nuru V1: foundation. Append-only audit table used by every later stage.
-- All timestamps are UTC (SYSUTCDATETIME default); display timezones live on the row that needs them.

CREATE TABLE audit_events (
  id            BIGINT IDENTITY(1,1) PRIMARY KEY,
  occurred_at   DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  actor_user_id UNIQUEIDENTIFIER NULL,
  group_id      UNIQUEIDENTIFIER NULL,
  action        NVARCHAR(120) NOT NULL,
  target_type   NVARCHAR(120) NULL,
  target_id     NVARCHAR(120) NULL,
  outcome       NVARCHAR(30) NOT NULL,
  details       NVARCHAR(MAX) NULL,
  correlation_id NVARCHAR(64) NULL
);

CREATE INDEX IX_audit_events_group_time ON audit_events (group_id, occurred_at DESC);
CREATE INDEX IX_audit_events_action_time ON audit_events (action, occurred_at DESC);
