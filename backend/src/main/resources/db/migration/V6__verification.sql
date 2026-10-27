-- Nuru V6: identity verification status (provider references only — no documents).
-- Status history is tracked through audit events (verification.status_changed).

CREATE TABLE verification_status (
  user_id            UNIQUEIDENTIFIER NOT NULL PRIMARY KEY REFERENCES users(id),
  status             VARCHAR(30) NOT NULL,
  provider           NVARCHAR(80) NULL,
  provider_reference NVARCHAR(200) NULL,
  verified_at        DATETIMEOFFSET NULL,
  expires_at         DATETIMEOFFSET NULL,
  failure_reason     NVARCHAR(500) NULL,
  updated_at         DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
