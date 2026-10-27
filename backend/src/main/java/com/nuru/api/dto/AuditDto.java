package com.nuru.api.dto;

import com.nuru.audit.AuditEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AuditDto {

  private AuditDto() {
  }

  public record Event(Long id, OffsetDateTime occurredAt, UUID actorUserId, UUID groupId,
      String action, String targetType, String targetId, String outcome, String details,
      String correlationId) {
    public static Event from(AuditEvent event) {
      return new Event(event.getId(), event.getOccurredAt(), event.getActorUserId(),
          event.getGroupId(), event.getAction(), event.getTargetType(), event.getTargetId(),
          event.getOutcome(), event.getDetails(), event.getCorrelationId());
    }
  }
}
