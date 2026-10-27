package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.GroupDto;
import com.nuru.service.InvitationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates a group invitation code. Runs through proposal + confirm. */
@Component
public class InvitationCreateTool implements AiTool {

  private final InvitationService invitationService;

  public InvitationCreateTool(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @Override
  public String name() {
    return "invitation_create";
  }

  @Override
  public String description() {
    return "Create a group invitation code for new members. Requires confirmation; "
        + "the caller needs invitation management rights.";
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public List<ToolParam> parameters() {
    return List.of(
        new ToolParam("expiresInHours", ParamType.INTEGER, false,
            "Expiry in hours, 1-720 (default 72)"),
        new ToolParam("maxUses", ParamType.INTEGER, false,
            "Maximum uses, 1-100 (default 1)"));
  }

  @Override
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    Integer hours = (Integer) args.get("expiresInHours");
    Integer uses = (Integer) args.get("maxUses");
    GroupDto.InvitationCreated invitation = invitationService.createInvitation(groupId,
        callerId, null, hours == null ? null : hours.longValue(), uses);
    return AiToolResult.of(
        "Invitation created, expires " + invitation.expiresAt() + ".",
        Map.of("invitationId", invitation.id().toString(), "code", invitation.code(),
            "expiresAt", invitation.expiresAt().toString()));
  }

  @Override
  public String describeProposal(Map<String, Object> args) {
    return "Create an invitation code (expires in "
        + args.getOrDefault("expiresInHours", 72) + "h, max uses "
        + args.getOrDefault("maxUses", 1) + ").";
  }
}
