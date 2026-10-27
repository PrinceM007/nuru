package com.nuru.meetings;

import com.nuru.domain.AttendanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "meeting_participants")
public class MeetingParticipant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "meeting_id", nullable = false)
  private UUID meetingId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AttendanceStatus attendance;

  protected MeetingParticipant() {
  }

  public MeetingParticipant(UUID meetingId, UUID userId, AttendanceStatus attendance) {
    this.meetingId = meetingId;
    this.userId = userId;
    this.attendance = attendance;
  }

  public Long getId() {
    return id;
  }

  public UUID getMeetingId() {
    return meetingId;
  }

  public UUID getUserId() {
    return userId;
  }

  public AttendanceStatus getAttendance() {
    return attendance;
  }

  public void setAttendance(AttendanceStatus attendance) {
    this.attendance = attendance;
  }
}
