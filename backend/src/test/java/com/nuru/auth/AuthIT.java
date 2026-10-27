package com.nuru.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Test
  void registerLoginMeRefreshLogoutCycle() {
    TestAuth.Session session = TestAuth.register(rest, "cycle");

    ResponseEntity<Map> me = TestAuth.get(rest, session.accessToken(), "/api/v1/users/me",
        Map.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().get("email")).isEqualTo(session.email());

    ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
        TestAuth.json(Map.of("email", session.email(), "password", "correct-horse-42")),
        Map.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    String rotatedRefresh = (String) login.getBody().get("refreshToken");

    ResponseEntity<Map> refreshed = rest.postForEntity("/api/v1/auth/refresh",
        TestAuth.json(Map.of("refreshToken", rotatedRefresh)), Map.class);
    assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    String newRefresh = (String) refreshed.getBody().get("refreshToken");

    // Old refresh token was rotated and must no longer work.
    ResponseEntity<Map> replay = rest.postForEntity("/api/v1/auth/refresh",
        TestAuth.json(Map.of("refreshToken", rotatedRefresh)), Map.class);
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<Void> logout = TestAuth.post(rest, session.accessToken(),
        "/api/v1/auth/logout", Map.of("refreshToken", newRefresh), Void.class);
    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<Map> afterLogout = rest.postForEntity("/api/v1/auth/refresh",
        TestAuth.json(Map.of("refreshToken", newRefresh)), Map.class);
    assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void duplicateEmailIsRejected() {
    TestAuth.Session session = TestAuth.register(rest, "dupe");
    ResponseEntity<Map> again = rest.postForEntity("/api/v1/auth/register",
        TestAuth.json(Map.of("email", session.email(), "password", "correct-horse-42",
            "displayName", "Dup")),
        Map.class);
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void wrongPasswordIsUnauthorized() {
    TestAuth.Session session = TestAuth.register(rest, "wrongpw");
    ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
        TestAuth.json(Map.of("email", session.email(), "password", "nope-nope-nope")),
        Map.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void invalidRegistrationIsRejectedWithFieldErrors() {
    ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/register",
        TestAuth.json(Map.of("email", "not-an-email", "password", "short")),
        Map.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsKey("details");
    assertThat(response.getBody().get("message")).isEqualTo("Validation failed");
  }

  @Test
  void unauthenticatedProfileIsUnauthorized() {
    ResponseEntity<Map> me = rest.getForEntity("/api/v1/users/me", Map.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void preferencesAndConsentsAreSelfManaged() {
    TestAuth.Session session = TestAuth.register(rest, "prefs");

    ResponseEntity<Map> prefs = TestAuth.patch(rest, session.accessToken(),
        "/api/v1/users/me/preferences",
        Map.of("emailEnabled", false, "smsEnabled", true, "locale", "sw"),
        Map.class);
    assertThat(prefs.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(prefs.getBody()).containsEntry("emailEnabled", false)
        .containsEntry("locale", "sw");

    ResponseEntity<Map> consent = TestAuth.post(rest, session.accessToken(),
        "/api/v1/users/me/consents",
        Map.of("purpose", "AI_ASSISTANCE", "granted", true),
        Map.class);
    assertThat(consent.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> unknown = TestAuth.post(rest, session.accessToken(),
        "/api/v1/users/me/consents",
        Map.of("purpose", "BOGUS_PURPOSE", "granted", true),
        Map.class);
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
