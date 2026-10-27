package com.nuru.voting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteMarkerRepository extends JpaRepository<VoteMarker, Long> {

  boolean existsByVoteIdAndVoterUserId(UUID voteId, UUID voterUserId);

  List<VoteMarker> findByVoteId(UUID voteId);
}
