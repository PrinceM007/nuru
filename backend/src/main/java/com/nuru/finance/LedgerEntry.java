package com.nuru.finance;

import com.nuru.domain.LedgerKind;
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

/**
 * Immutable journal row. Positive amounts are money into the group,
 * negative amounts are money out. Rows are only ever inserted.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private LedgerKind kind;

  @Column(name = "reference_id", nullable = false)
  private UUID referenceId;

  @Column(name = "member_user_id")
  private UUID memberUserId;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected LedgerEntry() {
  }

  public LedgerEntry(UUID groupId, LedgerKind kind, UUID referenceId, UUID memberUserId,
      BigDecimal amount, String currency, OffsetDateTime occurredAt) {
    this.groupId = groupId;
    this.kind = kind;
    this.referenceId = referenceId;
    this.memberUserId = memberUserId;
    this.amount = amount;
    this.currency = currency;
    this.occurredAt = occurredAt;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public LedgerKind getKind() {
    return kind;
  }

  public UUID getReferenceId() {
    return referenceId;
  }

  public UUID getMemberUserId() {
    return memberUserId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
