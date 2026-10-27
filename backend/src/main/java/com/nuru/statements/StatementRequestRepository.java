package com.nuru.statements;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementRequestRepository extends JpaRepository<StatementRequest, UUID> {

  Page<StatementRequest> findByGroupId(UUID groupId, Pageable pageable);

  Page<StatementRequest> findByGroupIdAndRequesterUserId(UUID groupId, UUID requesterUserId,
      Pageable pageable);
}
