package com.nuru.voting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BallotRepository extends JpaRepository<Ballot, Long> {

  long countByVoteId(UUID voteId);

  long countByVoteIdAndOptionId(UUID voteId, UUID optionId);

  List<Ballot> findByVoteId(UUID voteId);
}
