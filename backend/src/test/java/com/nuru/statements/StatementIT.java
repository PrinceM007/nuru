package com.nuru.statements;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import com.nuru.support.TestAuth.Session;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class StatementIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  private Session admin;
  private Session member;
  private String groupId;

  @BeforeEach
  void setUp() {
    admin = TestAuth.register(rest, "stmtadmin");
    member = TestAuth.register(rest, "stmtmember");
    groupId = createGroup(admin, "Statement Group", "GHS");
    joinAsMember(admin, groupId, member);
  }

  @Test
  void personalStatementIsAutoApprovedAndPrivate() {
    recordAndConfirm(member, admin, "400.00");

    ResponseEntity<Map> requested = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests",
        Map.of("requestType", "PERSONAL_STATEMENT", "periodStart", "2026-10-01",
            "periodEnd", "2026-10-31"),
        Map.class);
    assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(requested.getBody()).containsEntry("status", "APPROVED");
    String requestId = (String) requested.getBody().get("id");

    ResponseEntity<Map> document = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/document",
        Map.class);
    assertThat(document.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(document.getBody())
        .containsEntry("currency", "GHS")
        .containsEntry("periodStart", "2026-10-01")
        .containsEntry("periodEnd", "2026-10-31");
    assertThat(document.getBody().get("generatedAt")).isNotNull();
    assertThat(new BigDecimal(document.getBody().get("confirmedTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("400.00"));
    assertThat((List<?>) document.getBody().get("contributions")).hasSize(1);

    // Other members cannot read it; admins can.
    Session other = TestAuth.register(rest, "stmtother");
    joinAsMember(admin, groupId, other);
    assertThat(TestAuth.get(rest, other.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/document",
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/document",
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void groupSummaryNeedsApprovalAndShowsTotalsOnly() {
    recordAndConfirm(member, admin, "250.00");

    ResponseEntity<Map> requested = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests",
        Map.of("requestType", "GROUP_SUMMARY", "periodStart", "2026-10-01",
            "periodEnd", "2026-10-31"),
        Map.class);
    assertThat(requested.getBody()).containsEntry("status", "PENDING");
    String requestId = (String) requested.getBody().get("id");

    assertThat(TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/document",
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/approve", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> document = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/document",
        Map.class);
    assertThat(document.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> totals = (Map<String, Object>) document.getBody().get("groupTotals");
    assertThat(totals).containsEntry("activeMembers", 2);
    assertThat(new BigDecimal(totals.get("confirmedContributionsTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("250.00"));
    // Totals only — no per-member rows leak into the group summary.
    assertThat(document.getBody().toString()).doesNotContain(member.userId().toString());
  }

  @Test
  void adminsConfigureApprovalPolicy() {
    ResponseEntity<List> policies = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/policies", List.class);
    assertThat(policies.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(policies.getBody()).hasSize(2);

    assertThat(TestAuth.exchangePut(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/policies",
        Map.of("requestType", "PERSONAL_STATEMENT", "approvalRequired", true),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> requested = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests",
        Map.of("requestType", "PERSONAL_STATEMENT", "periodStart", "2026-10-01",
            "periodEnd", "2026-10-31"),
        Map.class);
    assertThat(requested.getBody()).containsEntry("status", "PENDING");

    // Members cannot change policy; rejection needs a reason.
    assertThat(TestAuth.exchangePut(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/policies",
        Map.of("requestType", "PERSONAL_STATEMENT", "approvalRequired", false),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    String requestId = (String) requested.getBody().get("id");
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/reject", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    ResponseEntity<Map> rejected = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests/" + requestId + "/reject",
        Map.of("reason", "Period not closed yet"), Map.class);
    assertThat(rejected.getBody())
        .containsEntry("status", "REJECTED")
        .containsEntry("rejectionReason", "Period not closed yet");
  }

  @Test
  void invalidStatementInputIsRejected() {
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests",
        Map.of("requestType", "BANK_STATEMENT", "periodStart", "2026-10-01",
            "periodEnd", "2026-10-31"),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/statements/requests",
        Map.of("requestType", "PERSONAL_STATEMENT", "periodStart", "2026-11-01",
            "periodEnd", "2026-10-01"),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  private void recordAndConfirm(Session payer, Session confirmer, String amount) {
    ResponseEntity<Map> recorded = TestAuth.post(rest, payer.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions",
        Map.of("periodStart", "2026-10-01", "periodEnd", "2026-10-31", "amount", amount,
            "assessment", false),
        Map.class);
    assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = (String) recorded.getBody().get("id");
    assertThat(TestAuth.post(rest, confirmer.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + id + "/confirm", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
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
