package com.nuru.audit;

import com.nuru.domain.Permission;
import com.nuru.service.GroupAuth;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for audit records. Insert-only by design; a database
 * trigger additionally rejects updates and deletes on {@code audit_events}.
 */
@Service
public class AuditService {

  private final AuditEventRepository repository;
  private final GroupAuth groupAuth;

  public AuditService(AuditEventRepository repository, GroupAuth groupAuth) {
    this.repository = repository;
    this.groupAuth = groupAuth;
  }

  @Transactional
  public void record(UUID actorUserId, UUID groupId, String action, String targetType,
      String targetId, String outcome, String details) {
    repository.save(new AuditEvent(actorUserId, groupId, action, targetType, targetId,
        outcome, details, MDC.get("correlationId")));
  }

  @Transactional(readOnly = true)
  public Page<AuditEvent> eventsForGroup(UUID groupId, UUID callerId, Pageable pageable) {
    groupAuth.requirePermission(groupId, callerId, Permission.AUDIT_VIEW);
    return repository.findByGroupIdOrderByOccurredAtDesc(groupId, pageable);
  }
}
