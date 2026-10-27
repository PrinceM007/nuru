package com.nuru.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import com.nuru.support.TestAuth.Session;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "nuru.identity.provider=SANDBOX",
    "nuru.credit.provider=GROUP_HISTORY"
})
class IdentityCreditIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  private Session admin;
  private Session member;
  private String groupId;

  @BeforeEach
  void setUp() {
    admin = TestAuth.register(rest, "idadmin");
    member = TestAuth.register(rest, "idmember");
    groupId = createGroup(admin, "Identity Group", "KES");
    joinAsMember(admin, groupId, member);
  }

  @Test
  void verificationFlowRequiresConsentAndConfirmsViaProvider() {
    ResponseEntity<Map> initial = TestAuth.get(rest, member.accessToken(),
        "/api/v1/verification/me", Map.class);
    assertThat(initial.getBody())
        .containsEntry("status", "NOT_STARTED")
        .containsEntry("provider", "SANDBOX");

    assertThat(TestAuth.post(rest, member.accessToken(), "/api/v1/verification/me/start",
        Map.of(), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    TestAuth.post(rest, member.accessToken(), "/api/v1/users/me/consents",
        Map.of("purpose", "IDENTITY_VERIFICATION", "granted", true), Map.class);

    ResponseEntity<Map> started = TestAuth.post(rest, member.accessToken(),
        "/api/v1/verification/me/start", Map.of(), Map.class);
    assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(started.getBody()).containsEntry("status", "PENDING");
    assertThat(started.getBody().get("providerReference").toString()).startsWith("SANDBOX-");

    ResponseEntity<Map> refreshed = TestAuth.post(rest, member.accessToken(),
        "/api/v1/verification/me/refresh", Map.of(), Map.class);
    assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(refreshed.getBody()).containsEntry("status", "VERIFIED");
    assertThat(refreshed.getBody().get("verifiedAt")).isNotNull();
    assertThat(refreshed.getBody().get("expiresAt")).isNotNull();
  }

  @Test
  void creditEstimateExplainsFactorsAndHandlesNoHistory() {
    ResponseEntity<Map> empty = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/credit/estimate", Map.class);
    assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(empty.getBody()).containsEntry("insufficientHistory", true);
    assertThat(empty.getBody().get("score")).isNull();

    recordAndConfirm(member, admin, "500.00");

    ResponseEntity<Map> estimate = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/credit/estimate", Map.class);
    assertThat(estimate.getBody()).containsEntry("insufficientHistory", false);
    assertThat((int) estimate.getBody().get("score")).isBetween(0, 100);
    assertThat((List<?>) estimate.getBody().get("factors")).isNotEmpty();
    assertThat(estimate.getBody().get("label").toString()).contains("ESTIMATE");
    assertThat(estimate.getBody().get("dataPeriodStart")).isNotNull();
    assertThat(estimate.getBody().get("estimatedAt")).isNotNull();
  }

  @Test
  void creditEstimatesArePrivate() {
    Session other = TestAuth.register(rest, "idother");
    joinAsMember(admin, groupId, other);
    assertThat(TestAuth.get(rest, other.accessToken(),
        "/api/v1/groups/" + groupId + "/credit/estimate?memberUserId=" + member.userId(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/credit/estimate?memberUserId=" + member.userId(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
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
