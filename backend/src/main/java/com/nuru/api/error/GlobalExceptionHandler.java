package com.nuru.api.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
      HttpServletRequest request) {
    Map<String, String> details = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      details.put(error.getField(), error.getDefaultMessage());
    }
    return body(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
      HttpServletRequest request) {
    Map<String, String> details = new HashMap<>();
    ex.getConstraintViolations().forEach(v ->
        details.put(v.getPropertyPath().toString(), v.getMessage()));
    return body(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadable(HttpServletRequest request) {
    return body(HttpStatus.BAD_REQUEST, "Malformed request body", request, null);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiError> handleNoHandler(HttpServletRequest request) {
    return body(HttpStatus.NOT_FOUND, "No such endpoint", request, null);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,
      HttpServletRequest request) {
    return body(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
  }

  @ExceptionHandler({AccessDeniedException.class, ForbiddenOperationException.class})
  public ResponseEntity<ApiError> handleForbidden(RuntimeException ex, HttpServletRequest request) {
    // Deliberately generic: do not reveal which resource exists or which check failed.
    return body(HttpStatus.FORBIDDEN, "Access denied", request, null);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleUnauthenticated(HttpServletRequest request) {
    return body(HttpStatus.UNAUTHORIZED, "Authentication required", request, null);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
    return body(HttpStatus.CONFLICT, ex.getMessage(), request, null);
  }

  @ExceptionHandler(UnprocessableException.class)
  public ResponseEntity<ApiError> handleUnprocessable(UnprocessableException ex,
      HttpServletRequest request) {
    return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null);
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<ApiError> handleUnavailable(ServiceUnavailableException ex,
      HttpServletRequest request) {
    return body(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
      HttpServletRequest request) {
    log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause());
    return body(HttpStatus.CONFLICT, "Request conflicts with existing data", request, null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error at {}", request.getRequestURI(), ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request, null);
  }

  private ResponseEntity<ApiError> body(HttpStatus status, String message,
      HttpServletRequest request, Map<String, String> details) {
    return ResponseEntity.status(status).body(ApiError.of(
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI(),
        MDC.get("correlationId"),
        details));
  }
}
