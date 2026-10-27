package com.nuru.voting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteOptionRepository extends JpaRepository<VoteOption, UUID> {

  List<VoteOption> findByVoteIdOrderByPosition(UUID voteId);

  void deleteByVoteId(UUID voteId);
}
