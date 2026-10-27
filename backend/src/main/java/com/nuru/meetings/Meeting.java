package com.nuru.meetings;

import com.nuru.domain.MeetingStatus;
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
@Table(name = "meetings")
public class Meeting {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 2000)
  private String agenda;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "ends_at")
  private OffsetDateTime endsAt;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(length = 500)
  private String location;

  @Column(name = "meeting_link", length = 500)
  private String meetingLink;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MeetingStatus status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Meeting() {
  }

  public Meeting(UUID groupId, String title, String agenda, OffsetDateTime startsAt,
      OffsetDateTime endsAt, String timezone, String location, String meetingLink,
      UUID createdBy) {
    this.groupId = groupId;
    this.title = title;
    this.agenda = agenda;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone;
    this.location = location;
    this.meetingLink = meetingLink;
    this.status = MeetingStatus.SCHEDULED;
    this.createdBy = createdBy;
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

  public UUID getGroupId() {
    return groupId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAgenda() {
    return agenda;
  }

  public void setAgenda(String agenda) {
    this.agenda = agenda;
  }

  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(OffsetDateTime startsAt) {
    this.startsAt = startsAt;
  }

  public OffsetDateTime getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(OffsetDateTime endsAt) {
    this.endsAt = endsAt;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getMeetingLink() {
    return meetingLink;
  }

  public void setMeetingLink(String meetingLink) {
    this.meetingLink = meetingLink;
  }

  public MeetingStatus getStatus() {
    return status;
  }

  public void setStatus(MeetingStatus status) {
    this.status = status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
