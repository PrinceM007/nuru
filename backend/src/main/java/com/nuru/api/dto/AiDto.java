package com.nuru.api.dto;

import com.nuru.ai.AiConversation;
import com.nuru.ai.AiMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiDto {

  private AiDto() {
  }

  public record Status(String provider, boolean available, String detail) {
  }

  public record CreateConversationRequest(UUID groupId, @Size(max = 200) String title) {
  }

  public record Conversation(UUID id, UUID groupId, String title, OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    public static Conversation from(AiConversation conversation) {
      return new Conversation(conversation.getId(), conversation.getGroupId(),
          conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }
  }

  public record Message(Long id, String role, String content, OffsetDateTime createdAt) {
    public static Message from(AiMessage message) {
      return new Message(message.getId(), message.getRole().name(), message.getContent(),
          message.getCreatedAt());
    }
  }

  public record ConversationDetail(UUID id, UUID groupId, String title,
      OffsetDateTime createdAt, OffsetDateTime updatedAt, List<Message> messages,
      List<Proposal> proposals) {
    public static ConversationDetail from(AiConversation conversation, List<Message> messages,
        List<Proposal> proposals) {
      return new ConversationDetail(conversation.getId(), conversation.getGroupId(),
          conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt(),
          messages, proposals);
    }
  }

  public record ChatRequest(@NotBlank @Size(min = 1, max = 4000) String text) {
  }

  public record ExecutedTool(String tool, String summary, Map<String, Object> facts) {
  }

  public record Proposal(UUID id, UUID conversationId, UUID groupId, String tool,
      String description, Map<String, Object> args, String status, String resultSummary,
      OffsetDateTime createdAt, OffsetDateTime expiresAt, OffsetDateTime decidedAt) {
  }

  public record ChatResponse(String assistantMessage, List<ExecutedTool> executedTools,
      List<Proposal> proposals) {
  }
}
