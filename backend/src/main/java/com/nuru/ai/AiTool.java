package com.nuru.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A named application capability the assistant may invoke. Every tool delegates to
 * the same application services the REST controllers use, so authorization,
 * validation, and transactions are identical for UI and AI paths.
 */
public interface AiTool {

  String name();

  String description();

  /** True when the tool changes state or is consequential: needs a user confirmation. */
  boolean requiresConfirmation();

  List<ToolParam> parameters();

  /**
   * Executes the tool for an authenticated user. Services enforce authorization;
   * tools never bypass it. Returns recorded facts only — never invented data.
   */
  AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args);

  /** Short human-readable description of a proposed call, shown on the confirm screen. */
  default String describeProposal(Map<String, Object> args) {
    return "Run " + name() + " with the provided details.";
  }

  enum ParamType {
    STRING,
    STRING_LIST,
    INTEGER,
    NUMBER,
    BOOLEAN,
    UUID,
    DATE,
    DATETIME
  }

  static UUID requireGroup(UUID groupId) {
    if (groupId == null) {
      throw new com.nuru.api.error.UnprocessableException(
          "This action needs a group — open or create a group conversation first");
    }
    return groupId;
  }

  record ToolParam(String name, ParamType type, boolean required, String description,
      List<String> enumValues, Integer maxLength) {

    public ToolParam(String name, ParamType type, boolean required, String description) {
      this(name, type, required, description, null, null);
    }

    public ToolParam(String name, ParamType type, boolean required, String description,
        int maxLength) {
      this(name, type, required, description, null, maxLength);
    }
  }

  record AiToolSpec(String name, String description, List<ToolParam> parameters) {
  }
}
