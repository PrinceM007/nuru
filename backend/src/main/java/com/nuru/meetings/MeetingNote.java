package com.nuru.meetings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_notes")
public class MeetingNote {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "meeting_id", nullable = false)
  private UUID meetingId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
  private String body;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected MeetingNote() {
  }

  public MeetingNote(UUID meetingId, UUID authorUserId, String body) {
    this.meetingId = meetingId;
    this.authorUserId = authorUserId;
    this.body = body;
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

  public UUID getMeetingId() {
    return meetingId;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
