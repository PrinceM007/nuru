package com.nuru.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import com.nuru.support.TestAuth.Session;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/** AI assistant flows against the deterministic DEV provider. */
@TestPropertySource(properties = "nuru.ai.provider=DEV")
class AiIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Autowired
  private AiConversationRepository conversations;

  @Autowired
  private com.nuru.service.AiRetentionService retentionService;

  private Session admin;
  private Session member;
  private String groupId;

  @BeforeEach
  void setUp() {
    admin = TestAuth.register(rest, "aiadmin");
    member = TestAuth.register(rest, "aimember");
    groupId = createGroup(admin, "AI Group", "KES");
    joinAsMember(admin, groupId, member);
    grantAiConsent(admin);
    grantAiConsent(member);
  }

  @Test
  void statusReportsDevProvider() {
    ResponseEntity<Map> status = TestAuth.get(rest, admin.accessToken(), "/api/v1/ai/status",
        Map.class);
    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(status.getBody())
        .containsEntry("provider", "DEV")
        .containsEntry("available", true);
  }

  @Test
  void consentIsRequiredForAssistantUse() {
    Session holdout = TestAuth.register(rest, "aiholdout");
    joinAsMember(admin, groupId, holdout);
    ResponseEntity<Map> conversation = TestAuth.post(rest, holdout.accessToken(),
        "/api/v1/ai/conversations", Map.of("groupId", groupId), Map.class);
    String conversationId = (String) conversation.getBody().get("id");
    ResponseEntity<Map> chat = TestAuth.post(rest, holdout.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "summarize my contributions"), Map.class);
    assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void contributionSummaryReturnsRecordedFacts() {
    recordAndConfirm(member, admin, "750.00");
    String conversationId = createConversation(member, groupId);

    ResponseEntity<Map> chat = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "summarize my contributions"), Map.class);
    assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> executed =
        (List<Map<String, Object>>) chat.getBody().get("executedTools");
    assertThat(executed).hasSize(1);
    assertThat(executed.get(0)).containsEntry("tool", "contribution_summary");
    @SuppressWarnings("unchecked")
    Map<String, Object> facts = (Map<String, Object>) executed.get(0).get("facts");
    assertThat(new BigDecimal(facts.get("myConfirmedTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("750.00"));
    assertThat(facts).containsEntry("currency", "KES");
  }

  @Test
  void ledgerAndMeetingReadsWork() {
    String conversationId = createConversation(member, groupId);
    ResponseEntity<Map> chat = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "explain the ledger"), Map.class);
    assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(chat.getBody().get("assistantMessage").toString())
        .contains("Recorded facts");

    ResponseEntity<Map> meetings = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "show upcoming meetings"), Map.class);
    assertThat(meetings.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void proposalConfirmFlowCreatesMeeting() {
    String conversationId = createConversation(admin, groupId);
    String startsAt = OffsetDateTime.now().plusDays(3).toString();
    ResponseEntity<Map> chat = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "tool:meeting_create {\"title\": \"AI planned meeting\", "
            + "\"startsAt\": \"" + startsAt + "\", \"timezone\": \"Africa/Nairobi\"}"),
        Map.class);
    assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> proposals =
        (List<Map<String, Object>>) chat.getBody().get("proposals");
    assertThat(proposals).hasSize(1);
    assertThat(proposals.get(0))
        .containsEntry("tool", "meeting_create")
        .containsEntry("status", "PENDING");
    String proposalId = (String) proposals.get(0).get("id");

    // Nothing changed yet.
    assertThat(meetingCount(admin)).isZero();

    ResponseEntity<Map> confirmed = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/ai/proposals/" + proposalId + "/confirm", Map.of(), Map.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody()).containsEntry("status", "CONFIRMED");
    assertThat(meetingCount(admin)).isEqualTo(1);

    // The AI-assisted action is audited like any UI action.
    ResponseEntity<Map> audit = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/audit?size=50", Map.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> events =
        (List<Map<String, Object>>) audit.getBody().get("content");
    assertThat(events).anySatisfy(e ->
        assertThat(e).containsEntry("action", "ai.action_confirmed"));

    // Double confirmation is rejected.
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/ai/proposals/" + proposalId + "/confirm", Map.of(), Map.class)
        .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void proposalRejectFlowChangesNothing() {
    String conversationId = createConversation(member, groupId);
    ResponseEntity<Map> chat = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "tool:statement_request {\"requestType\": \"PERSONAL_STATEMENT\", "
            + "\"periodStart\": \"2026-10-01\", \"periodEnd\": \"2026-10-31\"}"),
        Map.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> proposals =
        (List<Map<String, Object>>) chat.getBody().get("proposals");
    String proposalId = (String) proposals.get(0).get("id");

    ResponseEntity<Map> rejected = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/proposals/" + proposalId + "/reject", Map.of(), Map.class);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(rejected.getBody()).containsEntry("status", "REJECTED");

    ResponseEntity<Map> requests = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests", Map.class);
    assertThat((int) requests.getBody().get("totalElements")).isZero();
  }

  @Test
  void confirmationEnforcesBackendAuthorization() {
    // A plain member can draft the proposal but cannot execute a manager-only action.
    String conversationId = createConversation(member, groupId);
    String startsAt = OffsetDateTime.now().plusDays(3).toString();
    ResponseEntity<Map> chat = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "tool:meeting_create {\"title\": \"Member attempt\", "
            + "\"startsAt\": \"" + startsAt + "\", \"timezone\": \"Africa/Nairobi\"}"),
        Map.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> proposals =
        (List<Map<String, Object>>) chat.getBody().get("proposals");
    String proposalId = (String) proposals.get(0).get("id");

    ResponseEntity<Map> confirm = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/proposals/" + proposalId + "/confirm", Map.of(), Map.class);
    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(meetingCount(admin)).isZero();

    ResponseEntity<List> failed = TestAuth.get(rest, member.accessToken(),
        "/api/v1/ai/proposals?status=FAILED", List.class);
    assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(failed.getBody().toString()).contains(proposalId);
  }

  @Test
  void invalidAndUnknownToolsAreHandledSafely() {
    String conversationId = createConversation(member, groupId);

    ResponseEntity<Map> invalid = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "tool:statement_request {\"requestType\": \"BOGUS\"}"),
        Map.class);
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((List<?>) invalid.getBody().get("proposals"))).isEmpty();
    assertThat(invalid.getBody().get("assistantMessage").toString())
        .contains("couldn't run");

    ResponseEntity<Map> unknown = TestAuth.post(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "tool:launch_rockets {}"),
        Map.class);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(unknown.getBody().get("assistantMessage").toString())
        .contains("unknown capability");
  }

  @Test
  void conversationsArePrivateAndGroupScoped() {
    String conversationId = createConversation(member, groupId);
    // Another user cannot read it.
    assertThat(TestAuth.get(rest, admin.accessToken(),
        "/api/v1/ai/conversations/" + conversationId, Map.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    // Cannot bind a conversation to a group you do not belong to.
    Session outsider = TestAuth.register(rest, "aioutsider");
    grantAiConsent(outsider);
    assertThat(TestAuth.post(rest, outsider.accessToken(), "/api/v1/ai/conversations",
        Map.of("groupId", groupId), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void retentionPurgeRemovesOldConversations() {
    String conversationId = createConversation(member, groupId);
    AiConversation conversation = conversations.findById(java.util.UUID.fromString(
        conversationId)).orElseThrow();
    conversation.setUpdatedAt(OffsetDateTime.now().minusDays(100));
    conversations.save(conversation);

    retentionService.purgeOlderThan(90);
    assertThat(TestAuth.get(rest, member.accessToken(),
        "/api/v1/ai/conversations/" + conversationId, Map.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  private int meetingCount(Session session) {
    ResponseEntity<Map> response = TestAuth.get(rest, session.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings", Map.class);
    return (int) response.getBody().get("totalElements");
  }

  private String createConversation(Session session, String group) {
    ResponseEntity<Map> response = TestAuth.post(rest, session.accessToken(),
        "/api/v1/ai/conversations", Map.of("groupId", group), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) response.getBody().get("id");
  }

  private void grantAiConsent(Session session) {
    ResponseEntity<Map> response = TestAuth.post(rest, session.accessToken(),
        "/api/v1/users/me/consents",
        Map.of("purpose", "AI_ASSISTANCE", "granted", true), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
