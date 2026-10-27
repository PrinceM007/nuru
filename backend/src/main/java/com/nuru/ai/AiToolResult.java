package com.nuru.ai;

import java.util.Map;

/**
 * Result of a tool execution. {@code facts} holds recorded data for the model and
 * UI; {@code summary} is the user-facing sentence. Guidance text must be labeled
 * as guidance, never as a recorded fact.
 */
public record AiToolResult(String summary, Map<String, Object> facts) {

  public static AiToolResult of(String summary, Map<String, Object> facts) {
    return new AiToolResult(summary, facts);
  }
}
