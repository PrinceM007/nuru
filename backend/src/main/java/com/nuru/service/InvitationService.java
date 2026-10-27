package com.nuru.service;

import com.nuru.api.dto.GroupDto;
import com.nuru.api.error.ConflictException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.auth.TokenHash;
import com.nuru.domain.MembershipStatus;
import com.nuru.domain.Permission;
import com.nuru.groups.GroupRole;
import com.nuru.groups.GroupRoleRepository;
import com.nuru.groups.Invitation;
import com.nuru.groups.InvitationRepository;
import com.nuru.groups.Membership;
import com.nuru.groups.MembershipRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

  private static final long DEFAULT_EXPIRY_HOURS = 72;
  private static final long MAX_EXPIRY_HOURS = 720;
  private static final int MAX_USES_LIMIT = 100;

  private final InvitationRepository invitations;
  private final MembershipRepository memberships;
  private final GroupRoleRepository roles;
  private final GroupAuth groupAuth;
  private final AuditService auditService;

  public InvitationService(InvitationRepository invitations, MembershipRepository memberships,
      GroupRoleRepository roles, GroupAuth groupAuth, AuditService auditService) {
    this.invitations = invitations;
    this.memberships = memberships;
    this.roles = roles;
    this.groupAuth = groupAuth;
    this.auditService = auditService;
  }

  @Transactional
  public GroupDto.InvitationCreated createInvitation(UUID groupId, UUID callerId, UUID roleId,
      Long expiresInHours, Integer maxUses) {
    groupAuth.requirePermission(groupId, callerId, Permission.INVITATIONS_MANAGE);
    groupAuth.groupOrNotFound(groupId);
    GroupRole role = roleId == null
        ? roles.findByGroupIdAndName(groupId, GroupRole.MEMBER)
            .orElseThrow(() -> new IllegalStateException("Group is missing its MEMBER role"))
        : groupAuth.roleOrNotFound(groupId, roleId);
    long hours = expiresInHours == null ? DEFAULT_EXPIRY_HOURS : expiresInHours;
    if (hours < 1 || hours > MAX_EXPIRY_HOURS) {
      throw new UnprocessableException("Expiry must be between 1 and 720 hours");
    }
    int uses = maxUses == null ? 1 : maxUses;
    if (uses < 1 || uses > MAX_USES_LIMIT) {
      throw new UnprocessableException("Max uses must be between 1 and 100");
    }
    String code = TokenHash.mintOpaqueToken();
    Invitation invitation = invitations.save(new Invitation(groupId,
        TokenHash.sha256Hex(code), role.getId(), callerId,
        OffsetDateTime.now().plusHours(hours), uses));
    auditService.record(callerId, groupId, AuditActions.INVITATION_CREATED, "Invitation",
        invitation.getId().toString(), "SUCCESS", "role=" + role.getName());
    return GroupDto.InvitationCreated.from(invitation, code, role.getName());
  }

  @Transactional(readOnly = true)
  public Page<GroupDto.Invitation> listInvitations(UUID groupId, UUID callerId,
      Pageable pageable) {
    groupAuth.requirePermission(groupId, callerId, Permission.INVITATIONS_MANAGE);
    return invitations.findByGroupId(groupId, pageable)
        .map(inv -> GroupDto.Invitation.from(inv, roleName(inv.getRoleId())));
  }

  @Transactional
  public void revokeInvitation(UUID groupId, UUID invitationId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.INVITATIONS_MANAGE);
    Invitation invitation = invitations.findById(invitationId)
        .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
    if (!invitation.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Invitation not found in this group");
    }
    invitation.setRevokedAt(OffsetDateTime.now());
    auditService.record(callerId, groupId, AuditActions.INVITATION_REVOKED, "Invitation",
        invitation.getId().toString(), "SUCCESS", null);
  }

  @Transactional
  public UUID acceptInvitation(String code, UUID userId) {
    Invitation invitation = invitations.findByCodeHash(TokenHash.sha256Hex(code.trim()))
        .orElseThrow(() -> new ResourceNotFoundException("Invitation not found or expired"));
    if (!invitation.isUsable()) {
      throw new UnprocessableException("Invitation is expired, revoked, or fully used");
    }
    UUID groupId = invitation.getGroupId();
    groupAuth.groupOrNotFound(groupId);
    Membership existing = memberships.findByGroupIdAndUserId(groupId, userId).orElse(null);
    if (existing != null && existing.getStatus() == MembershipStatus.ACTIVE) {
      throw new ConflictException("Already an active member of this group");
    }
    if (existing == null) {
      memberships.save(new Membership(groupId, userId, invitation.getRoleId(),
          MembershipStatus.ACTIVE));
    } else {
      existing.setRoleId(invitation.getRoleId());
      existing.setStatus(MembershipStatus.ACTIVE);
    }
    invitation.setUseCount(invitation.getUseCount() + 1);
    auditService.record(userId, groupId, AuditActions.INVITATION_ACCEPTED, "Invitation",
        invitation.getId().toString(), "SUCCESS", null);
    return groupId;
  }

  private String roleName(UUID roleId) {
    return roles.findById(roleId).map(GroupRole::getName).orElse("UNKNOWN");
  }
}
