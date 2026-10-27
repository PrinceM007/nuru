package com.nuru.service;

import com.nuru.domain.NotificationChannel;
import com.nuru.domain.NotificationKind;
import com.nuru.groups.SavingsGroup;
import com.nuru.groups.SavingsGroupRepository;
import com.nuru.notifications.NotificationEvent;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Single place where domain services announce notifiable events. Delivery itself
 * happens after commit via {@link com.nuru.notifications.NotificationEventListener}.
 */
@Component
public class NotificationPublisher {

  private final ApplicationEventPublisher events;
  private final SavingsGroupRepository groups;

  public NotificationPublisher(ApplicationEventPublisher events, SavingsGroupRepository groups) {
    this.events = events;
    this.groups = groups;
  }

  public void membershipDecided(UUID groupId, UUID applicantId, boolean approved, String detail) {
    publish(applicantId, groupId, emailOnly(), NotificationKind.MEMBERSHIP_DECIDED, Map.of(
        "group", groupName(groupId),
        "decision", approved ? "approved" : "rejected",
        "detail", detail == null ? "" : detail));
  }

  public void joinRequestAlert(UUID groupId, Collection<UUID> adminIds, String applicantName) {
    for (UUID adminId : adminIds) {
      publish(adminId, groupId, emailOnly(), NotificationKind.JOIN_REQUEST_ADMIN_ALERT, Map.of(
          "group", groupName(groupId),
          "applicant", applicantName));
    }
  }

  public void meetingInvitation(UUID groupId, Collection<UUID> inviteeIds, String title,
      String startsAt, String timezone, String where) {
    for (UUID inviteeId : inviteeIds) {
      publish(inviteeId, groupId, emailOnly(), NotificationKind.MEETING_INVITATION, Map.of(
          "group", groupName(groupId),
          "title", title,
          "startsAt", startsAt,
          "timezone", timezone,
          "where", where == null ? "" : where));
    }
  }

  public void meetingUpdated(UUID groupId, Collection<UUID> inviteeIds, String title,
      String startsAt, String timezone) {
    for (UUID inviteeId : inviteeIds) {
      publish(inviteeId, groupId, emailOnly(), NotificationKind.MEETING_UPDATED, Map.of(
          "group", groupName(groupId),
          "title", title,
          "startsAt", startsAt,
          "timezone", timezone));
    }
  }

  public void voteOpened(UUID groupId, Collection<UUID> voterIds, String question,
      String policy, String closesAt) {
    for (UUID voterId : voterIds) {
      publish(voterId, groupId, emailAndSms(), NotificationKind.VOTE_OPENED, Map.of(
          "group", groupName(groupId),
          "question", question,
          "policy", policy.toLowerCase(),
          "closesAt", closesAt));
    }
  }

  public void voteClosed(UUID groupId, Collection<UUID> voterIds, String question) {
    for (UUID voterId : voterIds) {
      publish(voterId, groupId, emailAndSms(), NotificationKind.VOTE_CLOSED, Map.of(
          "group", groupName(groupId),
          "question", question));
    }
  }

  public void loanDecided(UUID groupId, UUID borrowerId, boolean approved, String amount,
      String currency, String detail) {
    publish(borrowerId, groupId, emailOnly(), NotificationKind.LOAN_DECIDED, Map.of(
        "group", groupName(groupId),
        "decision", approved ? "approved" : "rejected",
        "amount", amount,
        "currency", currency,
        "detail", detail == null ? "" : detail));
  }

  public void repaymentConfirmed(UUID groupId, UUID borrowerId, String amount, String currency,
      String outstanding) {
    publish(borrowerId, groupId, emailOnly(), NotificationKind.REPAYMENT_CONFIRMED, Map.of(
        "group", groupName(groupId),
        "amount", amount,
        "currency", currency,
        "outstanding", outstanding));
  }

  public void contributionConfirmed(UUID groupId, UUID memberId, String amount, String currency,
      String period) {
    publish(memberId, groupId, emailOnly(), NotificationKind.CONTRIBUTION_CONFIRMED, Map.of(
        "group", groupName(groupId),
        "amount", amount,
        "currency", currency,
        "period", period));
  }

  private void publish(UUID recipient, UUID groupId, NotificationChannel[] channels,
      NotificationKind kind, Map<String, String> vars) {
    for (NotificationChannel channel : channels) {
      events.publishEvent(new NotificationEvent(recipient, groupId, channel, kind, vars));
    }
  }

  private NotificationChannel[] emailOnly() {
    return new NotificationChannel[] { NotificationChannel.EMAIL };
  }

  private NotificationChannel[] emailAndSms() {
    return new NotificationChannel[] { NotificationChannel.EMAIL, NotificationChannel.SMS };
  }

  private String groupName(UUID groupId) {
    return groups.findById(groupId).map(SavingsGroup::getName).orElse("your group");
  }
}
