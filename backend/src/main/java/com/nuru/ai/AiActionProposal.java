package com.nuru.ai;

import com.nuru.domain.ProposalStatus;
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
@Table(name = "ai_action_proposals")
public class AiActionProposal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "tool_name", nullable = false, length = 80)
  private String toolName;

  @Column(name = "arguments_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
  private String argumentsJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProposalStatus status;

  @Column(name = "result_summary", length = 2000)
  private String resultSummary;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  protected AiActionProposal() {
  }

  public AiActionProposal(UUID conversationId, UUID userId, UUID groupId, String toolName,
      String argumentsJson, OffsetDateTime expiresAt) {
    this.conversationId = conversationId;
    this.userId = userId;
    this.groupId = groupId;
    this.toolName = toolName;
    this.argumentsJson = argumentsJson;
    this.status = ProposalStatus.PENDING;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public boolean isDecidable() {
    return status == ProposalStatus.PENDING && expiresAt.isAfter(OffsetDateTime.now());
  }

  public UUID getId() {
    return id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getToolName() {
    return toolName;
  }

  public String getArgumentsJson() {
    return argumentsJson;
  }

  public ProposalStatus getStatus() {
    return status;
  }

  public void setStatus(ProposalStatus status) {
    this.status = status;
  }

  public String getResultSummary() {
    return resultSummary;
  }

  public void setResultSummary(String resultSummary) {
    this.resultSummary = resultSummary;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(OffsetDateTime decidedAt) {
    this.decidedAt = decidedAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }
}
