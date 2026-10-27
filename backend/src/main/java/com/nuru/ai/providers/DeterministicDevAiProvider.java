package com.nuru.ai.providers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuru.ai.AiProvider;
import com.nuru.ai.AiProviderException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic development adapter. It never generates free-form facts: it routes
 * recognized intents to real application tools (which return recorded data) and
 * otherwise explains its capabilities. Anything it cannot map to a tool is answered
 * with guidance or a clarifying question — never invented account data.
 *
 * <p>Supports an explicit dev-only invocation syntax for testing:
 * {@code tool:tool_name {"arg": "value"}}.
 */
@Component
public class DeterministicDevAiProvider implements AiProvider {

  private final ObjectMapper objectMapper;

  public DeterministicDevAiProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "DEV";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public String statusDetail() {
    return "Deterministic development adapter (no model calls; tools return recorded data).";
  }

  @Override
  public AiPlannerResponse plan(AiPlannerRequest request) throws AiProviderException {
    String text = request.userText() == null ? "" : request.userText().trim();
    if (text.toLowerCase().startsWith("tool:")) {
      return parseExplicitInvocation(text);
    }
    if (text.toLowerCase().startsWith("summarize the tool results")) {
      return summarizeLastToolResult(request);
    }
    String lower = text.toLowerCase();
    if (lower.contains("contribution")) {
      return call("contribution_summary", Map.of(), "Here is your recorded contribution summary.");
    }
    if (lower.contains("ledger") || lower.contains("balance")) {
      return call("ledger_explain", Map.of(), "Here is what the recorded ledger shows.");
    }
    if ((lower.contains("schedul") || lower.contains("create") || lower.contains("draft"))
        && lower.contains("meet")) {
      return AiPlannerResponse.textOnly(
          "I can prepare a meeting for you. Please provide the title, start time "
              + "(with timezone offset), and timezone — for example: "
              + "\"tool:meeting_create {\"title\": \"October meeting\", "
              + "\"startsAt\": \"2026-11-02T10:00:00+03:00\", \"timezone\": \"Africa/Nairobi\"}\". "
              + "You will review and confirm before anything is created.");
    }
    if (lower.contains("meet")) {
      return call("meeting_info", Map.of("upcomingOnly", true),
          "Here are the upcoming meetings I can see.");
    }
    if (lower.contains("statement")) {
      return AiPlannerResponse.textOnly(
          "I can prepare a statement request. Please provide the type (PERSONAL_STATEMENT "
              + "or GROUP_SUMMARY) and the period — for example: "
              + "\"tool:statement_request {\"requestType\": \"PERSONAL_STATEMENT\", "
              + "\"periodStart\": \"2026-10-01\", \"periodEnd\": \"2026-10-31\"}\". "
              + "You will review and confirm before it is submitted.");
    }
    if (lower.contains("loan")) {
      return AiPlannerResponse.textOnly(
          "I can prepare a loan request for you — for example: "
              + "\"tool:loan_request {\"principal\": 5000, \"termMonths\": 6}\". "
              + "You will review and confirm before it is submitted, and group approval "
              + "still follows the normal workflow.");
    }
    if (lower.contains("vote") || lower.contains("ballot")) {
      return AiPlannerResponse.textOnly(
          "I can prepare a draft vote for review — for example: "
              + "\"tool:vote_create {\"question\": \"Approve the budget?\", "
              + "\"ballotPolicy\": \"SECRET\", \"opensAt\": \"2026-11-01T08:00:00+03:00\", "
              + "\"closesAt\": \"2026-11-03T18:00:00+03:00\", "
              + "\"options\": [\"Yes\", \"No\"]}\". I never cast ballots or reveal secret votes.");
    }
    if (lower.contains("invit")) {
      return AiPlannerResponse.textOnly(
          "I can prepare an invitation code — for example: "
              + "\"tool:invitation_create {\"expiresInHours\": 72, \"maxUses\": 5}\". "
              + "You will review and confirm before it is created.");
    }
    return AiPlannerResponse.textOnly(
        "I can help with your savings group using recorded data only. Try asking for "
            + "'my contribution summary', 'upcoming meetings', or 'explain the ledger'. "
            + "For actions (meetings, statements, loans, votes, invitations), I prepare a "
            + "proposal that you review and confirm — nothing changes until you approve it.");
  }

  private AiPlannerResponse summarizeLastToolResult(AiPlannerRequest request) {
    List<HistoryMessage> history = request.history();
    for (int i = history.size() - 1; i >= 0; i--) {
      HistoryMessage message = history.get(i);
      if ("TOOL".equalsIgnoreCase(message.role())) {
        try {
          String summary = objectMapper.readTree(message.content()).path("summary").asText("");
          if (!summary.isBlank()) {
            return AiPlannerResponse.textOnly(summary);
          }
        } catch (Exception ignored) {
          break;
        }
      }
    }
    return AiPlannerResponse.textOnly("Done. Let me know if you want anything else.");
  }

  private AiPlannerResponse parseExplicitInvocation(String text) throws AiProviderException {
    String payload = text.substring("tool:".length()).trim();
    int brace = payload.indexOf('{');
    String toolName = (brace < 0 ? payload : payload.substring(0, brace)).trim();
    Map<String, Object> args;
    if (brace < 0) {
      args = Map.of();
    } else {
      try {
        args = objectMapper.readValue(payload.substring(brace),
            new TypeReference<Map<String, Object>>() {
            });
      } catch (Exception e) {
        throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
            "Could not parse tool arguments as JSON");
      }
    }
    return new AiPlannerResponse("Running " + toolName + ".",
        List.of(new PlannedCall(toolName, args)), false);
  }

  private AiPlannerResponse call(String tool, Map<String, Object> args, String message) {
    return new AiPlannerResponse(message, List.of(new PlannedCall(tool, args)), false);
  }
}
