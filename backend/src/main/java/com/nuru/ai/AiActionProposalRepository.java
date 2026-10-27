package com.nuru.ai;

import com.nuru.domain.ProposalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiActionProposalRepository extends JpaRepository<AiActionProposal, UUID> {

  List<AiActionProposal> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId,
      ProposalStatus status);

  List<AiActionProposal> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
