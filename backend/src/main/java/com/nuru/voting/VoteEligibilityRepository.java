package com.nuru.voting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteEligibilityRepository extends JpaRepository<VoteEligibility, Long> {

  boolean existsByVoteIdAndUserId(UUID voteId, UUID userId);

  long countByVoteId(UUID voteId);

  List<VoteEligibility> findByVoteId(UUID voteId);
}
