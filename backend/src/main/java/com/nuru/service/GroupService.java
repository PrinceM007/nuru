package com.nuru.service;

import com.nuru.api.dto.GroupDto;
import com.nuru.api.error.ConflictException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.MembershipStatus;
import com.nuru.domain.Permission;
import com.nuru.groups.GroupRole;
import com.nuru.groups.GroupRoleRepository;
import com.nuru.groups.Membership;
import com.nuru.groups.MembershipRepository;
import com.nuru.groups.RolePermission;
import com.nuru.groups.RolePermissionRepository;
import com.nuru.groups.SavingsGroup;
import com.nuru.groups.SavingsGroupRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

  /** Treasurer default grants: confirm/record contributions and see the full ledger. */
  private static final Set<Permission> TREASURER_DEFAULTS =
      EnumSet.of(Permission.CONTRIBUTIONS_CONFIRM, Permission.LEDGER_VIEW_FULL);

  private final SavingsGroupRepository groups;
  private final MembershipRepository memberships;
  private final GroupRoleRepository roles;
  private final RolePermissionRepository rolePermissions;
  private final GroupAuth groupAuth;
  private final AuditService auditService;

  public GroupService(SavingsGroupRepository groups, MembershipRepository memberships,
      GroupRoleRepository roles, RolePermissionRepository rolePermissions, GroupAuth groupAuth,
      AuditService auditService) {
    this.groups = groups;
    this.memberships = memberships;
    this.roles = roles;
    this.rolePermissions = rolePermissions;
    this.groupAuth = groupAuth;
    this.auditService = auditService;
  }

  @Transactional
  public GroupDto.Response createGroup(UUID creatorId, String name, String description,
      String currency) {
    SavingsGroup group = groups.save(new SavingsGroup(name.trim(),
        description == null ? null : description.trim(), currency.trim().toUpperCase(), creatorId));
    GroupRole admin = roles.save(new GroupRole(group.getId(), GroupRole.ADMIN, true));
    GroupRole treasurer = roles.save(new GroupRole(group.getId(), GroupRole.TREASURER, true));
    roles.save(new GroupRole(group.getId(), GroupRole.MEMBER, true));
    for (Permission permission : Permission.values()) {
      rolePermissions.save(new RolePermission(admin.getId(), permission.name()));
    }
    for (Permission permission : TREASURER_DEFAULTS) {
      rolePermissions.save(new RolePermission(treasurer.getId(), permission.name()));
    }
    memberships.save(new Membership(group.getId(), creatorId, admin.getId(),
        MembershipStatus.ACTIVE));
    auditService.record(creatorId, group.getId(), AuditActions.GROUP_CREATED, "SavingsGroup",
        group.getId().toString(), "SUCCESS", null);
    return GroupDto.Response.from(group, GroupRole.ADMIN);
  }

  @Transactional(readOnly = true)
  public Page<GroupDto.Response> listMyGroups(UUID userId, Pageable pageable) {
    Page<Membership> page = memberships.findByUserIdAndStatus(userId,
        MembershipStatus.ACTIVE, pageable);
    List<UUID> groupIds = page.getContent().stream().map(Membership::getGroupId).toList();
    var byId = groups.findAllById(groupIds).stream()
        .collect(java.util.stream.Collectors.toMap(SavingsGroup::getId, g -> g));
    return page.map(m -> {
      SavingsGroup group = byId.get(m.getGroupId());
      return group == null ? null
          : GroupDto.Response.from(group, groupAuth.roleName(m));
    });
  }

  @Transactional(readOnly = true)
  public GroupDto.Response getGroup(UUID groupId, UUID userId) {
    Membership membership = groupAuth.requireMembership(groupId, userId);
    SavingsGroup group = groupAuth.groupOrNotFound(groupId);
    return GroupDto.Response.from(group, groupAuth.roleName(membership));
  }

  @Transactional
  public GroupDto.Response updateGroup(UUID groupId, UUID userId, String name,
      String description) {
    Membership membership = groupAuth.requirePermission(groupId, userId,
        Permission.GROUP_SETTINGS_MANAGE);
    SavingsGroup group = groupAuth.groupOrNotFound(groupId);
    if (name != null) {
      group.setName(name.trim());
    }
    if (description != null) {
      group.setDescription(description.trim().isEmpty() ? null : description.trim());
    }
    auditService.record(userId, groupId, AuditActions.GROUP_UPDATED, "SavingsGroup",
        groupId.toString(), "SUCCESS", null);
    return GroupDto.Response.from(group, groupAuth.roleName(membership));
  }

  @Transactional(readOnly = true)
  public List<GroupDto.Role> listRoles(UUID groupId, UUID userId) {
    groupAuth.requireMembership(groupId, userId);
    List<GroupDto.Role> result = new ArrayList<>();
    for (GroupRole role : roles.findByGroupId(groupId)) {
      result.add(GroupDto.Role.from(role, permissionsOf(role.getId())));
    }
    return result;
  }

  @Transactional
  public GroupDto.Role createCustomRole(UUID groupId, UUID userId, String name,
      Set<String> permissionNames) {
    groupAuth.requirePermission(groupId, userId, Permission.ROLES_MANAGE);
    groupAuth.groupOrNotFound(groupId);
    String normalized = name.trim().toUpperCase();
    if (isReservedRoleName(normalized)) {
      throw new UnprocessableException("Role name is reserved: " + normalized);
    }
    if (roles.existsByGroupIdAndName(groupId, normalized)) {
      throw new ConflictException("Role already exists: " + normalized);
    }
    Set<Permission> grants = parsePermissions(permissionNames);
    GroupRole role = roles.save(new GroupRole(groupId, normalized, false));
    saveGrants(role.getId(), grants);
    auditService.record(userId, groupId, AuditActions.ROLE_CHANGED, "GroupRole",
        role.getId().toString(), "SUCCESS", "created role " + normalized);
    return GroupDto.Role.from(role, grants);
  }

  @Transactional
  public GroupDto.Role updateRolePermissions(UUID groupId, UUID userId, UUID roleId,
      Set<String> permissionNames) {
    groupAuth.requirePermission(groupId, userId, Permission.ROLES_MANAGE);
    GroupRole role = groupAuth.roleOrNotFound(groupId, roleId);
    if (role.getName().equals(GroupRole.ADMIN)) {
      throw new UnprocessableException("The ADMIN role always holds all permissions");
    }
    Set<Permission> grants = parsePermissions(permissionNames);
    rolePermissions.deleteByRoleId(role.getId());
    saveGrants(role.getId(), grants);
    auditService.record(userId, groupId, AuditActions.ROLE_CHANGED, "GroupRole",
        role.getId().toString(), "SUCCESS", "updated grants for " + role.getName());
    return GroupDto.Role.from(role, grants);
  }

  @Transactional
  public void deleteRole(UUID groupId, UUID userId, UUID roleId) {
    groupAuth.requirePermission(groupId, userId, Permission.ROLES_MANAGE);
    GroupRole role = groupAuth.roleOrNotFound(groupId, roleId);
    if (role.isSystem()) {
      throw new UnprocessableException("System roles cannot be deleted");
    }
    long inUse = memberships.countByGroupIdAndRoleIdAndStatus(groupId, role.getId(),
        MembershipStatus.ACTIVE);
    if (inUse > 0) {
      throw new ConflictException("Role is assigned to active members");
    }
    rolePermissions.deleteByRoleId(role.getId());
    roles.delete(role);
    auditService.record(userId, groupId, AuditActions.ROLE_CHANGED, "GroupRole",
        role.getId().toString(), "SUCCESS", "deleted role " + role.getName());
  }

  private Set<Permission> permissionsOf(UUID roleId) {
    return rolePermissions.findByRoleId(roleId).stream()
        .map(rp -> Permission.valueOf(rp.getPermission()))
        .collect(java.util.stream.Collectors.toCollection(
            () -> EnumSet.noneOf(Permission.class)));
  }

  private void saveGrants(UUID roleId, Set<Permission> grants) {
    for (Permission permission : grants) {
      rolePermissions.save(new RolePermission(roleId, permission.name()));
    }
  }

  private Set<Permission> parsePermissions(Set<String> names) {
    if (names == null || names.isEmpty()) {
      throw new UnprocessableException("At least one permission is required");
    }
    try {
      return names.stream()
          .map(n -> Permission.valueOf(n.trim().toUpperCase()))
          .collect(java.util.stream.Collectors.toCollection(
              () -> EnumSet.noneOf(Permission.class)));
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown permission name");
    }
  }

  private boolean isReservedRoleName(String name) {
    return name.equals(GroupRole.ADMIN) || name.equals(GroupRole.TREASURER)
        || name.equals(GroupRole.MEMBER);
  }
}
