-- Nuru V5: AI conversations, messages, and action proposals.
-- Only conversation data needed for the feature is stored; prompts never include
-- credentials or secrets. Retention purge deletes old conversations (see AiRetentionService).

CREATE TABLE ai_conversations (
  id         UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  user_id    UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  group_id   UNIQUEIDENTIFIER NULL REFERENCES groups(id),
  title      NVARCHAR(200) NULL,
  created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_ai_conversations_user ON ai_conversations (user_id, updated_at DESC);

CREATE TABLE ai_messages (
  id              BIGINT IDENTITY(1,1) PRIMARY KEY,
  conversation_id UNIQUEIDENTIFIER NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
  role            VARCHAR(20) NOT NULL,
  content         NVARCHAR(MAX) NOT NULL,
  created_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET()
);
CREATE INDEX IX_ai_messages_conversation ON ai_messages (conversation_id, id);

CREATE TABLE ai_action_proposals (
  id              UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
  conversation_id UNIQUEIDENTIFIER NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
  user_id         UNIQUEIDENTIFIER NOT NULL REFERENCES users(id),
  group_id        UNIQUEIDENTIFIER NULL REFERENCES groups(id),
  tool_name       VARCHAR(80) NOT NULL,
  arguments_json  NVARCHAR(MAX) NOT NULL,
  status          VARCHAR(20) NOT NULL,
  result_summary  NVARCHAR(2000) NULL,
  created_at      DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
  decided_at      DATETIMEOFFSET NULL,
  expires_at      DATETIMEOFFSET NOT NULL
);
CREATE INDEX IX_ai_proposals_user ON ai_action_proposals (user_id, status);
