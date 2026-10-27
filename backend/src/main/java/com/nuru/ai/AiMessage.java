package com.nuru.ai;

import com.nuru.domain.AiMessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
public class AiMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AiMessageRole role;

  @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
  private String content;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AiMessage() {
  }

  public AiMessage(UUID conversationId, AiMessageRole role, String content) {
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public AiMessageRole getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
