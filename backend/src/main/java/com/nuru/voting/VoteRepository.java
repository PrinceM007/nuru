package com.nuru.voting;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

  Page<Vote> findByGroupIdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);
}
