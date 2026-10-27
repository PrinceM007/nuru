package com.nuru.service;

import com.nuru.ai.AiConversationRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversation retention: conversations (with messages and proposals via FK
 * cascade) older than the configured window are purged nightly. Users can also
 * delete their own conversations at any time.
 */
@Service
public class AiRetentionService {

  private static final Logger log = LoggerFactory.getLogger(AiRetentionService.class);

  private final AiConversationRepository conversations;
  private final long retentionDays;

  public AiRetentionService(AiConversationRepository conversations,
      @Value("${nuru.ai.retention-days:90}") long retentionDays) {
    this.conversations = conversations;
    this.retentionDays = retentionDays;
  }

  @Scheduled(cron = "0 0 3 * * *")
  public void purgeNightly() {
    purgeOlderThan(retentionDays);
  }

  @Transactional
  public void purgeOlderThan(long days) {
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
    conversations.deleteByUpdatedAtBefore(cutoff);
    log.info("Purged AI conversations older than {}", cutoff);
  }
}
