package com.nuru.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Consistent API error body. Never includes stack traces, SQL, or sensitive details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    String correlationId,
    Map<String, String> details) {

  public static ApiError of(int status, String error, String message, String path,
      String correlationId, Map<String, String> details) {
    return new ApiError(OffsetDateTime.now(), status, error, message, path, correlationId, details);
  }
}
