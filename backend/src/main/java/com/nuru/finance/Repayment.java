package com.nuru.finance;

import com.nuru.domain.RecordedVia;
import com.nuru.domain.RepaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayments")
public class Repayment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "payer_user_id", nullable = false)
  private UUID payerUserId;

  @Column(name = "recorded_by", nullable = false)
  private UUID recordedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "recorded_via", nullable = false, length = 20)
  private RecordedVia recordedVia;

  @Column(name = "payment_reference", length = 120)
  private String paymentReference;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RepaymentStatus status;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "confirmed_by")
  private UUID confirmedBy;

  @Column(name = "confirmed_at")
  private OffsetDateTime confirmedAt;

  @Column(name = "idempotency_key", length = 64)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected Repayment() {
  }

  public Repayment(UUID loanId, UUID groupId, BigDecimal amount, String currency,
      UUID payerUserId, UUID recordedBy, RecordedVia recordedVia, String paymentReference,
      String idempotencyKey) {
    this.loanId = loanId;
    this.groupId = groupId;
    this.amount = amount;
    this.currency = currency;
    this.payerUserId = payerUserId;
    this.recordedBy = recordedBy;
    this.recordedVia = recordedVia;
    this.paymentReference = paymentReference;
    this.status = RepaymentStatus.SUBMITTED;
    this.idempotencyKey = idempotencyKey;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public UUID getPayerUserId() {
    return payerUserId;
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

  public RepaymentStatus getStatus() {
    return status;
  }

  public void setStatus(RepaymentStatus status) {
    this.status = status;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
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

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
