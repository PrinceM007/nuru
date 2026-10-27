package com.nuru.service;

import com.nuru.api.dto.GroupDto;
import com.nuru.api.error.ConflictException;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.JoinRequestStatus;
import com.nuru.domain.MembershipStatus;
import com.nuru.domain.Permission;
import com.nuru.groups.GroupRole;
import com.nuru.groups.GroupRoleRepository;
import com.nuru.groups.JoinRequest;
import com.nuru.groups.JoinRequestRepository;
import com.nuru.groups.Membership;
import com.nuru.groups.MembershipRepository;
import com.nuru.users.User;
import com.nuru.users.UserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

  private final MembershipRepository memberships;
  private final JoinRequestRepository joinRequests;
  private final GroupRoleRepository roles;
  private final UserRepository users;
  private final GroupAuth groupAuth;
  private final AuditService auditService;
  private final NotificationPublisher notifications;

  public MembershipService(MembershipRepository memberships, JoinRequestRepository joinRequests,
      GroupRoleRepository roles, UserRepository users, GroupAuth groupAuth,
      AuditService auditService, NotificationPublisher notifications) {
    this.memberships = memberships;
    this.joinRequests = joinRequests;
    this.roles = roles;
    this.users = users;
    this.groupAuth = groupAuth;
    this.auditService = auditService;
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public Page<GroupDto.Member> listMembers(UUID groupId, UUID callerId, Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    boolean callerIsAdmin = groupAuth.isAdmin(groupId, callerId);
    Page<Membership> page = memberships.findByGroupId(groupId, pageable);
    Map<UUID, User> userById = users.findAllById(
        page.getContent().stream().map(Membership::getUserId).toList()).stream()
        .collect(Collectors.toMap(User::getId, u -> u));
    Map<UUID, String> roleNames = roles.findByGroupId(groupId).stream()
        .collect(Collectors.toMap(GroupRole::getId, GroupRole::getName));
    return page.map(m -> {
      User user = userById.get(m.getUserId());
      String email = (callerIsAdmin || m.getUserId().equals(callerId)) && user != null
          ? user.getEmail() : null;
      return new GroupDto.Member(
          m.getUserId(),
          user == null ? "Unknown member" : user.getDisplayName(),
          email,
          roleNames.getOrDefault(m.getRoleId(), "UNKNOWN"),
          m.getStatus().name(),
          m.getJoinedAt());
    });
  }

  @Transactional
  public GroupDto.JoinRequest requestJoin(UUID groupId, UUID userId, String message) {
    groupAuth.groupOrNotFound(groupId);
    Membership existing = memberships.findByGroupIdAndUserId(groupId, userId).orElse(null);
    if (existing != null && existing.getStatus() == MembershipStatus.ACTIVE) {
      throw new ConflictException("Already an active member of this group");
    }
    if (existing != null && existing.getStatus() == MembershipStatus.SUSPENDED) {
      throw new ForbiddenOperationException("Membership is suspended");
    }
    joinRequests.findByGroupIdAndUserIdAndStatus(groupId, userId, JoinRequestStatus.PENDING)
        .ifPresent(r -> {
          throw new ConflictException("A pending join request already exists");
        });
    JoinRequest request = joinRequests.save(new JoinRequest(groupId, userId,
        message == null ? null : message.trim()));
    auditService.record(userId, groupId, AuditActions.MEMBER_JOIN_REQUESTED, "JoinRequest",
        request.getId().toString(), "SUCCESS", null);
    roles.findByGroupIdAndName(groupId, GroupRole.ADMIN).ifPresent(adminRole ->
        notifications.joinRequestAlert(groupId,
            memberships.findByGroupIdAndRoleIdAndStatus(groupId, adminRole.getId(),
                MembershipStatus.ACTIVE).stream().map(Membership::getUserId).toList(),
            displayName(userId)));
    return GroupDto.JoinRequest.from(request, displayName(userId));
  }

  @Transactional
  public void cancelJoinRequest(UUID groupId, UUID requestId, UUID userId) {
    JoinRequest request = joinRequestInGroup(groupId, requestId);
    if (!request.getUserId().equals(userId)) {
      throw new ForbiddenOperationException("Only the requester can cancel");
    }
    if (request.getStatus() != JoinRequestStatus.PENDING) {
      throw new UnprocessableException("Only pending requests can be cancelled");
    }
    request.setStatus(JoinRequestStatus.CANCELLED);
  }

  @Transactional(readOnly = true)
  public Page<GroupDto.JoinRequest> listJoinRequests(UUID groupId, UUID callerId,
      String status, Pageable pageable) {
    groupAuth.requirePermission(groupId, callerId, Permission.MEMBERS_MANAGE);
    Page<JoinRequest> page = status == null
        ? joinRequests.findByGroupId(groupId, pageable)
        : joinRequests.findByGroupIdAndStatus(groupId, parseStatus(status), pageable);
    Map<UUID, String> names = displayNames(page.getContent().stream()
        .map(JoinRequest::getUserId).toList());
    return page.map(r -> GroupDto.JoinRequest.from(r, names.get(r.getUserId())));
  }

  @Transactional
  public GroupDto.JoinRequest approveJoinRequest(UUID groupId, UUID requestId, UUID approverId,
      UUID roleId) {
    groupAuth.requirePermission(groupId, approverId, Permission.MEMBERS_MANAGE);
    JoinRequest request = joinRequestInGroup(groupId, requestId);
    if (request.getStatus() != JoinRequestStatus.PENDING) {
      throw new UnprocessableException("Only pending requests can be approved");
    }
    GroupRole role = roleId == null ? memberRole(groupId) : groupAuth.roleOrNotFound(groupId, roleId);
    Membership membership = memberships
        .findByGroupIdAndUserId(groupId, request.getUserId())
        .orElseGet(() -> memberships.save(new Membership(groupId, request.getUserId(),
            role.getId(), MembershipStatus.ACTIVE)));
    membership.setRoleId(role.getId());
    membership.setStatus(MembershipStatus.ACTIVE);
    request.setStatus(JoinRequestStatus.APPROVED);
    request.setReviewedBy(approverId);
    request.setReviewedAt(OffsetDateTime.now());
    auditService.record(approverId, groupId, AuditActions.MEMBER_APPROVED, "JoinRequest",
        request.getId().toString(), "SUCCESS", "role=" + role.getName());
    notifications.membershipDecided(groupId, request.getUserId(), true,
        "You joined as " + role.getName() + ".");
    return GroupDto.JoinRequest.from(request, displayName(request.getUserId()));
  }

  @Transactional
  public GroupDto.JoinRequest rejectJoinRequest(UUID groupId, UUID requestId, UUID approverId,
      String reason) {
    groupAuth.requirePermission(groupId, approverId, Permission.MEMBERS_MANAGE);
    JoinRequest request = joinRequestInGroup(groupId, requestId);
    if (request.getStatus() != JoinRequestStatus.PENDING) {
      throw new UnprocessableException("Only pending requests can be rejected");
    }
    request.setStatus(JoinRequestStatus.REJECTED);
    request.setReviewedBy(approverId);
    request.setReviewedAt(OffsetDateTime.now());
    request.setRejectionReason(reason == null ? null : reason.trim());
    auditService.record(approverId, groupId, AuditActions.MEMBER_REJECTED, "JoinRequest",
        request.getId().toString(), "SUCCESS", null);
    notifications.membershipDecided(groupId, request.getUserId(), false,
        request.getRejectionReason() == null ? "" : request.getRejectionReason());
    return GroupDto.JoinRequest.from(request, displayName(request.getUserId()));
  }

  @Transactional
  public GroupDto.Member changeRole(UUID groupId, UUID targetUserId, UUID callerId,
      UUID newRoleId) {
    groupAuth.requirePermission(groupId, callerId, Permission.ROLES_MANAGE);
    Membership target = activeMembership(groupId, targetUserId);
    GroupRole newRole = groupAuth.roleOrNotFound(groupId, newRoleId);
    String currentRole = groupAuth.roleName(target);
    if (currentRole.equals(GroupRole.ADMIN) && !newRole.getName().equals(GroupRole.ADMIN)) {
      guardLastAdmin(groupId);
    }
    target.setRoleId(newRole.getId());
    auditService.record(callerId, groupId, AuditActions.ROLE_CHANGED, "Membership",
        target.getId().toString(), "SUCCESS",
        "user=" + targetUserId + ",from=" + currentRole + ",to=" + newRole.getName());
    return memberView(groupId, callerId, target);
  }

  @Transactional
  public void removeMember(UUID groupId, UUID targetUserId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.MEMBERS_MANAGE);
    if (targetUserId.equals(callerId)) {
      throw new UnprocessableException("Use leave-group to remove yourself");
    }
    Membership target = activeMembership(groupId, targetUserId);
    if (groupAuth.roleName(target).equals(GroupRole.ADMIN)) {
      guardLastAdmin(groupId);
    }
    target.setStatus(MembershipStatus.LEFT);
    auditService.record(callerId, groupId, AuditActions.MEMBER_REMOVED, "Membership",
        target.getId().toString(), "SUCCESS", "user=" + targetUserId);
  }

  @Transactional
  public void leaveGroup(UUID groupId, UUID userId) {
    Membership membership = groupAuth.requireMembership(groupId, userId);
    if (groupAuth.roleName(membership).equals(GroupRole.ADMIN)) {
      guardLastAdmin(groupId);
    }
    membership.setStatus(MembershipStatus.LEFT);
    auditService.record(userId, groupId, AuditActions.MEMBER_REMOVED, "Membership",
        membership.getId().toString(), "SUCCESS", "self-leave");
  }

  /**
   * Explicit safe admin transfer: the caller must be an admin; the target becomes admin
   * and the caller steps down to MEMBER in one transaction, so the group always keeps
   * at least one admin.
   */
  @Transactional
  public void transferAdmin(UUID groupId, UUID callerId, UUID newAdminUserId) {
    Membership caller = groupAuth.requireMembership(groupId, callerId);
    if (!groupAuth.roleName(caller).equals(GroupRole.ADMIN)) {
      throw new ForbiddenOperationException("Only an admin can transfer administration");
    }
    if (newAdminUserId.equals(callerId)) {
      throw new UnprocessableException("Transfer target must be another member");
    }
    Membership target = activeMembership(groupId, newAdminUserId);
    GroupRole adminRole = roles.findByGroupIdAndName(groupId, GroupRole.ADMIN)
        .orElseThrow(() -> new IllegalStateException("Group is missing its ADMIN role"));
    GroupRole memberRole = memberRole(groupId);
    target.setRoleId(adminRole.getId());
    caller.setRoleId(memberRole.getId());
    auditService.record(callerId, groupId, AuditActions.ADMIN_TRANSFERRED, "Membership",
        target.getId().toString(), "SUCCESS", "newAdmin=" + newAdminUserId);
  }

  private void guardLastAdmin(UUID groupId) {
    if (memberships.countActiveAdmins(groupId) <= 1) {
      throw new UnprocessableException(
          "Cannot remove or demote the last admin; use admin transfer instead");
    }
  }

  private Membership activeMembership(UUID groupId, UUID userId) {
    return memberships.findByGroupIdAndUserId(groupId, userId)
        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
        .orElseThrow(() -> new ResourceNotFoundException("Active membership not found"));
  }

  private JoinRequest joinRequestInGroup(UUID groupId, UUID requestId) {
    JoinRequest request = joinRequests.findById(requestId)
        .orElseThrow(() -> new ResourceNotFoundException("Join request not found"));
    if (!request.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Join request not found in this group");
    }
    return request;
  }

  private GroupRole memberRole(UUID groupId) {
    return roles.findByGroupIdAndName(groupId, GroupRole.MEMBER)
        .orElseThrow(() -> new IllegalStateException("Group is missing its MEMBER role"));
  }

  private JoinRequestStatus parseStatus(String status) {
    try {
      return JoinRequestStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown join request status: " + status);
    }
  }

  private String displayName(UUID userId) {
    return users.findById(userId).map(User::getDisplayName).orElse("Unknown member");
  }

  private Map<UUID, String> displayNames(java.util.List<UUID> userIds) {
    return users.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, User::getDisplayName));
  }

  private GroupDto.Member memberView(UUID groupId, UUID callerId, Membership membership) {
    User user = users.findById(membership.getUserId()).orElse(null);
    String email = (groupAuth.isAdmin(groupId, callerId)
        || membership.getUserId().equals(callerId)) && user != null ? user.getEmail() : null;
    return new GroupDto.Member(
        membership.getUserId(),
        user == null ? "Unknown member" : user.getDisplayName(),
        email,
        groupAuth.roleName(membership),
        membership.getStatus().name(),
        membership.getJoinedAt());
  }
}
