package com.nuru.groups;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import com.nuru.support.TestAuth.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GroupMembershipIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Test
  void creatorBecomesAdminAndManagesGroup() {
    Session admin = TestAuth.register(rest, "creator");
    String groupId = createGroup(admin, " Harambee Savings ", "KES");

    ResponseEntity<Map> fetched = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId, Map.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody())
        .containsEntry("name", "Harambee Savings")
        .containsEntry("currentUserRole", "ADMIN");

    ResponseEntity<Map> updated = TestAuth.patch(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId, Map.of("description", "Weekly contributions"), Map.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<List> roles = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/roles", List.class);
    assertThat(roles.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roles.getBody()).hasSize(3);
  }

  @Test
  void nonMemberCannotAccessGroupById() {
    Session admin = TestAuth.register(rest, "owner");
    String groupId = createGroup(admin, "Closed Group", "NGN");
    Session outsider = TestAuth.register(rest, "outsider");

    assertThat(TestAuth.get(rest, outsider.accessToken(), "/api/v1/groups/" + groupId,
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(TestAuth.get(rest, outsider.accessToken(),
        "/api/v1/groups/" + groupId + "/members", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(TestAuth.patch(rest, outsider.accessToken(), "/api/v1/groups/" + groupId,
        Map.of("name", "Hijacked"), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    // Random ids behave identically — no existence oracle.
    assertThat(TestAuth.get(rest, outsider.accessToken(), "/api/v1/groups/" + UUID.randomUUID(),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void joinRequestApproveAndRejectFlow() {
    Session admin = TestAuth.register(rest, "approver");
    String groupId = createGroup(admin, "Join Flow", "GHS");
    Session applicant = TestAuth.register(rest, "applicant");

    ResponseEntity<Map> requested = TestAuth.post(rest, applicant.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests", Map.of("message", "Please add me"),
        Map.class);
    assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String requestId = (String) requested.getBody().get("id");

    // Duplicate pending request is rejected.
    assertThat(TestAuth.post(rest, applicant.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests", Map.of(), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    // Applicant cannot list requests; admin can.
    assertThat(TestAuth.get(rest, applicant.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests?status=PENDING",
        Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> approved = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests/" + requestId + "/approve", Map.of(),
        Map.class);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(approved.getBody()).containsEntry("status", "APPROVED");

    // New member can now read the group.
    ResponseEntity<Map> asMember = TestAuth.get(rest, applicant.accessToken(),
        "/api/v1/groups/" + groupId, Map.class);
    assertThat(asMember.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(asMember.getBody()).containsEntry("currentUserRole", "MEMBER");

    // Reject path with a second applicant.
    Session rejected = TestAuth.register(rest, "rejected");
    ResponseEntity<Map> req2 = TestAuth.post(rest, rejected.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests", Map.of(), Map.class);
    String requestId2 = (String) req2.getBody().get("id");
    ResponseEntity<Map> denied = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests/" + requestId2 + "/reject",
        Map.of("reason", "Group is full"), Map.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(denied.getBody())
        .containsEntry("status", "REJECTED")
        .containsEntry("rejectionReason", "Group is full");
  }

  @Test
  void invitationAcceptRevokeAndExpiry() {
    Session admin = TestAuth.register(rest, "inviter");
    String groupId = createGroup(admin, "Invite Group", "ZAR");
    Session guest = TestAuth.register(rest, "guest");

    ResponseEntity<Map> created = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/invitations",
        Map.of("expiresInHours", 24, "maxUses", 1), Map.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String code = (String) created.getBody().get("code");
    assertThat(code).isNotBlank();

    // Codes are never listed back.
    ResponseEntity<Map> listed = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/invitations", Map.class);
    assertThat(listed.getBody().toString()).doesNotContain(code);

    ResponseEntity<Map> accepted = TestAuth.post(rest, guest.accessToken(),
        "/api/v1/invitations/accept", Map.of("code", code), Map.class);
    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(accepted.getBody()).containsEntry("groupId", groupId);

    // Single-use code cannot be reused.
    Session late = TestAuth.register(rest, "late");
    assertThat(TestAuth.post(rest, late.accessToken(), "/api/v1/invitations/accept",
        Map.of("code", code), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // Revoked invitation cannot be used.
    ResponseEntity<Map> created2 = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/invitations", Map.of(), Map.class);
    String code2 = (String) created2.getBody().get("code");
    String invId2 = (String) created2.getBody().get("id");
    assertThat(TestAuth.delete(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/invitations/" + invId2, Void.class).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(TestAuth.post(rest, late.accessToken(), "/api/v1/invitations/accept",
        Map.of("code", code2), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // Bogus code → 404, invalid expiry → 422.
    assertThat(TestAuth.post(rest, late.accessToken(), "/api/v1/invitations/accept",
        Map.of("code", "bogus-code"), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/invitations", Map.of("expiresInHours", 0),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void lastAdminIsProtectedAndTransferWorks() {
    Session admin = TestAuth.register(rest, "soleadmin");
    String groupId = createGroup(admin, "Admin Guard", "KES");
    Session member = TestAuth.register(rest, "newcomer");
    joinAsMember(admin, groupId, member);

    // Sole admin cannot leave or be demoted.
    assertThat(TestAuth.delete(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/members/me", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    String memberRoleId = roleId(admin, groupId, "MEMBER");
    assertThat(TestAuth.patch(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/members/" + admin.userId() + "/role",
        Map.of("roleId", memberRoleId), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // Explicit transfer: member becomes admin, former admin becomes member.
    ResponseEntity<Void> transfer = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/admin-transfer",
        Map.of("newAdminUserId", member.userId().toString()), Void.class);
    assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<Map> asNewAdmin = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId, Map.class);
    assertThat(asNewAdmin.getBody()).containsEntry("currentUserRole", "ADMIN");
    ResponseEntity<Map> asOldAdmin = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId, Map.class);
    assertThat(asOldAdmin.getBody()).containsEntry("currentUserRole", "MEMBER");

    // New admin can now remove the old admin.
    assertThat(TestAuth.delete(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/members/" + admin.userId(), Void.class)
        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void customRolesUseControlledPermissions() {
    Session admin = TestAuth.register(rest, "roleadmin");
    String groupId = createGroup(admin, "Roles Group", "KES");

    ResponseEntity<Map> created = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/roles",
        Map.of("name", "auditor", "permissions", List.of("AUDIT_VIEW", "LEDGER_VIEW_FULL")),
        Map.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).containsEntry("name", "AUDITOR");

    // Unknown permission is rejected.
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/roles",
        Map.of("name", "hacker", "permissions", List.of("EVERYTHING")),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // System role names are reserved.
    assertThat(TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/roles",
        Map.of("name", "ADMIN", "permissions", List.of("AUDIT_VIEW")),
        Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // ADMIN grants are immutable.
    String adminRoleId = roleId(admin, groupId, "ADMIN");
    assertThat(TestAuth.patch(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/roles/" + adminRoleId,
        Map.of("permissions", List.of("AUDIT_VIEW")), Map.class).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void memberEmailsAreHiddenFromNonAdmins() {
    Session admin = TestAuth.register(rest, "emailadmin");
    String groupId = createGroup(admin, "Privacy Group", "KES");
    Session member = TestAuth.register(rest, "regular");
    joinAsMember(admin, groupId, member);

    ResponseEntity<Map> asMember = TestAuth.get(rest, member.accessToken(),
        "/api/v1/groups/" + groupId + "/members", Map.class);
    assertThat(asMember.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Member sees own email but not the admin's.
    assertThat(asMember.getBody().toString()).contains(member.email());
    assertThat(asMember.getBody().toString()).doesNotContain(admin.email());

    ResponseEntity<Map> asAdmin = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/members", Map.class);
    assertThat(asAdmin.getBody().toString()).contains(member.email());
  }

  private String createGroup(Session owner, String name, String currency) {
    ResponseEntity<Map> response = TestAuth.post(rest, owner.accessToken(), "/api/v1/groups",
        Map.of("name", name, "currency", currency), Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return (String) response.getBody().get("id");
  }

  private void joinAsMember(Session admin, String groupId, Session applicant) {
    ResponseEntity<Map> requested = TestAuth.post(rest, applicant.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests", Map.of(), Map.class);
    String requestId = (String) requested.getBody().get("id");
    ResponseEntity<Map> approved = TestAuth.post(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/join-requests/" + requestId + "/approve", Map.of(),
        Map.class);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @SuppressWarnings("unchecked")
  private String roleId(Session admin, String groupId, String name) {
    ResponseEntity<List> roles = TestAuth.get(rest, admin.accessToken(),
        "/api/v1/groups/" + groupId + "/roles", List.class);
    List<Map<String, Object>> body = (List<Map<String, Object>>) (List<?>) roles.getBody();
    return body.stream()
        .filter(r -> r.get("name").equals(name))
        .map(r -> (String) r.get("id"))
        .findFirst()
        .orElseThrow();
  }
}
