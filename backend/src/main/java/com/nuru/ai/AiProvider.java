package com.nuru.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-neutral AI planning interface. Providers propose text and tool calls;
 * they never touch the database, execute code, or decide permissions. Tool
 * arguments are untrusted input and are validated before any execution.
 */
public interface AiProvider {

  String name();

  /** False when credentials/endpoints are not configured — no calls are attempted. */
  boolean isAvailable();

  /** Human-safe status shown to users (never includes keys or URLs with secrets). */
  String statusDetail();

  AiPlannerResponse plan(AiPlannerRequest request) throws AiProviderException;

  record AiPlannerRequest(UUID userId, UUID groupId, List<HistoryMessage> history,
      String userText, List<AiTool.AiToolSpec> tools) {
  }

  record HistoryMessage(String role, String content) {
  }

  record AiPlannerResponse(String text, List<PlannedCall> toolCalls, boolean refusal) {
    public static AiPlannerResponse textOnly(String text) {
      return new AiPlannerResponse(text, List.of(), false);
    }
  }

  record PlannedCall(String tool, Map<String, Object> arguments) {
  }
}
