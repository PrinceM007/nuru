package com.nuru.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled = true;

  @Column(name = "sms_enabled", nullable = false)
  private boolean smsEnabled;

  @Column(nullable = false, length = 10)
  private String locale = "en";

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected UserPreference() {
  }

  public UserPreference(UUID userId) {
    this.userId = userId;
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

  public boolean isEmailEnabled() {
    return emailEnabled;
  }

  public void setEmailEnabled(boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }

  public boolean isSmsEnabled() {
    return smsEnabled;
  }

  public void setSmsEnabled(boolean smsEnabled) {
    this.smsEnabled = smsEnabled;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
