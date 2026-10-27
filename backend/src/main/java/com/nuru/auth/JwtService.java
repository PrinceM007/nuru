package com.nuru.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Short-lived signed access tokens. Session revocation is handled via refresh tokens. */
@Service
public class JwtService {

  private final SecretKey key;
  private final long accessMinutes;

  public JwtService(
      @Value("${nuru.jwt.secret}") String secret,
      @Value("${nuru.jwt.access-minutes:30}") long accessMinutes) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "nuru.jwt.secret must be at least 32 bytes; set NURU_JWT_SECRET per environment");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessMinutes = accessMinutes;
  }

  public String createAccessToken(UUID userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
        .signWith(key)
        .compact();
  }

  public Optional<UUID> validateAndExtractUserId(String token) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload();
      return Optional.of(UUID.fromString(claims.getSubject()));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public long getAccessMinutes() {
    return accessMinutes;
  }
}
