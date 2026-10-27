package com.nuru.identity;

import com.nuru.domain.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_status")
public class VerificationState {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private VerificationStatus status;

  @Column(length = 80)
  private String provider;

  @Column(name = "provider_reference", length = 200)
  private String providerReference;

  @Column(name = "verified_at")
  private OffsetDateTime verifiedAt;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected VerificationState() {
  }

  public VerificationState(UUID userId, VerificationStatus status) {
    this.userId = userId;
    this.status = status;
  }

  @PrePersist
  void onCreate() {
    updatedAt = OffsetDateTime.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public UUID getUserId() {
    return userId;
  }

  public VerificationStatus getStatus() {
    return status;
  }

  public void setStatus(VerificationStatus status) {
    this.status = status;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getProviderReference() {
    return providerReference;
  }

  public void setProviderReference(String providerReference) {
    this.providerReference = providerReference;
  }

  public OffsetDateTime getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(OffsetDateTime verifiedAt) {
    this.verifiedAt = verifiedAt;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
