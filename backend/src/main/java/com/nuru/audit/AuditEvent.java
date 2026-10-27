package com.nuru.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit record. There is intentionally no update/delete path:
 * services only insert, admins only read. Tamper-evidence limits are
 * documented in docs/PRODUCTION_CHECKLIST.md.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "action", nullable = false, length = 120)
  private String action;

  @Column(name = "target_type", length = 120)
  private String targetType;

  @Column(name = "target_id", length = 120)
  private String targetId;

  @Column(name = "outcome", nullable = false, length = 30)
  private String outcome;

  @Column(name = "details", columnDefinition = "NVARCHAR(MAX)")
  private String details;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  protected AuditEvent() {
  }

  public AuditEvent(UUID actorUserId, UUID groupId, String action, String targetType,
      String targetId, String outcome, String details, String correlationId) {
    this.occurredAt = OffsetDateTime.now();
    this.actorUserId = actorUserId;
    this.groupId = groupId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.outcome = outcome;
    this.details = details;
    this.correlationId = correlationId;
  }

  public Long getId() {
    return id;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public String getAction() {
    return action;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getOutcome() {
    return outcome;
  }

  public String getDetails() {
    return details;
  }

  public String getCorrelationId() {
    return correlationId;
  }
}
