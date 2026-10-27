package com.nuru.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import com.nuru.support.TestAuth.Session;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class NotificationIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Autowired
  private com.nuru.service.NotificationService notificationService;

  private Session admin;
  private Session member;
  private String groupId;

  @BeforeEach
  void setUp() {
    admin = TestAuth.register(rest, "notadmin");
    member = TestAuth.register(rest, "notmember");
    groupId = createGroup(admin, "Notify Group", "KES");
    joinAsMember(admin, groupId, member);
  }

  @Test
  void membershipDecisionsNotifyApplicantAndAdmins() {
    Session applicant = TestAuth.register(rest, "notapplicant");
    TestAuth.post(rest, applicant.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests", Map.of(), Map.class);

    // Admin received the alert; dev adapter reports SIMULATED, never delivered.
    List<Map<String, Object>> adminNotes = myNotifications(admin);
    assertThat(adminNotes).anySatisfy(note -> {
      assertThat(note).containsEntry("kind", "JOIN_REQUEST_ADMIN_ALERT");
      assertThat(note).containsEntry("status", "SIMULATED");
      assertThat(note).containsEntry("provider", "LOG");
    });

    ResponseEntity<Map> requests = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests?status=PENDING", Map.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content =
        (List<Map<String, Object>>) requests.getBody().get("content");
    String requestId = (String) content.get(0).get("id");
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests/" + requestId + "/approve", Map.of(),
        Map.class);

    assertThat(myNotifications(applicant)).anySatisfy(note ->
        assertThat(note).containsEntry("kind", "MEMBERSHIP_DECIDED"));
  }

  @Test
  void disabledChannelSkipsDelivery() {
    TestAuth.patch(rest, member.accessToken(), "/api/v1/users/me/preferences",
        Map.of("emailEnabled", false), Map.class);
    recordAndConfirm(member, admin, "100.00");

    assertThat(myNotifications(member)).anySatisfy(note -> {
      assertThat(note).containsEntry("kind", "CONTRIBUTION_CONFIRMED");
      assertThat(note).containsEntry("status", "SKIPPED");
    });
  }

  @Test
  void meetingsAndVotesNotifyMembers() {
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings",
        Map.of("title", "Notify meeting",
            "startsAt", OffsetDateTime.now().plusDays(1).toString(),
            "timezone", "Africa/Nairobi"),
        Map.class);
    assertThat(myNotifications(member)).anySatisfy(note ->
        assertThat(note).containsEntry("kind", "MEETING_INVITATION"));

    ResponseEntity<Map> vote = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes",
        Map.of("question", "Notify vote?", "ballotPolicy", "SECRET",
            "opensAt", OffsetDateTime.now().minusHours(1).toString(),
            "closesAt", OffsetDateTime.now().plusHours(1).toString(),
            "options", List.of("Yes", "No")),
        Map.class);
    String voteId = (String) vote.getBody().get("id");
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/open", Map.of(), Map.class);
    assertThat(myNotifications(member)).anySatisfy(note -> {
      assertThat(note).containsEntry("kind", "VOTE_OPENED");
      assertThat(note).containsEntry("channel", "EMAIL");
    });
  }

  @Test
  void overdueRemindersAreSentOncePerDay() {
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions",
        Map.of("memberUserId", member.userId().toString(), "periodStart", "2025-01-01",
            "periodEnd", "2025-01-31", "amount", "200.00", "assessment", true),
        Map.class);

    notificationService.sendOverdueContributionReminders();
    notificationService.sendOverdueContributionReminders();

    long reminders = myNotifications(member).stream()
        .filter(note -> "CONTRIBUTION_REMINDER".equals(note.get("kind"))
            && "EMAIL".equals(note.get("channel")))
        .count();
    assertThat(reminders).isEqualTo(1);
  }

  @Test
  void groupNotificationViewNeedsAuditRights() {
    assertThat(TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/notifications", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/notifications", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> myNotifications(Session session) {
    ResponseEntity<Map> response = TestAuth.get(rest, session.accessToken(),
        "/api/v1/notifications/me?size=50", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return (List<Map<String, Object>>) response.getBody().get("content");
  }

  private void recordAndConfirm(Session payer, Session confirmer, String amount) {
    ResponseEntity<Map> recorded = TestAuth.post(rest, payer.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions",
        Map.of("periodStart", "2026-10-01", "periodEnd", "2026-10-31", "amount", amount,
            "assessment", false),
        Map.class);
    String id = (String) recorded.getBody().get("id");
    TestAuth.post(rest, confirmer.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + id + "/confirm", Map.of(), Map.class);
  }

  private String createGroup(Session owner, String name, String currency) {
    ResponseEntity<Map> response = TestAuth.post(rest, owner.accessToken(), "/api/v1/groups",
        Map.of("name", name, "currency", currency), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) response.getBody().get("id");
  }

  private void joinAsMember(Session adminSession, String group, Session applicant) {
    ResponseEntity<Map> requested = TestAuth.post(rest, applicant.accessToken(),
        "/api/v1/groups/" + group + "/join-requests", Map.of(), Map.class);
    String requestId = (String) requested.getBody().get("id");
    ResponseEntity<Map> approved = TestAuth.post(rest, adminSession.accessToken(),
        "/api/v1/groups/" + group + "/join-requests/" + requestId + "/approve", Map.of(),
        Map.class);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
