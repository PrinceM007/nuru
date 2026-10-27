package com.nuru.ai;

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

/** Default configuration: no AI provider → explicit unavailable state (no fabrication). */
class AiUnavailableIT extends SharedContainerBase {

  @Autowired
  private TestRestTemplate rest;

  @Test
  void unconfiguredProviderReportsUnavailable() {
    Session user = TestAuth.register(rest, "ainone");
    TestAuth.post(rest, user.accessToken(), "/api/v1/users/me/consents",
        Map.of("purpose", "AI_ASSISTANCE", "granted", true), Map.class);

    ResponseEntity<Map> status = TestAuth.get(rest, user.accessToken(), "/api/v1/ai/status",
        Map.class);
    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(status.getBody())
        .containsEntry("provider", "UNCONFIGURED")
        .containsEntry("available", false);

    ResponseEntity<Map> conversation = TestAuth.post(rest, user.accessToken(),
        "/api/v1/ai/conversations", Map.of(), Map.class);
    String conversationId = (String) conversation.getBody().get("id");
    ResponseEntity<Map> chat = TestAuth.post(rest, user.accessToken(),
        "/api/v1/ai/conversations/" + conversationId + "/messages",
        Map.of("text", "summarize my contributions"), Map.class);
    assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(chat.getBody().get("message").toString()).contains("not configured");
  }
}
