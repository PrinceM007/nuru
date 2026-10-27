-- Nuru V4: meetings, votes with secret-ballot support, statement requests.

CREATE TABLE meetings (
  id           UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id     UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  title        NVARCHAR(200) NOT NULL,
  agenda       NVARCHAR(2000) NULL,
  starts_at    DATETIMEOFFSET NOT NULL,
  ends_at      DATETIMEOFFSET NULL,
  timezone     NVARCHAR(64) NOT NULL,
  location     NVARCHAR(500) NULL,
  meeting_link NVARCHAR(500) NULL,
  status       VARCHAR(20) NOT NULL,
  created_by   UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  created_at   DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at   DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_meetings_group_time ON meetings (group_id, starts_at);

CREATE TABLE meeting_participants (
  id         BIGINT IDENTITY(1,1) PRIMARY KEY,
  meeting_id UNIQUEIDENTIFIER NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
  user_id    UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  attendance VARCHAR(20) NOT NULL
);
CREATE UNIQUE INDEX UX_meeting_participants ON meeting_participants (meeting_id, user_id);

CREATE TABLE meeting_notes (
  id             UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  meeting_id     UNIQUEIDENTIFIER NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
  author_user_id UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  body           NVARCHAR(MAX) NOT NULL,
  created_at     DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at     DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_meeting_notes_meeting ON meeting_notes (meeting_id, created_at);

CREATE TABLE votes (
  id            UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id      UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  question      NVARCHAR(500) NOT NULL,
  description   NVARCHAR(2000) NULL,
  ballot_policy VARCHAR(20) NOT NULL,
  status        VARCHAR(20) NOT NULL,
  opens_at      DATETIMEOFFSET NOT NULL,
  closes_at     DATETIMEOFFSET NOT NULL,
  quorum        INT NULL,
  created_by    UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  created_at    DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  published_at  DATETIMEOFFSET NULL,
  published_by  UNIQUEIDENTIFIER NULL REFERENCES users(id)
);
CREATE INDEX IX_votes_group ON votes (group_id, status);

CREATE TABLE vote_options (
  id       UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  vote_id  UNIQUEIDENTIFIER NOT NULL REFERENCES votes(id) ON DELETE CASCADE,
  label    NVARCHAR(200) NOT NULL,
  position INT NOT NULL
);
CREATE UNIQUE INDEX UX_vote_options_position ON vote_options (vote_id, position);

-- Frozen eligibility snapshot taken when the vote opens.
CREATE TABLE vote_eligibility (
  id          BIGINT IDENTITY(1,1) PRIMARY KEY,
  vote_id     UNIQUEIDENTIFIER NOT NULL REFERENCES votes(id) ON DELETE CASCADE,
  user_id     UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  snapshot_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_vote_eligibility ON vote_eligibility (vote_id, user_id);

-- Anonymous choice rows. voter_user_id is filled ONLY for attributable votes;
-- for secret votes it stays NULL so choices cannot be linked to voters.
CREATE TABLE ballots (
  id             BIGINT IDENTITY(1,1) PRIMARY KEY,
  vote_id        UNIQUEIDENTIFIER NOT NULL REFERENCES votes(id) ON DELETE CASCADE,
  option_id      UNIQUEIDENTIFIER NOT NULL REFERENCES vote_options(id),
  voter_user_id  UNIQUEIDENTIFIER NULL REFERENCES users(id),
  cast_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_ballots_vote_option ON ballots (vote_id, option_id);

-- One row per voter per vote: enforces one ballot each without linking to a choice.
CREATE TABLE vote_markers (
  id            BIGINT IDENTITY(1,1) PRIMARY KEY,
  vote_id       UNIQUEIDENTIFIER NOT NULL REFERENCES votes(id) ON DELETE CASCADE,
  voter_user_id UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  cast_at       DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_vote_markers ON vote_markers (vote_id, voter_user_id);

CREATE TABLE statement_policies (
  id                BIGINT IDENTITY(1,1) PRIMARY KEY,
  group_id          UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  request_type      VARCHAR(40) NOT NULL,
  approval_required BIT NOT NULL,
  updated_by        UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  updated_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE UNIQUE INDEX UX_statement_policies ON statement_policies (group_id, request_type);

CREATE TABLE statement_requests (
  id                UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  group_id          UNIQUEIDENTIFIER NOT NULL REFERENCES groups(id),
  requester_user_id UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  request_type      VARCHAR(40) NOT NULL,
  period_start      DATE NOT NULL,
  period_end        DATE NOT NULL,
  status            VARCHAR(20) NOT NULL,
  reviewer_user_id  UNIQUEIDENTIFIER NULL REFERENCES users(id),
  decided_at        DATETIMEOFFSET NULL,
  rejection_reason  NVARCHAR(500) NULL,
  created_at        DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_statement_requests_group ON statement_requests (group_id, status);
