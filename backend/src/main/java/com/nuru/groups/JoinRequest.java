package com.nuru.groups;

import com.nuru.domain.JoinRequestStatus;
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
@Table(name = "join_requests")
public class JoinRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JoinRequestStatus status;

  @Column(length = 500)
  private String message;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected JoinRequest() {
  }

  public JoinRequest(UUID groupId, UUID userId, String message) {
    this.groupId = groupId;
    this.userId = userId;
    this.message = message;
    this.status = JoinRequestStatus.PENDING;
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

  public UUID getUserId() {
    return userId;
  }

  public JoinRequestStatus getStatus() {
    return status;
  }

  public void setStatus(JoinRequestStatus status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public void setReviewedBy(UUID reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(OffsetDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
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
