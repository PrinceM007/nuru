package com.nuru.finance;

import com.nuru.domain.LoanStatus;
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
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loans")
public class Loan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Column(name = "amount_principal", nullable = false, precision = 19, scale = 4)
  private BigDecimal amountPrincipal;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "interest_rate", nullable = false, precision = 9, scale = 6)
  private BigDecimal interestRate;

  @Column(name = "total_due", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalDue;

  @Column(name = "term_months", nullable = false)
  private int termMonths;

  @Column(length = 500)
  private String purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LoanStatus status;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "requested_at", nullable = false)
  private OffsetDateTime requestedAt;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  @Column(name = "decision_reason", length = 500)
  private String decisionReason;

  @Column(name = "disbursed_at")
  private OffsetDateTime disbursedAt;

  @Column(name = "idempotency_key", length = 64)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Loan() {
  }

  public Loan(UUID groupId, UUID borrowerUserId, BigDecimal amountPrincipal, String currency,
      BigDecimal interestRate, BigDecimal totalDue, int termMonths, String purpose,
      UUID requestedBy, String idempotencyKey) {
    this.groupId = groupId;
    this.borrowerUserId = borrowerUserId;
    this.amountPrincipal = amountPrincipal;
    this.currency = currency;
    this.interestRate = interestRate;
    this.totalDue = totalDue;
    this.termMonths = termMonths;
    this.purpose = purpose;
    this.status = LoanStatus.REQUESTED;
    this.requestedBy = requestedBy;
    this.idempotencyKey = idempotencyKey;
  }

  @PrePersist
  void onCreate() {
    requestedAt = OffsetDateTime.now();
    createdAt = requestedAt;
    updatedAt = requestedAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public UUID getBorrowerUserId() {
    return borrowerUserId;
  }

  public BigDecimal getAmountPrincipal() {
    return amountPrincipal;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getInterestRate() {
    return interestRate;
  }

  public BigDecimal getTotalDue() {
    return totalDue;
  }

  public int getTermMonths() {
    return termMonths;
  }

  public String getPurpose() {
    return purpose;
  }

  public LoanStatus getStatus() {
    return status;
  }

  public void setStatus(LoanStatus status) {
    this.status = status;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public OffsetDateTime getRequestedAt() {
    return requestedAt;
  }

  public UUID getDecidedBy() {
    return decidedBy;
  }

  public void setDecidedBy(UUID decidedBy) {
    this.decidedBy = decidedBy;
  }

  public OffsetDateTime getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(OffsetDateTime decidedAt) {
    this.decidedAt = decidedAt;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public void setDecisionReason(String decisionReason) {
    this.decisionReason = decisionReason;
  }

  public OffsetDateTime getDisbursedAt() {
    return disbursedAt;
  }

  public void setDisbursedAt(OffsetDateTime disbursedAt) {
    this.disbursedAt = disbursedAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }
}
