package com.nuru.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Server-side refresh token: only a SHA-256 hash is stored, so a DB read cannot mint sessions. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected RefreshToken() {
  }

  public RefreshToken(UUID userId, String tokenHash, OffsetDateTime expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
  }

  public boolean isUsable() {
    return revokedAt == null && expiresAt.isAfter(OffsetDateTime.now());
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(OffsetDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }
}
