package com.nuru.groups;

import com.nuru.domain.JoinRequestStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

  Optional<JoinRequest> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId,
      JoinRequestStatus status);

  Page<JoinRequest> findByGroupIdAndStatus(UUID groupId, JoinRequestStatus status, Pageable pageable);

  Page<JoinRequest> findByGroupId(UUID groupId, Pageable pageable);
}
