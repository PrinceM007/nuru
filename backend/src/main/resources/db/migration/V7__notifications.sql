-- Nuru V7: notification records. Only subject lines are stored — message bodies
-- are rendered at send time and never persisted or logged.

CREATE TABLE notifications (
  id                 UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  recipient_user_id  UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  group_id           UNIQUEIDENTIFIER NULL REFERENCES groups(id),
  channel            VARCHAR(10) NOT NULL,
  kind               VARCHAR(60) NOT NULL,
  subject            NVARCHAR(200) NULL,
  status             VARCHAR(20) NOT NULL,
  provider           NVARCHAR(80) NULL,
  provider_reference NVARCHAR(200) NULL,
  error_detail       NVARCHAR(500) NULL,
  created_at         DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at         DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_notifications_recipient ON notifications (recipient_user_id, created_at DESC);
CREATE INDEX IX_notifications_group ON notifications (group_id, created_at DESC);
CREATE INDEX IX_notifications_dedupe ON notifications (recipient_user_id, group_id, kind,
  created_at DESC);
