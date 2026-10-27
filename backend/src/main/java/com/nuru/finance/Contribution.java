package com.nuru.finance;

import com.nuru.domain.ContributionStatus;
import com.nuru.domain.RecordedVia;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "contributions")
public class Contribution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "member_user_id", nullable = false)
  private UUID memberUserId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ContributionStatus status;

  @Column(name = "recorded_by", nullable = false)
  private UUID recordedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "recorded_via", nullable = false, length = 20)
  private RecordedVia recordedVia;

  @Column(name = "payment_reference", length = 120)
  private String paymentReference;

  @Column(name = "confirmed_by")
  private UUID confirmedBy;

  @Column(name = "confirmed_at")
  private OffsetDateTime confirmedAt;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "correction_of")
  private UUID correctionOf;

  @Column(length = 1000)
  private String notes;

  @Column(name = "idempotency_key", length = 64)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Contribution() {
  }

  public Contribution(UUID groupId, UUID scheduleId, UUID memberUserId, LocalDate periodStart,
      LocalDate periodEnd, BigDecimal amount, String currency, ContributionStatus status,
      UUID recordedBy, RecordedVia recordedVia, String paymentReference, UUID correctionOf,
      String notes, String idempotencyKey) {
    this.groupId = groupId;
    this.scheduleId = scheduleId;
    this.memberUserId = memberUserId;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.amount = amount;
    this.currency = currency;
    this.status = status;
    this.recordedBy = recordedBy;
    this.recordedVia = recordedVia;
    this.paymentReference = paymentReference;
    this.correctionOf = correctionOf;
    this.notes = notes;
    this.idempotencyKey = idempotencyKey;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public boolean isConfirmed() {
    return status == ContributionStatus.CONFIRMED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getMemberUserId() {
    return memberUserId;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public void setPeriodStart(LocalDate periodStart) {
    this.periodStart = periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public void setPeriodEnd(LocalDate periodEnd) {
    this.periodEnd = periodEnd;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public ContributionStatus getStatus() {
    return status;
  }

  public void setStatus(ContributionStatus status) {
    this.status = status;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public RecordedVia getRecordedVia() {
    return recordedVia;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public UUID getConfirmedBy() {
    return confirmedBy;
  }

  public void setConfirmedBy(UUID confirmedBy) {
    this.confirmedBy = confirmedBy;
  }

  public OffsetDateTime getConfirmedAt() {
    return confirmedAt;
  }

  public void setConfirmedAt(OffsetDateTime confirmedAt) {
    this.confirmedAt = confirmedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public UUID getCorrectionOf() {
    return correctionOf;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
