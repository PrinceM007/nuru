package com.nuru.notifications;

import com.nuru.domain.NotificationChannel;
import com.nuru.domain.NotificationKind;
import com.nuru.domain.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Column(name = "group_id")
  private UUID groupId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private NotificationChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 60)
  private NotificationKind kind;

  @Column(length = 200)
  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationStatus status;

  @Column(length = 80)
  private String provider;

  @Column(name = "provider_reference", length = 200)
  private String providerReference;

  @Column(name = "error_detail", length = 500)
  private String errorDetail;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Notification() {
  }

  public Notification(UUID recipientUserId, UUID groupId, NotificationChannel channel,
      NotificationKind kind, String subject, NotificationStatus status) {
    this.recipientUserId = recipientUserId;
    this.groupId = groupId;
    this.channel = channel;
    this.kind = kind;
    this.subject = subject;
    this.status = status;
  }

  @PrePersist
  void onCreate() {
    createdAt = OffsetDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  public NotificationKind getKind() {
    return kind;
  }

  public String getSubject() {
    return subject;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
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

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
