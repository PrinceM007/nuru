package com.nuru.ai;

import com.nuru.ai.AiTool.ParamType;
import com.nuru.ai.AiTool.ToolParam;
import com.nuru.api.error.UnprocessableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Central validation for tool arguments. Model output is untrusted: unknown tools,
 * unknown parameters, wrong types, and missing required values are all rejected
 * before any service runs.
 */
@Component
public class AiToolRegistry {

  private final Map<String, AiTool> tools;

  public AiToolRegistry(List<AiTool> toolBeans) {
    this.tools = new HashMap<>();
    for (AiTool tool : toolBeans) {
      if (tools.put(tool.name(), tool) != null) {
        throw new IllegalStateException("Duplicate AI tool name: " + tool.name());
      }
    }
  }

  public AiTool requireTool(String name) {
    AiTool tool = tools.get(name);
    if (tool == null) {
      throw new UnprocessableException("Unknown AI tool: " + name);
    }
    return tool;
  }

  public List<AiTool.AiToolSpec> specs() {
    return tools.values().stream()
        .map(t -> new AiTool.AiToolSpec(t.name(), t.description(), t.parameters()))
        .toList();
  }

  /** Validates and coerces raw arguments; returns only declared parameters. */
  public Map<String, Object> validatedArgs(AiTool tool, Map<String, Object> raw) {
    Map<String, Object> args = raw == null ? Map.of() : raw;
    Map<String, Object> result = new HashMap<>();
    for (ToolParam param : tool.parameters()) {
      Object value = args.get(param.name());
      if (value == null) {
        if (param.required()) {
          throw new UnprocessableException(
              "Tool " + tool.name() + " is missing required argument: " + param.name());
        }
        continue;
      }
      result.put(param.name(), coerce(tool.name(), param, value));
    }
    return result;
  }

  private Object coerce(String tool, ToolParam param, Object value) {
    String label = "Tool " + tool + " argument '" + param.name() + "'";
    try {
      return switch (param.type()) {
        case STRING -> {
          if (!(value instanceof String text)) {
            throw new UnprocessableException(label + " must be a string");
          }
          String trimmed = text.trim();
          if (trimmed.isEmpty() && param.required()) {
            throw new UnprocessableException(label + " must not be blank");
          }
          if (param.maxLength() != null && trimmed.length() > param.maxLength()) {
            throw new UnprocessableException(
                label + " must be at most " + param.maxLength() + " characters");
          }
          if (param.enumValues() != null && !trimmed.isEmpty()
              && param.enumValues().stream().noneMatch(e -> e.equalsIgnoreCase(trimmed))) {
            throw new UnprocessableException(
                label + " must be one of " + param.enumValues());
          }
          yield trimmed;
        }
        case STRING_LIST -> {
          if (!(value instanceof List<?> list)) {
            throw new UnprocessableException(label + " must be a list of strings");
          }
          if (list.size() > 10) {
            throw new UnprocessableException(label + " must have at most 10 items");
          }
          for (Object item : list) {
            if (!(item instanceof String)) {
              throw new UnprocessableException(label + " must be a list of strings");
            }
          }
          yield list.stream().map(item -> ((String) item).trim()).toList();
        }
        case INTEGER -> {
          if (value instanceof Number number) {
            yield number.intValue();
          }
          yield Integer.parseInt(value.toString().trim());
        }
        case NUMBER -> {
          if (value instanceof BigDecimal decimal) {
            yield decimal;
          }
          yield new BigDecimal(value.toString().trim());
        }
        case BOOLEAN -> {
          if (value instanceof Boolean bool) {
            yield bool;
          }
          yield Boolean.parseBoolean(value.toString().trim());
        }
        case UUID -> UUID.fromString(value.toString().trim());
        case DATE -> LocalDate.parse(value.toString().trim());
        case DATETIME -> OffsetDateTime.parse(value.toString().trim());
      };
    } catch (UnprocessableException e) {
      throw e;
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw new UnprocessableException(
          label + " has an invalid " + param.type().name().toLowerCase() + " value");
    }
  }
}
