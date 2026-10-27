package com.nuru.api;

import com.nuru.api.dto.GroupDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.MembershipService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@Tag(name = "Memberships")
public class MembershipController {

  private final MembershipService membershipService;

  public MembershipController(MembershipService membershipService) {
    this.membershipService = membershipService;
  }

  @GetMapping("/members")
  public Page<GroupDto.Member> members(@PathVariable UUID groupId,
      @PageableDefault(size = 50) Pageable pageable) {
    return membershipService.listMembers(groupId, currentUser(), pageable);
  }

  @PostMapping("/join-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public GroupDto.JoinRequest requestJoin(@PathVariable UUID groupId,
      @Valid @RequestBody(required = false) GroupDto.JoinRequestRequest request) {
    return membershipService.requestJoin(groupId, currentUser(),
        request == null ? null : request.message());
  }

  @GetMapping("/join-requests")
  public Page<GroupDto.JoinRequest> joinRequests(@PathVariable UUID groupId,
      @RequestParam(required = false) String status,
      @PageableDefault(size = 20) Pageable pageable) {
    return membershipService.listJoinRequests(groupId, currentUser(), status, pageable);
  }

  @PostMapping("/join-requests/{requestId}/approve")
  public GroupDto.JoinRequest approve(@PathVariable UUID groupId,
      @PathVariable UUID requestId,
      @Valid @RequestBody(required = false) GroupDto.DecideJoinRequest decision) {
    return membershipService.approveJoinRequest(groupId, requestId, currentUser(),
        decision == null ? null : decision.roleId());
  }

  @PostMapping("/join-requests/{requestId}/reject")
  public GroupDto.JoinRequest reject(@PathVariable UUID groupId,
      @PathVariable UUID requestId,
      @Valid @RequestBody(required = false) GroupDto.DecideJoinRequest decision) {
    return membershipService.rejectJoinRequest(groupId, requestId, currentUser(),
        decision == null ? null : decision.reason());
  }

  @DeleteMapping("/join-requests/{requestId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelJoinRequest(@PathVariable UUID groupId, @PathVariable UUID requestId) {
    membershipService.cancelJoinRequest(groupId, requestId, currentUser());
  }

  @PatchMapping("/members/{userId}/role")
  public GroupDto.Member changeRole(@PathVariable UUID groupId, @PathVariable UUID userId,
      @Valid @RequestBody GroupDto.ChangeRoleRequest request) {
    return membershipService.changeRole(groupId, userId, currentUser(), request.roleId());
  }

  @DeleteMapping("/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
    membershipService.removeMember(groupId, userId, currentUser());
  }

  @DeleteMapping("/members/me")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void leave(@PathVariable UUID groupId) {
    membershipService.leaveGroup(groupId, currentUser());
  }

  @PostMapping("/admin-transfer")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void transferAdmin(@PathVariable UUID groupId,
      @Valid @RequestBody GroupDto.TransferAdminRequest request) {
    membershipService.transferAdmin(groupId, currentUser(), request.newAdminUserId());
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
