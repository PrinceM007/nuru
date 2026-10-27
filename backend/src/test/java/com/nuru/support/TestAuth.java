package com.nuru.support;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Test helper: register users and call the API with bearer tokens. */
public final class TestAuth {

  private TestAuth() {
  }

  public record Session(String email, String accessToken, String refreshToken, UUID userId) {
  }

  public static Session register(TestRestTemplate rest, String emailPrefix) {
    String email = emailPrefix + "+" + UUID.randomUUID().toString().substring(0, 8)
        + "@example.com";
    Map<String, Object> body = Map.of(
        "email", email,
        "password", "correct-horse-42",
        "displayName", "Test " + emailPrefix);
    ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/register", json(body),
        Map.class);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("register failed: " + response);
    }
    String access = (String) response.getBody().get("accessToken");
    String refresh = (String) response.getBody().get("refreshToken");
    ResponseEntity<Map> me = get(rest, access, "/api/v1/users/me", Map.class);
    UUID userId = UUID.fromString((String) me.getBody().get("id"));
    return new Session(email, access, refresh, userId);
  }

  public static HttpEntity<?> json(Object body, String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return new HttpEntity<>(body, headers);
  }

  public static HttpEntity<?> json(Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  public static <T> ResponseEntity<T> get(TestRestTemplate rest, String accessToken, String url,
      Class<T> type) {
    return rest.exchange(url, HttpMethod.GET, json(null, accessToken), type);
  }

  public static <T> ResponseEntity<T> post(TestRestTemplate rest, String accessToken, String url,
      Object body, Class<T> type) {
    return rest.exchange(url, HttpMethod.POST, json(body, accessToken), type);
  }

  public static <T> ResponseEntity<T> postWithKey(TestRestTemplate rest, String accessToken,
      String url, Object body, String idempotencyKey, Class<T> type) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    headers.set("Idempotency-Key", idempotencyKey);
    return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), type);
  }

  public static <T> ResponseEntity<T> patch(TestRestTemplate rest, String accessToken, String url,
      Object body, Class<T> type) {
    return rest.exchange(url, HttpMethod.PATCH, json(body, accessToken), type);
  }

  public static <T> ResponseEntity<T> delete(TestRestTemplate rest, String accessToken, String url,
      Class<T> type) {
    return rest.exchange(url, HttpMethod.DELETE, json(null, accessToken), type);
  }

  public static <T> ResponseEntity<T> exchangePut(TestRestTemplate rest, String accessToken,
      String url, Object body, Class<T> type) {
    return rest.exchange(url, HttpMethod.PUT, json(body, accessToken), type);
  }
}
