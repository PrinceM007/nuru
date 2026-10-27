package com.nuru.notifications;

import com.nuru.domain.NotificationChannel;
import com.nuru.domain.NotificationKind;
import java.util.Map;
import java.util.UUID;

/** Published by domain services after state changes; sent after commit. */
public record NotificationEvent(UUID recipientUserId, UUID groupId, NotificationChannel channel,
    NotificationKind kind, Map<String, String> vars) {
}
