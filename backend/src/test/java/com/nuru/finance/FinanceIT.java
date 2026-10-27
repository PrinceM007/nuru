package com.nuru.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import com.nuru.support.TestAuth.Session;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class FinanceIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  private Session admin;
  private Session member;
  private Session outsider;
  private String groupId;

  @BeforeEach
  void setUp() {
    admin = TestAuth.register(rest, "finadmin");
    member = TestAuth.register(rest, "finmember");
    outsider = TestAuth.register(rest, "finoutsider");
    groupId = createGroup(admin, "Finance Group", "KES");
    joinAsMember(admin, groupId, member);
  }

  @Test
  void contributionLifecycleExpectedToConfirmed() {
    String scheduleId = createSchedule(admin, "Monthly dues", "1000.00");

    // Member reports a payment; treasurer confirms it.
    ResponseEntity<Map> recorded = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions",
        Map.of("scheduleId", scheduleId, "periodStart", "2026-10-01", "periodEnd", "2026-10-31",
            "amount", "1000.00", "assessment", false, "notes", "M-Pesa"),
        Map.class);
    assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(recorded.getBody())
        .containsEntry("status", "SUBMITTED")
        .containsEntry("recordedVia", "MANUAL");
    String contributionId = (String) recorded.getBody().get("id");

    // Members cannot confirm their own payments.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + contributionId + "/confirm", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<Map> confirmed = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + contributionId + "/confirm", Map.of(),
        Map.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody()).containsEntry("status", "CONFIRMED");

    // Double confirmation is rejected.
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + contributionId + "/confirm", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    Map<String, Object> summary = ledgerSummary(admin);
    assertThat(new BigDecimal(summary.get("confirmedContributionsTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(new BigDecimal(summary.get("expectedTotal").toString()))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(new BigDecimal(summary.get("netBalance").toString()))
        .isEqualByComparingTo(new BigDecimal("1000.00"));
  }

  @Test
  void assessmentPostsExpectationAndConfirmsDirectly() {
    ResponseEntity<Map> assessed = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions",
        Map.of("memberUserId", member.userId().toString(), "periodStart", "2026-11-01",
            "periodEnd", "2026-11-30", "amount", "500.00", "assessment", true),
        Map.class);
    assertThat(assessed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(assessed.getBody()).containsEntry("status", "EXPECTED");
    String contributionId = (String) assessed.getBody().get("id");

    // Plain members cannot post assessments.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions",
        Map.of("periodStart", "2026-11-01", "periodEnd", "2026-11-30", "amount", "1.00",
            "assessment", true),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    Map<String, Object> summary = ledgerSummary(member);
    assertThat(new BigDecimal(summary.get("expectedTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("500.00"));
    assertThat(new BigDecimal(summary.get("myExpectedTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("500.00"));

    ResponseEntity<Map> confirmed = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + contributionId + "/confirm", Map.of(),
        Map.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void duplicateSubmissionWithSameKeyReturnsOriginal() {
    String key = "idem-" + UUID.randomUUID();
    Map<String, Object> body = Map.of("periodStart", "2026-10-01", "periodEnd", "2026-10-31",
        "amount", "250.00", "assessment", false);
    ResponseEntity<Map> first = TestAuth.postWithKey(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions", body, key, Map.class);
    ResponseEntity<Map> replay = TestAuth.postWithKey(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions", body, key, Map.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replay.getBody().get("id")).isEqualTo(first.getBody().get("id"));
  }

  @Test
  void correctionsAreLinkedAdjustmentsAndOriginalIsUntouched() {
    String contributionId = recordAndConfirm(member, admin, "1000.00");

    // Confirmed rows cannot be edited in place.
    assertThat(TestAuth.patch(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + contributionId,
        Map.of("amount", "1.00"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    ResponseEntity<Map> adjusted = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + contributionId + "/adjust",
        Map.of("amount", "-100.00", "reason", "Duplicate M-Pesa line"), Map.class);
    assertThat(adjusted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(adjusted.getBody()).containsEntry("correctionOf", contributionId);

    ResponseEntity<Map> original = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + contributionId, Map.class);
    assertThat(new BigDecimal(original.getBody().get("amount").toString()))
        .isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat((List<?>) original.getBody().get("adjustments")).hasSize(1);

    Map<String, Object> summary = ledgerSummary(admin);
    assertThat(new BigDecimal(summary.get("confirmedContributionsTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("900.00"));
  }

  @Test
  void membersCannotSeeEachOthersContributions() {
    recordAndConfirm(member, admin, "300.00");
    Session other = TestAuth.register(rest, "finother");
    joinAsMember(admin, groupId, other);

    assertThat(TestAuth.get(rest, other.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions?memberUserId=" + member.userId(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<Map> own = TestAuth.get(rest, other.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions", Map.class);
    assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((int) own.getBody().get("totalElements")).isZero();

    assertThat(TestAuth.get(rest, outsider.accessToken(),
        "/api/v1/groups/" + groupId + "/ledger/summary", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void loanLifecycleApproveDisburseRepay() {
    ResponseEntity<Map> requested = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans",
        Map.of("principal", "12000.00", "interestRate", "0.10", "termMonths", 12,
            "purpose", "Shop stock"),
        Map.class);
    assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(new BigDecimal(requested.getBody().get("totalDue").toString()))
        .isEqualByComparingTo(new BigDecimal("13200.00"));
    String loanId = (String) requested.getBody().get("id");

    // Borrower cannot approve their own loan.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/approve", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/approve", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<Map> disbursed = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/disburse", Map.of(), Map.class);
    assertThat(disbursed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(disbursed.getBody()).containsEntry("status", "ACTIVE");

    // Schedule sums exactly to the total due.
    ResponseEntity<List> schedule = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/schedule", List.class);
    assertThat(schedule.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(schedule.getBody()).hasSize(12);
    BigDecimal scheduled = BigDecimal.ZERO;
    for (Object row : schedule.getBody()) {
      scheduled = scheduled.add(
          new BigDecimal(((Map<?, ?>) row).get("amountDue").toString()));
    }
    assertThat(scheduled).isEqualByComparingTo(new BigDecimal("13200.00"));

    // Overpayment is rejected; full repayment closes the loan.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/repayments",
        Map.of("amount", "99999.00"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    ResponseEntity<Map> repayment = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/repayments",
        Map.of("amount", "13200.00"), Map.class);
    assertThat(repayment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String repaymentId = (String) repayment.getBody().get("id");

    ResponseEntity<Map> loanBeforeConfirm = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId, Map.class);
    assertThat(loanBeforeConfirm.getBody()).containsEntry("status", "ACTIVE");

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/repayments/" + repaymentId
            + "/confirm",
        Map.of(), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> loanAfter = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId, Map.class);
    assertThat(loanAfter.getBody()).containsEntry("status", "REPAID");
    assertThat(new BigDecimal(loanAfter.getBody().get("outstandingBalance").toString()))
        .isEqualByComparingTo(BigDecimal.ZERO);

    Map<String, Object> summary = ledgerSummary(admin);
    assertThat(new BigDecimal(summary.get("loansDisbursedTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("12000.00"));
    assertThat(new BigDecimal(summary.get("loansRepaidTotal").toString()))
        .isEqualByComparingTo(new BigDecimal("13200.00"));
    assertThat(new BigDecimal(summary.get("loansOutstandingTotal").toString()))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void loanRejectCancelAndDefaultFlows() {
    ResponseEntity<Map> requested = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans",
        Map.of("principal", "1000.00", "termMonths", 6), Map.class);
    String loanId = (String) requested.getBody().get("id");

    // Rejection needs a reason.
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/reject", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId + "/reject",
        Map.of("reason", "Insufficient group funds"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // Cancelled loans cannot be approved.
    ResponseEntity<Map> requested2 = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans",
        Map.of("principal", "500.00", "termMonths", 3), Map.class);
    String loanId2 = (String) requested2.getBody().get("id");
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId2 + "/cancel", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId2 + "/approve", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // Default + reactivate on an active loan.
    ResponseEntity<Map> requested3 = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans",
        Map.of("principal", "800.00", "termMonths", 4), Map.class);
    String loanId3 = (String) requested3.getBody().get("id");
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId3 + "/approve", Map.of(), Map.class);
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId3 + "/disburse", Map.of(), Map.class);
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId3 + "/mark-defaulted",
        Map.of("reason", "Missed 2 installments"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId3 + "/reactivate", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void loanVisibilityIsBorrowerOrPrivileged() {
    ResponseEntity<Map> requested = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/loans",
        Map.of("principal", "700.00", "termMonths", 2), Map.class);
    String loanId = (String) requested.getBody().get("id");
    Session other = TestAuth.register(rest, "finpeer");
    joinAsMember(admin, groupId, other);

    assertThat(TestAuth.get(rest, other.accessToken(),
        "/api/v1/groups/" + groupId + "/loans/" + loanId, Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<Map> own = TestAuth.get(rest, other.accessToken(),
        "/api/v1/groups/" + groupId + "/loans", Map.class);
    assertThat((int) own.getBody().get("totalElements")).isZero();
  }

  private String recordAndConfirm(Session payer, Session confirmer, String amount) {
    ResponseEntity<Map> recorded = TestAuth.post(rest, payer.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions",
        Map.of("periodStart", "2026-10-01", "periodEnd", "2026-10-31", "amount", amount,
            "assessment", false),
        Map.class);
    assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = (String) recorded.getBody().get("id");
    ResponseEntity<Map> confirmed = TestAuth.post(rest, confirmer.accessToken(),
        "/api/v1/groups/" + groupId + "/contributions/" + id + "/confirm", Map.of(), Map.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    return id;
  }

  private Map<String, Object> ledgerSummary(Session session) {
    ResponseEntity<Map> response = TestAuth.get(rest, session.accessToken(),
        "/api/v1/groups/" + groupId + "/ledger/summary", Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    return body;
  }

  private String createSchedule(Session owner, String title, String amount) {
    ResponseEntity<Map> response = TestAuth.post(rest, owner.accessToken(),
        "/api/v1/groups/" + groupId + "/contribution-schedules",
        Map.of("title", title, "amount", amount, "frequency", "MONTHLY", "dueDay", 5,
            "startDate", "2026-10-01"),
        Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) response.getBody().get("id");
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
