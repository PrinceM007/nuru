package com.nuru.statements;

import com.nuru.domain.StatementRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "statement_policies")
public class StatementPolicy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_type", nullable = false, length = 40)
  private StatementRequestType requestType;

  @Column(name = "approval_required", nullable = false)
  private boolean approvalRequired;

  @Column(name = "updated_by", nullable = false)
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected StatementPolicy() {
  }

  public StatementPolicy(UUID groupId, StatementRequestType requestType,
      boolean approvalRequired, UUID updatedBy) {
    this.groupId = groupId;
    this.requestType = requestType;
    this.approvalRequired = approvalRequired;
    this.updatedBy = updatedBy;
  }

  @PrePersist
  void onCreate() {
    updatedAt = OffsetDateTime.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public StatementRequestType getRequestType() {
    return requestType;
  }

  public boolean isApprovalRequired() {
    return approvalRequired;
  }

  public void setApprovalRequired(boolean approvalRequired) {
    this.approvalRequired = approvalRequired;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(UUID updatedBy) {
    this.updatedBy = updatedBy;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
