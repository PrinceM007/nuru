package com.nuru.notifications;

import com.nuru.domain.NotificationKind;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId,
      Pageable pageable);

  Page<Notification> findByGroupIdOrderByCreatedAtDesc(UUID groupId, Pageable pageable);

  boolean existsByRecipientUserIdAndGroupIdAndKindAndCreatedAtAfter(UUID recipientUserId,
      UUID groupId, NotificationKind kind, OffsetDateTime after);
}
