package com.nuru.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Autowired
  private JdbcTemplate jdbc;

  private Session admin;
  private Session member;
  private String groupId;

  @BeforeEach
  void setUp() {
    admin = TestAuth.register(rest, "auditadmin");
    member = TestAuth.register(rest, "auditmember");
    groupId = createGroup(admin, "Audit Group", "KES");
    joinAsMember(admin, groupId, member);
  }

  @Test
  void auditTrailCapturesGroupActionsForAdminsOnly() {
    recordAndConfirm(member, admin, "100.00");

    ResponseEntity<Map> response = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/audit?size=50", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> events =
        (List<Map<String, Object>>) response.getBody().get("content");
    assertThat(events).anySatisfy(e -> assertThat(e).containsEntry("action", "group.created"));
    assertThat(events).anySatisfy(e ->
        assertThat(e).containsEntry("action", "membership.approved"));
    assertThat(events).anySatisfy(e ->
        assertThat(e).containsEntry("action", "contribution.confirmed"));
    assertThat(events.get(0).get("correlationId")).isNotNull();

    assertThat(TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/audit", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void auditRowsCannotBeUpdatedOrDeleted() {
    Long id = jdbc.queryForObject(
        "SELECT MAX(id) FROM audit_events WHERE group_id = CAST(? AS UNIQUEIDENTIFIER)",
        Long.class, groupId);
    assertThat(id).isNotNull();
    String before = jdbc.queryForObject("SELECT outcome FROM audit_events WHERE id = ?",
        String.class, id);

    assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET outcome = 'X' WHERE id = ?",
        id)).isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class);

    String after = jdbc.queryForObject("SELECT outcome FROM audit_events WHERE id = ?",
        String.class, id);
    assertThat(after).isEqualTo(before);
  }

  @Test
  void draftVoteDeletionStillWorksWithImmutabilityTriggers() {
    ResponseEntity<Map> vote = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes",
        Map.of("question", "Delete me?", "ballotPolicy", "SECRET",
            "opensAt", OffsetDateTime.now().plusHours(1).toString(),
            "closesAt", OffsetDateTime.now().plusHours(2).toString(),
            "options", List.of("Yes", "No")),
        Map.class);
    String voteId = (String) vote.getBody().get("id");
    assertThat(TestAuth.delete(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId, Void.class).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
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
