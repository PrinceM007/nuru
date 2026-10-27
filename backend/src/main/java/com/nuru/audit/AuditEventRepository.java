package com.nuru.audit;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read + insert only. No delete or update methods by design. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

  Page<AuditEvent> findByGroupIdOrderByOccurredAtDesc(UUID groupId, Pageable pageable);
}
