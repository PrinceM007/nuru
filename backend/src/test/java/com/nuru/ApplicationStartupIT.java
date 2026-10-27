package com.nuru;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Stage 2: application starts, migrates, and exposes health + consistent errors. */
class ApplicationStartupIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Test
  void healthReportsUp() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void readinessProbeReportsUp() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health/readiness", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void unknownEndpointReturnsConsistentErrorWithoutStackTrace() {
    ResponseEntity<String> response = rest.getForEntity("/api/v1/does-not-exist", String.class);
    // Security chain rejects unauthenticated API calls before routing: still a clean JSON error.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).contains("Authentication required");
    assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    if (response.getBody() != null) {
      assertThat(response.getBody()).doesNotContain("stackTrace", "StackTrace", "at com.nuru");
    }
  }

  @Test
  void openApiDocumentIsServed() {
    ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("Nuru API");
  }
}
