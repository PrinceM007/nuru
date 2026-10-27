package com.nuru.api;

import com.nuru.api.dto.GroupDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.InvitationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Invitations")
public class InvitationController {

  private final InvitationService invitationService;

  public InvitationController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @PostMapping("/api/v1/groups/{groupId}/invitations")
  @ResponseStatus(HttpStatus.CREATED)
  public GroupDto.InvitationCreated create(@PathVariable UUID groupId,
      @Valid @RequestBody(required = false) GroupDto.CreateInvitationRequest request) {
    return invitationService.createInvitation(groupId, currentUser(),
        request == null ? null : request.roleId(),
        request == null ? null : request.expiresInHours(),
        request == null ? null : request.maxUses());
  }

  @GetMapping("/api/v1/groups/{groupId}/invitations")
  public Page<GroupDto.Invitation> list(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return invitationService.listInvitations(groupId, currentUser(), pageable);
  }

  @DeleteMapping("/api/v1/groups/{groupId}/invitations/{invitationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID groupId, @PathVariable UUID invitationId) {
    invitationService.revokeInvitation(groupId, invitationId, currentUser());
  }

  @PostMapping("/api/v1/invitations/accept")
  public GroupDto.AcceptedInvitation accept(
      @Valid @RequestBody GroupDto.AcceptInvitationRequest request) {
    return new GroupDto.AcceptedInvitation(
        invitationService.acceptInvitation(request.code(), currentUser()));
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
