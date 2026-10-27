package com.nuru.api.dto;

import com.nuru.domain.Permission;
import com.nuru.groups.GroupRole;
import com.nuru.groups.Invitation;
import com.nuru.groups.JoinRequest;
import com.nuru.groups.SavingsGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GroupDto {

  private GroupDto() {
  }

  public record CreateRequest(
      @NotBlank @Size(min = 1, max = 160) String name,
      @Size(max = 1000) String description,
      @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code")
      String currency) {
  }

  public record UpdateRequest(
      @Size(min = 1, max = 160) String name,
      @Size(max = 1000) String description) {
  }

  public record Response(UUID id, String name, String description, String currency,
      OffsetDateTime createdAt, String currentUserRole) {
    public static Response from(SavingsGroup group, String currentUserRole) {
      return new Response(group.getId(), group.getName(), group.getDescription(),
          group.getCurrency(), group.getCreatedAt(), currentUserRole);
    }
  }

  public record Member(UUID userId, String displayName, String email, String role,
      String status, OffsetDateTime joinedAt) {
  }

  public record Role(UUID id, String name, boolean system, Set<String> permissions) {
    public static Role from(GroupRole role, Set<Permission> permissions) {
      return new Role(role.getId(), role.getName(), role.isSystem(),
          permissions.stream().map(Permission::name).collect(Collectors.toSet()));
    }
  }

  public record CreateRoleRequest(
      @NotBlank @Size(min = 1, max = 60) String name,
      @NotNull @Size(min = 1) Set<String> permissions) {
  }

  public record UpdateRoleRequest(@NotNull @Size(min = 1) Set<String> permissions) {
  }

  public record ChangeRoleRequest(@NotNull UUID roleId) {
  }

  public record TransferAdminRequest(@NotNull UUID newAdminUserId) {
  }

  public record JoinRequestRequest(@Size(max = 500) String message) {
  }

  public record JoinRequest(UUID id, UUID groupId, UUID userId, String displayName,
      String status, String message, UUID reviewedBy, OffsetDateTime reviewedAt,
      String rejectionReason, OffsetDateTime createdAt) {
    public static JoinRequest from(com.nuru.groups.JoinRequest request, String displayName) {
      return new JoinRequest(request.getId(), request.getGroupId(), request.getUserId(),
          displayName, request.getStatus().name(), request.getMessage(),
          request.getReviewedBy(), request.getReviewedAt(), request.getRejectionReason(),
          request.getCreatedAt());
    }
  }

  public record DecideJoinRequest(
      UUID roleId,
      @Size(max = 500) String reason) {
  }

  public record CreateInvitationRequest(UUID roleId, Long expiresInHours, Integer maxUses) {
  }

  public record InvitationCreated(UUID id, UUID groupId, String role, String code,
      OffsetDateTime expiresAt, int maxUses) {
    public static InvitationCreated from(com.nuru.groups.Invitation invitation, String code,
        String role) {
      return new InvitationCreated(invitation.getId(), invitation.getGroupId(), role,
          code, invitation.getExpiresAt(), invitation.getMaxUses());
    }
  }

  public record Invitation(UUID id, UUID groupId, String role, OffsetDateTime expiresAt,
      OffsetDateTime revokedAt, int maxUses, int useCount, OffsetDateTime createdAt) {
    public static Invitation from(com.nuru.groups.Invitation invitation, String role) {
      return new Invitation(invitation.getId(), invitation.getGroupId(), role,
          invitation.getExpiresAt(), invitation.getRevokedAt(), invitation.getMaxUses(),
          invitation.getUseCount(), invitation.getCreatedAt());
    }
  }

  public record AcceptInvitationRequest(@NotBlank String code) {
  }

  public record AcceptedInvitation(UUID groupId) {
  }
}
