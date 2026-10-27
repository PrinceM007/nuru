package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.StatementDto;
import com.nuru.domain.StatementRequestType;
import com.nuru.service.StatementService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Prepares a statement request. Runs through proposal + confirm. */
@Component
public class StatementRequestTool implements AiTool {

  private final StatementService statementService;

  public StatementRequestTool(StatementService statementService) {
    this.statementService = statementService;
  }

  @Override
  public String name() {
    return "statement_request";
  }

  @Override
  public String description() {
    return "Request a personal statement or group summary for a period. "
        + "Requires confirmation; group summaries may need admin approval.";
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public List<ToolParam> parameters() {
    return List.of(
        new ToolParam("requestType", ParamType.STRING, true,
            "PERSONAL_STATEMENT or GROUP_SUMMARY",
            List.of("PERSONAL_STATEMENT", "GROUP_SUMMARY"), null),
        new ToolParam("periodStart", ParamType.DATE, true, "Period start (YYYY-MM-DD)"),
        new ToolParam("periodEnd", ParamType.DATE, true, "Period end (YYYY-MM-DD)"));
  }

  @Override
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    StatementDto.Request request = statementService.request(groupId, callerId,
        StatementRequestType.valueOf(((String) args.get("requestType")).toUpperCase()),
        (LocalDate) args.get("periodStart"), (LocalDate) args.get("periodEnd"));
    return AiToolResult.of(
        "Statement request " + request.status().toLowerCase() + " (" + request.id() + ").",
        Map.of("requestId", request.id().toString(), "status", request.status()));
  }

  @Override
  public String describeProposal(Map<String, Object> args) {
    return "Request " + args.get("requestType") + " for " + args.get("periodStart")
        + " to " + args.get("periodEnd") + ".";
  }
}
