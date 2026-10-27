package com.nuru.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuru.support.SharedContainerBase;
import com.nuru.support.TestAuth;
import com.nuru.support.TestAuth.Session;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Default configuration: identity and credit providers report unavailable, never fake data. */
class IdentityUnavailableIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Test
  void unconfiguredProvidersReportUnavailable() {
    Session user = TestAuth.register(rest, "nounprov");
    TestAuth.post(rest, user.accessToken(), "/api/v1/users/me/consents",
        Map.of("purpose", "IDENTITY_VERIFICATION", "granted", true), Map.class);

    ResponseEntity<Map> status = TestAuth.get(rest, user.accessToken(),
        "/api/v1/verification/me", Map.class);
    assertThat(status.getBody()).containsEntry("status", "UNAVAILABLE");

    assertThat(TestAuth.post(rest, user.accessToken(), "/api/v1/verification/me/start",
        Map.of(), Map.class).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    ResponseEntity<Map> group = TestAuth.post(rest, user.accessToken(), "/api/v1/groups",
        Map.of("name", "No Providers", "currency", "KES"), Map.class);
    String groupId = (String) group.getBody().get("id");
    assertThat(TestAuth.get(rest, user.accessToken(),
        "/api/v1/groups/" + groupId + "/credit/estimate", Map.class).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
