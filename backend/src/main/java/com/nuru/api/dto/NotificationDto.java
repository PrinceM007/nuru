package com.nuru.api.dto;

import com.nuru.notifications.Notification;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class NotificationDto {

  private NotificationDto() {
  }

  public record Response(UUID id, UUID groupId, String channel, String kind, String subject,
      String status, String provider, String providerReference, String errorDetail,
      OffsetDateTime createdAt) {
    public static Response from(Notification notification) {
      return new Response(notification.getId(), notification.getGroupId(),
          notification.getChannel().name(), notification.getKind().name(),
          notification.getSubject(), notification.getStatus().name(),
          notification.getProvider(), notification.getProviderReference(),
          notification.getErrorDetail(), notification.getCreatedAt());
    }
  }
}
