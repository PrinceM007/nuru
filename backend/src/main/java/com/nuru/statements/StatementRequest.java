package com.nuru.statements;

import com.nuru.domain.StatementRequestStatus;
import com.nuru.domain.StatementRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "statement_requests")
public class StatementRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "requester_user_id", nullable = false)
  private UUID requesterUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_type", nullable = false, length = 40)
  private StatementRequestType requestType;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StatementRequestStatus status;

  @Column(name = "reviewer_user_id")
  private UUID reviewerUserId;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected StatementRequest() {
  }

  public StatementRequest(UUID groupId, UUID requesterUserId, StatementRequestType requestType,
      LocalDate periodStart, LocalDate periodEnd, StatementRequestStatus status) {
    this.groupId = groupId;
    this.requesterUserId = requesterUserId;
    this.requestType = requestType;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.status = status;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public UUID getRequesterUserId() {
    return requesterUserId;
  }

  public StatementRequestType getRequestType() {
    return requestType;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public StatementRequestStatus getStatus() {
    return status;
  }

  public void setStatus(StatementRequestStatus status) {
    this.status = status;
  }

  public UUID getReviewerUserId() {
    return reviewerUserId;
  }

  public void setReviewerUserId(UUID reviewerUserId) {
    this.reviewerUserId = reviewerUserId;
  }

  public OffsetDateTime getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(OffsetDateTime decidedAt) {
    this.decidedAt = decidedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
