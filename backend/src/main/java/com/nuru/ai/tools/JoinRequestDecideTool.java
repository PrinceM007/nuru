package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.GroupDto;
import com.nuru.service.MembershipService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Approves or rejects a membership join request. Runs through proposal + confirm. */
@Component
public class JoinRequestDecideTool implements AiTool {

  private final MembershipService membershipService;

  public JoinRequestDecideTool(MembershipService membershipService) {
    this.membershipService = membershipService;
  }

  @Override
  public String name() {
    return "join_request_decide";
  }

  @Override
  public String description() {
    return "Approve or reject a pending group join request. Requires confirmation; "
        + "the caller needs member management rights.";
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public List<ToolParam> parameters() {
    return List.of(
        new ToolParam("joinRequestId", ParamType.UUID, true, "Join request id"),
        new ToolParam("decision", ParamType.STRING, true, "APPROVE or REJECT",
            List.of("APPROVE", "REJECT"), null),
        new ToolParam("reason", ParamType.STRING, false,
            "Reason (required for rejection)", 500));
  }

  @Override
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    UUID requestId = (UUID) args.get("joinRequestId");
    boolean approve = "APPROVE".equalsIgnoreCase((String) args.get("decision"));
    GroupDto.JoinRequest result = approve
        ? membershipService.approveJoinRequest(groupId, requestId, callerId, null)
        : membershipService.rejectJoinRequest(groupId, requestId, callerId,
            (String) args.get("reason"));
    return AiToolResult.of(
        "Join request " + result.status().toLowerCase() + ".",
        Map.of("requestId", result.id().toString(), "status", result.status()));
  }

  @Override
  public String describeProposal(Map<String, Object> args) {
    return ((String) args.get("decision")).toLowerCase() + " join request "
        + args.get("joinRequestId") + ".";
  }
}
