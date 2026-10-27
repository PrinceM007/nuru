package com.nuru.service;

import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.domain.MembershipStatus;
import com.nuru.domain.Permission;
import com.nuru.groups.GroupRole;
import com.nuru.groups.GroupRoleRepository;
import com.nuru.groups.Membership;
import com.nuru.groups.MembershipRepository;
import com.nuru.groups.RolePermissionRepository;
import com.nuru.groups.SavingsGroup;
import com.nuru.groups.SavingsGroupRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared group authorization layer. Every group-scoped service enforces access here —
 * controllers and the AI tool layer never reimplement these checks.
 *
 * <p>Ordering rule: membership is verified before group existence, so probing random
 * group ids reveals nothing (always 403 for non-members).
 */
@Service
public class GroupAuth {

  private final SavingsGroupRepository groups;
  private final MembershipRepository memberships;
  private final GroupRoleRepository roles;
  private final RolePermissionRepository rolePermissions;

  public GroupAuth(SavingsGroupRepository groups, MembershipRepository memberships,
      GroupRoleRepository roles, RolePermissionRepository rolePermissions) {
    this.groups = groups;
    this.memberships = memberships;
    this.roles = roles;
    this.rolePermissions = rolePermissions;
  }

  /** Group lookup for pre-membership flows (join requests, invitation acceptance). */
  @Transactional(readOnly = true)
  public SavingsGroup groupOrNotFound(UUID groupId) {
    return groups.findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
  }

  /** Active membership or 403. Never reveals whether the group id exists. */
  @Transactional(readOnly = true)
  public Membership requireMembership(UUID groupId, UUID userId) {
    return memberships.findByGroupIdAndUserId(groupId, userId)
        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
        .orElseThrow(() -> new ForbiddenOperationException("Not an active member of this group"));
  }

  /** Active membership plus the named permission, or 403. */
  @Transactional(readOnly = true)
  public Membership requirePermission(UUID groupId, UUID userId, Permission permission) {
    Membership membership = requireMembership(groupId, userId);
    if (!permissionsOf(membership).contains(permission)) {
      throw new ForbiddenOperationException("Missing permission: " + permission);
    }
    return membership;
  }

  @Transactional(readOnly = true)
  public boolean hasPermission(UUID groupId, UUID userId, Permission permission) {
    return hasAnyPermission(groupId, userId, permission);
  }

  @Transactional(readOnly = true)
  public boolean hasAnyPermission(UUID groupId, UUID userId, Permission... permissions) {
    return memberships.findByGroupIdAndUserId(groupId, userId)
        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
        .map(m -> {
          Set<Permission> held = permissionsOf(m);
          for (Permission permission : permissions) {
            if (held.contains(permission)) {
              return true;
            }
          }
          return false;
        })
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean isAdmin(UUID groupId, UUID userId) {
    return memberships.findByGroupIdAndUserId(groupId, userId)
        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
        .map(m -> roleName(m).equals(GroupRole.ADMIN))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public Set<Permission> permissionsOf(Membership membership) {
    if (roleName(membership).equals(GroupRole.ADMIN)) {
      return EnumSet.allOf(Permission.class);
    }
    return rolePermissions.findByRoleId(membership.getRoleId()).stream()
        .map(rp -> Permission.valueOf(rp.getPermission()))
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
  }

  @Transactional(readOnly = true)
  public String roleName(Membership membership) {
    return roles.findById(membership.getRoleId())
        .map(GroupRole::getName)
        .orElseThrow(() -> new IllegalStateException("Membership references missing role"));
  }

  @Transactional(readOnly = true)
  public GroupRole roleOrNotFound(UUID groupId, UUID roleId) {
    GroupRole role = roles.findById(roleId)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    if (!role.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Role not found in this group");
    }
    return role;
  }
}
