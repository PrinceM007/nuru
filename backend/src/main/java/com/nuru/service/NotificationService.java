package com.nuru.service;

import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.ContributionStatus;
import com.nuru.domain.NotificationChannel;
import com.nuru.domain.NotificationKind;
import com.nuru.domain.NotificationStatus;
import com.nuru.finance.Contribution;
import com.nuru.finance.ContributionRepository;
import com.nuru.groups.SavingsGroup;
import com.nuru.groups.SavingsGroupRepository;
import com.nuru.notifications.Notification;
import com.nuru.notifications.NotificationEvent;
import com.nuru.notifications.NotificationRepository;
import com.nuru.notifications.NotificationTemplates;
import com.nuru.provider.email.EmailProvider;
import com.nuru.provider.sms.SmsProvider;
import com.nuru.users.User;
import com.nuru.users.UserPreference;
import com.nuru.users.UserPreferenceRepository;
import com.nuru.users.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Best-effort notification delivery. Provider failures are recorded as FAILED —
 * they never throw into the calling workflow. Delivery is only ever reported as
 * DELIVERED when the provider confirms it; the dev adapters report SIMULATED.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationRepository notifications;
  private final UserRepository users;
  private final UserPreferenceRepository preferences;
  private final ContributionRepository contributions;
  private final SavingsGroupRepository groups;
  private final EmailProvider emailProvider;
  private final SmsProvider smsProvider;
  private final AuditService auditService;

  public NotificationService(NotificationRepository notifications, UserRepository users,
      UserPreferenceRepository preferences, ContributionRepository contributions,
      SavingsGroupRepository groups, EmailProvider emailProvider, SmsProvider smsProvider,
      AuditService auditService) {
    this.notifications = notifications;
    this.users = users;
    this.preferences = preferences;
    this.contributions = contributions;
    this.groups = groups;
    this.emailProvider = emailProvider;
    this.smsProvider = smsProvider;
    this.auditService = auditService;
  }

  /**
   * Always runs in its own transaction: after-commit listeners must not join the
   * completed transaction (writes would attach to a dead session and be silently
   * lost), and reminder sends must commit independently of each other.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void send(NotificationEvent event) {
    try {
      sendInternal(event);
    } catch (Exception e) {
      // Best effort: a notification must never break the group action that caused it.
      log.warn("Notification {} to {} failed: {}", event.kind(), event.recipientUserId(),
          e.getMessage());
      Notification failed = new Notification(event.recipientUserId(), event.groupId(),
          event.channel(), event.kind(), null, NotificationStatus.FAILED);
      failed.setErrorDetail(truncate(e.getMessage(), 500));
      notifications.save(failed);
    }
  }

  private void sendInternal(NotificationEvent event) {
    User recipient = users.findById(event.recipientUserId()).orElse(null);
    if (recipient == null) {
      return;
    }
    Map<String, String> vars = new HashMap<>(event.vars());
    vars.put("name", recipient.getDisplayName());
    NotificationTemplates.Rendered rendered =
        NotificationTemplates.render(event.kind(), vars);
    Notification notification = new Notification(event.recipientUserId(), event.groupId(),
        event.channel(), event.kind(), truncate(rendered.subject(), 200),
        NotificationStatus.QUEUED);

    UserPreference pref = preferences.findById(recipient.getId()).orElse(null);
    boolean channelEnabled = event.channel() == NotificationChannel.EMAIL
        ? pref == null || pref.isEmailEnabled()
        : pref != null && pref.isSmsEnabled();
    if (!channelEnabled) {
      notification.setStatus(NotificationStatus.SKIPPED);
      notification.setErrorDetail("Channel disabled in user preferences");
      notifications.save(notification);
      return;
    }
    if (event.channel() == NotificationChannel.SMS
        && (recipient.getPhone() == null || recipient.getPhone().isBlank())) {
      notification.setStatus(NotificationStatus.SKIPPED);
      notification.setErrorDetail("No phone number on file");
      notifications.save(notification);
      return;
    }

    if (event.channel() == NotificationChannel.EMAIL) {
      if (!emailProvider.isAvailable()) {
        markFailed(notification, "Email provider is not configured");
        return;
      }
      try {
        EmailProvider.EmailResult result = emailProvider.send(recipient.getId(),
            recipient.getEmail(), rendered.subject(), rendered.body());
        notification.setProvider(emailProvider.name());
        notification.setProviderReference(result.providerReference());
        notification.setStatus(result.delivered() ? NotificationStatus.DELIVERED
            : "LOG".equals(emailProvider.name()) ? NotificationStatus.SIMULATED
                : NotificationStatus.SENT);
        if (!result.delivered()) {
          notification.setErrorDetail(truncate(result.detail(), 500));
        }
      } catch (Exception e) {
        markFailed(notification, "Email provider error: " + e.getMessage());
        return;
      }
    } else {
      if (!smsProvider.isAvailable()) {
        markFailed(notification, "SMS provider is not configured");
        return;
      }
      try {
        SmsProvider.SmsResult result = smsProvider.send(recipient.getId(),
            recipient.getPhone(), rendered.body());
        notification.setProvider(smsProvider.name());
        notification.setProviderReference(result.providerReference());
        notification.setStatus(result.delivered() ? NotificationStatus.DELIVERED
            : "LOG".equals(smsProvider.name()) ? NotificationStatus.SIMULATED
                : NotificationStatus.SENT);
        if (!result.delivered()) {
          notification.setErrorDetail(truncate(result.detail(), 500));
        }
      } catch (Exception e) {
        markFailed(notification, "SMS provider error: " + e.getMessage());
        return;
      }
    }
    notifications.saveAndFlush(notification);
    auditService.record(null, event.groupId(), AuditActions.NOTIFICATION_DELIVERY_RECORDED,
        "Notification",
        notification.getId() == null ? null : notification.getId().toString(),
        notification.getStatus().name(),
        "kind=" + event.kind() + ",channel=" + event.channel());
  }

  private void markFailed(Notification notification, String reason) {
    notification.setStatus(NotificationStatus.FAILED);
    notification.setErrorDetail(truncate(reason, 500));
    notifications.save(notification);
  }

  /** Daily overdue-contribution reminders, at most one per member per group per day. */
  @Scheduled(cron = "0 0 8 * * *")
  @Transactional
  public void sendOverdueContributionReminders() {
    LocalDate today = LocalDate.now();
    OffsetDateTime dayStart = today.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
    List<Contribution> overdue = contributions.findByStatusAndPeriodEndBefore(
        ContributionStatus.EXPECTED, today, Pageable.ofSize(200)).getContent();
    for (Contribution contribution : overdue) {
      if (notifications.existsByRecipientUserIdAndGroupIdAndKindAndCreatedAtAfter(
          contribution.getMemberUserId(), contribution.getGroupId(),
          NotificationKind.CONTRIBUTION_REMINDER, dayStart)) {
        continue;
      }
      String groupName = groups.findById(contribution.getGroupId())
          .map(SavingsGroup::getName).orElse("your group");
      Map<String, String> vars = Map.of(
          "group", groupName,
          "amount", contribution.getAmount().toPlainString(),
          "currency", contribution.getCurrency(),
          "period", contribution.getPeriodStart() + " to " + contribution.getPeriodEnd());
      send(new NotificationEvent(contribution.getMemberUserId(), contribution.getGroupId(),
          NotificationChannel.EMAIL, NotificationKind.CONTRIBUTION_REMINDER, vars));
      send(new NotificationEvent(contribution.getMemberUserId(), contribution.getGroupId(),
          NotificationChannel.SMS, NotificationKind.CONTRIBUTION_REMINDER, vars));
    }
  }

  @Transactional(readOnly = true)
  public Page<Notification> myNotifications(UUID callerId, Pageable pageable) {
    return notifications.findByRecipientUserIdOrderByCreatedAtDesc(callerId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<Notification> groupNotifications(UUID groupId, UUID callerId,
      GroupAuth groupAuth, Pageable pageable) {
    groupAuth.requirePermission(groupId, callerId,
        com.nuru.domain.Permission.AUDIT_VIEW);
    return notifications.findByGroupIdOrderByCreatedAtDesc(groupId, pageable);
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
