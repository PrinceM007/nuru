package com.nuru.meetings;

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

class MeetingVoteIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  private Session admin;
  private Session member;
  private Session outsider;
  private String groupId;

  @BeforeEach
  void setUp() {
    admin = TestAuth.register(rest, "meetadmin");
    member = TestAuth.register(rest, "meetmember");
    outsider = TestAuth.register(rest, "meetoutsider");
    groupId = createGroup(admin, "Meeting Group", "KES");
    joinAsMember(admin, groupId, member);
  }

  @Test
  void meetingLifecycleAttendanceAndNotes() {
    ResponseEntity<Map> created = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings",
        Map.of("title", "October meeting", "agenda", "Dues review",
            "startsAt", OffsetDateTime.now().plusDays(1).toString(),
            "endsAt", OffsetDateTime.now().plusDays(1).plusHours(2).toString(),
            "timezone", "Africa/Nairobi", "location", "Community hall"),
        Map.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String meetingId = (String) created.getBody().get("id");
    // Default invite list snapshots all active members.
    assertThat((List<?>) created.getBody().get("participants")).hasSize(2);

    // Members cannot create meetings.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings",
        Map.of("title", "Rogue", "startsAt", OffsetDateTime.now().plusDays(2).toString(),
            "timezone", "Africa/Nairobi"),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // Member marks own attendance; cannot mark the admin's.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId + "/attendance/"
            + member.userId(),
        Map.of("attendance", "PRESENT"), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId + "/attendance/"
            + admin.userId(),
        Map.of("attendance", "ABSENT"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    // Notes: member adds, author edits, others cannot.
    ResponseEntity<Map> note = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId + "/notes",
        Map.of("body", "Agreed to raise dues."), Map.class);
    assertThat(note.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String noteId = (String) note.getBody().get("id");
    Session other = TestAuth.register(rest, "meetother");
    joinAsMember(admin, groupId, other);
    assertThat(TestAuth.patch(rest, other.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId + "/notes/" + noteId,
        Map.of("body", "Edited by outsider"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(TestAuth.patch(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId + "/notes/" + noteId,
        Map.of("body", "Agreed to raise dues to 1000."), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // Complete freezes scheduling edits but keeps history.
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId + "/complete", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(TestAuth.patch(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId,
        Map.of("title", "Changed"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.get(rest, outsider.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings/" + meetingId, Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void meetingValidationRejectsBadInput() {
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings",
        Map.of("title", "Bad tz", "startsAt", OffsetDateTime.now().plusDays(1).toString(),
            "timezone", "Mars/Olympus"),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings",
        Map.of("title", "Bad times", "startsAt", OffsetDateTime.now().plusDays(2).toString(),
            "endsAt", OffsetDateTime.now().plusDays(1).toString(), "timezone", "Africa/Nairobi"),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/meetings",
        Map.of("title", "Bad invitee", "startsAt", OffsetDateTime.now().plusDays(1).toString(),
            "timezone", "Africa/Nairobi", "invitedUserIds",
            List.of(outsider.userId().toString())),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void secretVoteFlowKeepsBallotsSecret() {
    String voteId = createVote(admin, "SECRET", 2);

    // Draft votes accept no ballots.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", firstOption(voteId)), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/open", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> ballot = TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", firstOption(voteId)), Map.class);
    assertThat(ballot.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(ballot.getBody()).containsEntry("voted", true);
    // Secret: even the voter's own choice is never returned.
    assertThat(ballot.getBody().get("optionId")).isNull();

    // Double voting is rejected.
    assertThat(TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", firstOption(voteId)), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", secondOption(voteId)), Map.class);

    // Results stay hidden until publication.
    assertThat(TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/results", Map.class)
        .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/close", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

    // Closed votes accept no ballots.
    Session late = TestAuth.register(rest, "latevoter");
    joinAsMember(admin, groupId, late);
    assertThat(TestAuth.post(rest, late.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", firstOption(voteId)), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/publish", Map.of(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> results = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/results", Map.class);
    assertThat(results.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(results.getBody())
        .containsEntry("totalBallots", 2)
        .containsEntry("eligibleCount", 2)
        .containsEntry("quorum", 2)
        .containsEntry("quorumMet", true);
    assertThat(results.getBody().get("attributableChoices")).isNull();
    // No voter identity anywhere in the results payload.
    assertThat(results.getBody().toString())
        .doesNotContain(member.userId().toString())
        .doesNotContain(admin.userId().toString());

    // Reproducible: second read matches.
    ResponseEntity<Map> again = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/results", Map.class);
    assertThat(again.getBody().get("tallies")).isEqualTo(results.getBody().get("tallies"));
  }

  @Test
  void attributableVoteShowsVoterChoicesAfterPublish() {
    String voteId = createVote(admin, "ATTRIBUTABLE", null);
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/open", Map.of(), Map.class);
    TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", firstOption(voteId)), Map.class);

    ResponseEntity<Map> mine = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/my-ballot", Map.class);
    assertThat(mine.getBody().get("optionId")).isEqualTo(firstOption(voteId));

    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/close", Map.of(), Map.class);
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/publish", Map.of(), Map.class);

    ResponseEntity<Map> results = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/results", Map.class);
    assertThat(results.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(results.getBody().toString()).contains(member.userId().toString());
  }

  @Test
  void eligibilityFreezesAtOpening() {
    String voteId = createVote(admin, "SECRET", null);
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/open", Map.of(), Map.class);

    Session newcomer = TestAuth.register(rest, "newcomer");
    joinAsMember(admin, groupId, newcomer);
    assertThat(TestAuth.post(rest, newcomer.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", firstOption(voteId)), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void quorumNotMetIsReported() {
    String voteId = createVote(admin, "SECRET", 5);
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/open", Map.of(), Map.class);
    TestAuth.post(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/ballots",
        Map.of("optionId", firstOption(voteId)), Map.class);
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/close", Map.of(), Map.class);
    TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/publish", Map.of(), Map.class);

    ResponseEntity<Map> results = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId + "/results", Map.class);
    assertThat(results.getBody()).containsEntry("quorumMet", false);
  }

  @SuppressWarnings("unchecked")
  private String firstOption(String voteId) {
    ResponseEntity<Map> vote = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId, Map.class);
    List<Map<String, Object>> options = (List<Map<String, Object>>) vote.getBody().get("options");
    return (String) options.get(0).get("id");
  }

  @SuppressWarnings("unchecked")
  private String secondOption(String voteId) {
    ResponseEntity<Map> vote = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/votes/" + voteId, Map.class);
    List<Map<String, Object>> options = (List<Map<String, Object>>) vote.getBody().get("options");
    return (String) options.get(1).get("id");
  }

  private String createVote(Session creator, String policy, Integer quorum) {
    java.util.Map<String, Object> body = new java.util.HashMap<>(Map.of(
        "question", "Approve the new dues?",
        "ballotPolicy", policy,
        "opensAt", OffsetDateTime.now().minusHours(1).toString(),
        "closesAt", OffsetDateTime.now().plusHours(1).toString(),
        "options", List.of("Yes", "No")));
    if (quorum != null) {
      body.put("quorum", quorum);
    }
    ResponseEntity<Map> response = TestAuth.post(rest, creator.accessToken(),
        "/api/v1/groups/" + groupId + "/votes", body, Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).containsEntry("ballotPolicy", policy);
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
