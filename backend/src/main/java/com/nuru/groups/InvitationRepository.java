package com.nuru.groups;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

  Optional<Invitation> findByCodeHash(String codeHash);

  Page<Invitation> findByGroupId(UUID groupId, Pageable pageable);
}
