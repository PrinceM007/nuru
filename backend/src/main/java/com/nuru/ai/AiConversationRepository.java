package com.nuru.ai;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

  Page<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

  void deleteByUpdatedAtBefore(OffsetDateTime cutoff);
}
