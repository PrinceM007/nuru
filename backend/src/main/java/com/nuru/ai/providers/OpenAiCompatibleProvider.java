package com.nuru.ai.providers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuru.ai.AiProvider;
import com.nuru.ai.AiProviderException;
import com.nuru.ai.AiTool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAI-compatible chat completions provider (OpenAI or any compatible gateway).
 * Credentials come only from runtime configuration and are never logged.
 * Timeouts and refusals surface as explicit, safe states.
 */
@Component
public class OpenAiCompatibleProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

  private static final String SYSTEM_PROMPT = """
      You are Nuru, an assistant for African savings groups and stokvels.
      Rules you must follow:
      - Use ONLY the provided tools to obtain balances, members, meetings, votes,
        loans, or statements. Never invent figures, people, votes, or delivery states.
      - Clearly separate recorded facts (tool results) from guidance.
      - State-changing tools only prepare proposals; the application asks the user to
        confirm. Never claim an action succeeded — report what the tool returned.
      - Never reveal one member's private data to another; tools already scope data.
      - Never output credentials, tokens, or secrets.
      - If a tool call fails, explain the failure plainly and suggest a next step.
      """;

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String apiKey;
  private final String model;

  public OpenAiCompatibleProvider(RestTemplateBuilder builder, ObjectMapper objectMapper,
      @Value("${nuru.ai.base-url:}") String baseUrl,
      @Value("${nuru.ai.api-key:}") String apiKey,
      @Value("${nuru.ai.model:}") String model,
      @Value("${nuru.ai.timeout-ms:15000}") long timeoutMs) {
    this.restTemplate = builder
        .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000)))
        .readTimeout(Duration.ofMillis(timeoutMs))
        .build();
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.apiKey = apiKey == null ? "" : apiKey;
    this.model = model == null ? "" : model.trim();
  }

  @Override
  public String name() {
    return "OPENAI_COMPATIBLE";
  }

  @Override
  public boolean isAvailable() {
    return !baseUrl.isEmpty() && !apiKey.isEmpty() && !model.isEmpty();
  }

  @Override
  public String statusDetail() {
    if (isAvailable()) {
      return "OpenAI-compatible provider configured (model hidden from clients).";
    }
    return "OpenAI-compatible provider is missing base URL, API key, or model.";
  }

  @Override
  public AiPlannerResponse plan(AiPlannerRequest request) throws AiProviderException {
    if (!isAvailable()) {
      throw new AiProviderException(AiProviderException.Kind.TRANSPORT,
          "AI provider is not configured");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    List<Map<String, Object>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
    int from = Math.max(0, request.history().size() - 20);
    for (AiProvider.HistoryMessage history : request.history().subList(from,
        request.history().size())) {
      messages.add(Map.of("role", history.role().toLowerCase(), "content", history.content()));
    }
    messages.add(Map.of("role", "user", "content", request.userText()));
    body.put("messages", messages);
    body.put("tools", request.tools().stream().map(this::toFunctionTool).toList());
    body.put("tool_choice", "auto");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);
    String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions"
        : baseUrl + "/chat/completions";
    ResponseEntity<String> response;
    try {
      response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    } catch (ResourceAccessException e) {
      log.warn("AI provider timeout/unreachable");
      throw new AiProviderException(AiProviderException.Kind.TIMEOUT,
          "AI provider timed out; please try again", e);
    } catch (Exception e) {
      log.warn("AI provider transport failure: {}", e.getMessage());
      throw new AiProviderException(AiProviderException.Kind.TRANSPORT,
          "AI provider is unreachable right now", e);
    }
    if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
      log.warn("AI provider returned status {}", response.getStatusCode());
      throw new AiProviderException(AiProviderException.Kind.TRANSPORT,
          "AI provider returned an error");
    }
    return parseResponse(response.getBody());
  }

  private AiPlannerResponse parseResponse(String json) throws AiProviderException {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode message = root.path("choices").path(0).path("message");
      if (message.isMissingNode()) {
        throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
            "AI provider returned an unexpected response");
      }
      if (!message.path("refusal").isMissingNode()
          && !message.path("refusal").isNull()) {
        return new AiPlannerResponse(message.path("refusal").asText(""), List.of(), true);
      }
      String content = message.path("content").asText("");
      List<PlannedCall> calls = new ArrayList<>();
      for (JsonNode call : message.path("tool_calls")) {
        String toolName = call.path("function").path("name").asText("");
        String argsJson = call.path("function").path("arguments").asText("{}");
        Map<String, Object> args = objectMapper.readValue(argsJson,
            new TypeReference<Map<String, Object>>() {
            });
        if (!toolName.isBlank()) {
          calls.add(new PlannedCall(toolName, args));
        }
      }
      return new AiPlannerResponse(content == null ? "" : content, calls, false);
    } catch (AiProviderException e) {
      throw e;
    } catch (Exception e) {
      throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
          "AI provider returned an unparseable response", e);
    }
  }

  private Map<String, Object> toFunctionTool(AiTool.AiToolSpec spec) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (AiTool.ToolParam param : spec.parameters()) {
      Map<String, Object> schema = new LinkedHashMap<>();
      schema.put("type", switch (param.type()) {
        case STRING -> "string";
        case STRING_LIST -> "array";
        case INTEGER -> "integer";
        case NUMBER -> "number";
        case BOOLEAN -> "boolean";
        case UUID, DATE, DATETIME -> "string";
      });
      if (param.type() == AiTool.ParamType.STRING_LIST) {
        schema.put("items", Map.of("type", "string"));
      }
      schema.put("description", param.description());
      if (param.enumValues() != null) {
        schema.put("enum", param.enumValues());
      }
      properties.put(param.name(), schema);
      if (param.required()) {
        required.add(param.name());
      }
    }
    return Map.of("type", "function", "function", Map.of(
        "name", spec.name(),
        "description", spec.description(),
        "parameters", Map.of("type", "object", "properties", properties,
            "required", required, "additionalProperties", false)));
  }
}
