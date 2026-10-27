-- Nuru V2: users, auth/session, consents, preferences, groups, roles, memberships, invitations.

CREATE TABLE users (
  id            UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  email         NVARCHAR(320) NOT NULL,
  password_hash NVARCHAR(255) NOT NULL,
  display_name  NVARCHAR(120) NOT NULL,
  phone         NVARCHAR(40) NULL,
  created_at    DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at    DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_users_email ON users (email);

CREATE TABLE user_consents (
  id          BIGINT IDENTITY(1,1) PRIMARY KEY,
  user_id     UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  purpose     NVARCHAR(80) NOT NULL,
  granted     BIT NOT NULL,
  recorded_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_user_consents_user_purpose ON user_consents (user_id, purpose, recorded_at DESC);

CREATE TABLE user_preferences (
  user_id       UNIQUEIDENTIFIER NOT NULL PRIMARY KEY REFERENCES users(id),
  email_enabled BIT NOT NULL DEFAULT 1,
  sms_enabled   BIT NOT NULL DEFAULT 0,
  locale        NVARCHAR(10) NOT NULL DEFAULT 'en',
  updated_at    DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE TABLE refresh_tokens (
  id         UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  user_id    UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  token_hash NVARCHAR(64) NOT NULL,
  expires_at DATETIMEOFFSET NOT NULL,
  revoked_at DATETIMEOFFSET NULL,
  created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX IX_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE groups (
  id          UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  name        NVARCHAR(160) NOT NULL,
  description NVARCHAR(1000) NULL,
  currency    NVARCHAR(3) NOT NULL,
  created_by  UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  created_at  DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at  DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);

CREATE TABLE group_roles (
  id       UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  name     NVARCHAR(60) NOT NULL,
  is_system BIT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX UX_group_roles_group_name ON group_roles (group_id, name);

CREATE TABLE role_permissions (
  id         BIGINT IDENTITY(1,1) PRIMARY KEY,
  role_id    UNIQUEIDENTIFIER NOT NULL REFERENCES group_roles(id) ON DELETE CASCADE,
  permission NVARCHAR(80) NOT NULL
);
CREATE UNIQUE INDEX UX_role_permissions ON role_permissions (role_id, permission);

CREATE TABLE memberships (
  id       UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  user_id  UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  role_id  UNIQUEIDENTIFIER NOT NULL REFERENCES group_roles(id),
  status   VARCHAR(20) NOT NULL,
  joined_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_memberships_group_user ON memberships (group_id, user_id);
CREATE INDEX IX_memberships_user ON memberships (user_id, status);

CREATE TABLE join_requests (
  id               UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id         UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  user_id          UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  status           VARCHAR(20) NOT NULL,
  message          NVARCHAR(500) NULL,
  reviewed_by      UNIQUEIDENTIFIER NULL REFERENCES users(id),
  reviewed_at      DATETIMEOFFSET NULL,
  rejection_reason NVARCHAR(500) NULL,
  created_at       DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_join_requests_pending ON join_requests (group_id, user_id) WHERE status = 'PENDING';

CREATE TABLE invitations (
  id         UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id   UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  code_hash  NVARCHAR(64) NOT NULL,
  role_id    UNIQUEIDENTIFIER NOT NULL REFERENCES group_roles(id),
  created_by UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  expires_at DATETIMEOFFSET NOT NULL,
  revoked_at DATETIMEOFFSET NULL,
  max_uses   INT NOT NULL DEFAULT 1,
  use_count  INT NOT NULL DEFAULT 0,
  created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_invitations_code ON invitations (code_hash);
CREATE INDEX IX_invitations_group ON invitations (group_id);
