package com.nuru.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

  List<AiMessage> findByConversationIdOrderById(UUID conversationId);

  void deleteByConversationId(UUID conversationId);
}
