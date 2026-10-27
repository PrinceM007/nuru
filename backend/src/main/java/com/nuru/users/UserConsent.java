package com.nuru.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only consent history. The latest row per (user, purpose) is the current decision.
 * Well-known purposes: AI_ASSISTANCE, IDENTITY_VERIFICATION.
 */
@Entity
@Table(name = "user_consents")
public class UserConsent {

  public static final String AI_ASSISTANCE = "AI_ASSISTANCE";
  public static final String IDENTITY_VERIFICATION = "IDENTITY_VERIFICATION";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 80)
  private String purpose;

  @Column(nullable = false)
  private boolean granted;

  @Column(name = "recorded_at", nullable = false)
  private OffsetDateTime recordedAt;

  protected UserConsent() {
  }

  public UserConsent(UUID userId, String purpose, boolean granted) {
    this.userId = userId;
    this.purpose = purpose;
    this.granted = granted;
  }

  @PrePersist
  void onCreate() {
    recordedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getPurpose() {
    return purpose;
  }

  public boolean isGranted() {
    return granted;
  }

  public OffsetDateTime getRecordedAt() {
    return recordedAt;
  }
}
