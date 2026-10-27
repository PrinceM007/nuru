package com.nuru.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_conversations")
public class AiConversation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(length = 200)
  private String title;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected AiConversation() {
  }

  public AiConversation(UUID userId, UUID groupId, String title) {
    this.userId = userId;
    this.groupId = groupId;
    this.title = title;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
    updatedAt = createdAt;
  }

  // No @PreUpdate: updatedAt moves only via touch() so retention backdating works.

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getTitle() {
    return title;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void touch() {
    updatedAt = OffsetDateTime.now();
  }

  /** Used by retention tests to simulate aged conversations. */
  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
