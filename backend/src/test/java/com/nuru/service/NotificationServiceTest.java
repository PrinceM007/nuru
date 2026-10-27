package com.nuru.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuru.audit.AuditService;
import com.nuru.domain.NotificationChannel;
import com.nuru.domain.NotificationKind;
import com.nuru.domain.NotificationStatus;
import com.nuru.finance.ContributionRepository;
import com.nuru.groups.SavingsGroupRepository;
import com.nuru.notifications.Notification;
import com.nuru.notifications.NotificationEvent;
import com.nuru.notifications.NotificationRepository;
import com.nuru.provider.email.EmailProvider;
import com.nuru.provider.sms.SmsProvider;
import com.nuru.users.User;
import com.nuru.users.UserPreference;
import com.nuru.users.UserPreferenceRepository;
import com.nuru.users.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

  private NotificationRepository notifications;
  private UserRepository users;
  private UserPreferenceRepository preferences;
  private EmailProvider emailProvider;
  private NotificationService service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    notifications = mock(NotificationRepository.class);
    users = mock(UserRepository.class);
    preferences = mock(UserPreferenceRepository.class);
    ContributionRepository contributions = mock(ContributionRepository.class);
    SavingsGroupRepository groups = mock(SavingsGroupRepository.class);
    emailProvider = mock(EmailProvider.class);
    SmsProvider smsProvider = mock(SmsProvider.class);
    AuditService auditService = mock(AuditService.class);
    service = new NotificationService(notifications, users, preferences, contributions,
        groups, emailProvider, smsProvider, auditService);

    userId = UUID.randomUUID();
    User user = mock(User.class);
    when(user.getId()).thenReturn(userId);
    when(user.getEmail()).thenReturn("member@example.com");
    when(user.getDisplayName()).thenReturn("Member");
    when(users.findById(userId)).thenReturn(Optional.of(user));
    UserPreference pref = mock(UserPreference.class);
    when(pref.isEmailEnabled()).thenReturn(true);
    when(preferences.findById(userId)).thenReturn(Optional.of(pref));
  }

  @Test
  void providerFailureRecordsFailedAndNeverThrows() {
    when(emailProvider.isAvailable()).thenReturn(true);
    when(emailProvider.name()).thenReturn("BROKEN");
    when(emailProvider.send(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("gateway down"));

    NotificationEvent event = new NotificationEvent(userId, UUID.randomUUID(),
        NotificationChannel.EMAIL, NotificationKind.VOTE_OPENED,
        Map.of("group", "g", "question", "q", "policy", "secret", "closesAt", "c"));

    assertThatCode(() -> service.send(event)).doesNotThrowAnyException();

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
  }

  @Test
  void confirmedDeliveryRecordsDelivered() {
    when(emailProvider.isAvailable()).thenReturn(true);
    when(emailProvider.name()).thenReturn("GATEWAY");
    when(emailProvider.send(any(), any(), any(), any()))
        .thenReturn(new EmailProvider.EmailResult(true, "ref-1", "ok"));

    NotificationEvent event = new NotificationEvent(userId, UUID.randomUUID(),
        NotificationChannel.EMAIL, NotificationKind.VOTE_OPENED,
        Map.of("group", "g", "question", "q", "policy", "secret", "closesAt", "c"));
    service.send(event);

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    assertThat(saved.getValue().getProviderReference()).isEqualTo("ref-1");
  }

  @Test
  void disabledChannelIsSkippedWithoutCallingProvider() {
    UserPreference pref = mock(UserPreference.class);
    when(pref.isEmailEnabled()).thenReturn(false);
    when(preferences.findById(userId)).thenReturn(Optional.of(pref));

    NotificationEvent event = new NotificationEvent(userId, UUID.randomUUID(),
        NotificationChannel.EMAIL, NotificationKind.VOTE_OPENED,
        Map.of("group", "g", "question", "q", "policy", "secret", "closesAt", "c"));
    service.send(event);

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
  }
}
