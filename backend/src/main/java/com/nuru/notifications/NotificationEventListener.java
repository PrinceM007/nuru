package com.nuru.notifications;

import com.nuru.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends notifications only after the causing transaction commits, so rolled-back
 * actions never notify anyone. Sending runs in its own transaction.
 */
@Component
public class NotificationEventListener {

  private final NotificationService notificationService;

  public NotificationEventListener(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(NotificationEvent event) {
    notificationService.send(event);
  }
}
