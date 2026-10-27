package com.nuru.service;

import com.nuru.api.dto.MeetingDto;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.AttendanceStatus;
import com.nuru.domain.MeetingStatus;
import com.nuru.domain.MembershipStatus;
import com.nuru.domain.Permission;
import com.nuru.groups.MembershipRepository;
import com.nuru.meetings.Meeting;
import com.nuru.meetings.MeetingNote;
import com.nuru.meetings.MeetingNoteRepository;
import com.nuru.meetings.MeetingParticipant;
import com.nuru.meetings.MeetingParticipantRepository;
import com.nuru.meetings.MeetingRepository;
import com.nuru.users.User;
import com.nuru.users.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

  private final MeetingRepository meetings;
  private final MeetingParticipantRepository participants;
  private final MeetingNoteRepository notes;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final GroupAuth groupAuth;
  private final AuditService auditService;
  private final NotificationPublisher notifications;

  public MeetingService(MeetingRepository meetings,
      MeetingParticipantRepository participants, MeetingNoteRepository notes,
      MembershipRepository memberships, UserRepository users, GroupAuth groupAuth,
      AuditService auditService, NotificationPublisher notifications) {
    this.meetings = meetings;
    this.participants = participants;
    this.notes = notes;
    this.memberships = memberships;
    this.users = users;
    this.groupAuth = groupAuth;
    this.auditService = auditService;
    this.notifications = notifications;
  }

  @Transactional
  public MeetingDto.Response create(UUID groupId, UUID callerId, String title, String agenda,
      OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, String location,
      String meetingLink, List<UUID> invitedUserIds) {
    groupAuth.requirePermission(groupId, callerId, Permission.MEETINGS_MANAGE);
    groupAuth.groupOrNotFound(groupId);
    validateTimes(startsAt, endsAt);
    validateTimezone(timezone);
    Meeting meeting = meetings.save(new Meeting(groupId, title.trim(),
        blankToNull(agenda), startsAt, endsAt, timezone.trim(), blankToNull(location),
        blankToNull(meetingLink), callerId));
    Set<UUID> invitees = resolveInvitees(groupId, invitedUserIds);
    for (UUID userId : invitees) {
      participants.save(new MeetingParticipant(meeting.getId(), userId,
          AttendanceStatus.INVITED));
    }
    auditService.record(callerId, groupId, AuditActions.MEETING_CREATED, "Meeting",
        meeting.getId().toString(), "SUCCESS", null);
    String where = meeting.getLocation() != null ? meeting.getLocation()
        : meeting.getMeetingLink() != null ? meeting.getMeetingLink() : "";
    notifications.meetingInvitation(groupId, invitees, meeting.getTitle(),
        meeting.getStartsAt().toString(), meeting.getTimezone(), where);
    return toDto(meeting);
  }

  @Transactional(readOnly = true)
  public Page<MeetingDto.Response> list(UUID groupId, UUID callerId, Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    return meetings.findByGroupIdOrderByStartsAtDesc(groupId, pageable).map(this::toDto);
  }

  @Transactional(readOnly = true)
  public MeetingDto.Response get(UUID groupId, UUID meetingId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    return toDto(meetingInGroup(groupId, meetingId));
  }

  @Transactional
  public MeetingDto.Response update(UUID groupId, UUID meetingId, UUID callerId, String title,
      String agenda, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone,
      String location, String meetingLink) {
    groupAuth.requirePermission(groupId, callerId, Permission.MEETINGS_MANAGE);
    Meeting meeting = meetingInGroup(groupId, meetingId);
    if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
      throw new UnprocessableException("Only scheduled meetings can be updated");
    }
    if (title != null) {
      meeting.setTitle(title.trim());
    }
    if (agenda != null) {
      meeting.setAgenda(blankToNull(agenda));
    }
    if (startsAt != null) {
      meeting.setStartsAt(startsAt);
    }
    if (endsAt != null || startsAt != null) {
      meeting.setEndsAt(endsAt);
    }
    if (timezone != null) {
      validateTimezone(timezone);
      meeting.setTimezone(timezone.trim());
    }
    if (location != null) {
      meeting.setLocation(blankToNull(location));
    }
    if (meetingLink != null) {
      meeting.setMeetingLink(blankToNull(meetingLink));
    }
    validateTimes(meeting.getStartsAt(), meeting.getEndsAt());
    auditService.record(callerId, groupId, AuditActions.MEETING_UPDATED, "Meeting",
        meeting.getId().toString(), "SUCCESS", null);
    notifications.meetingUpdated(groupId,
        participants.findByMeetingId(meeting.getId()).stream()
            .map(MeetingParticipant::getUserId).toList(),
        meeting.getTitle(), meeting.getStartsAt().toString(), meeting.getTimezone());
    return toDto(meeting);
  }

  @Transactional
  public MeetingDto.Response complete(UUID groupId, UUID meetingId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.MEETINGS_MANAGE);
    Meeting meeting = meetingInGroup(groupId, meetingId);
    if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
      throw new UnprocessableException("Only scheduled meetings can be completed");
    }
    meeting.setStatus(MeetingStatus.COMPLETED);
    auditService.record(callerId, groupId, AuditActions.MEETING_UPDATED, "Meeting",
        meeting.getId().toString(), "SUCCESS", "status=COMPLETED");
    return toDto(meeting);
  }

  @Transactional
  public MeetingDto.Response cancel(UUID groupId, UUID meetingId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.MEETINGS_MANAGE);
    Meeting meeting = meetingInGroup(groupId, meetingId);
    if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
      throw new UnprocessableException("Only scheduled meetings can be cancelled");
    }
    meeting.setStatus(MeetingStatus.CANCELLED);
    auditService.record(callerId, groupId, AuditActions.MEETING_CANCELLED, "Meeting",
        meeting.getId().toString(), "SUCCESS", null);
    return toDto(meeting);
  }

  @Transactional
  public MeetingDto.Participant markAttendance(UUID groupId, UUID meetingId, UUID targetUserId,
      UUID callerId, AttendanceStatus attendance) {
    groupAuth.requireMembership(groupId, callerId);
    Meeting meeting = meetingInGroup(groupId, meetingId);
    if (meeting.getStatus() == MeetingStatus.CANCELLED) {
      throw new UnprocessableException("Cannot record attendance for a cancelled meeting");
    }
    boolean manager = groupAuth.hasPermission(groupId, callerId, Permission.MEETINGS_MANAGE);
    if (!targetUserId.equals(callerId) && !manager) {
      throw new ForbiddenOperationException("Can only mark your own attendance");
    }
    MeetingParticipant participant = participants
        .findByMeetingIdAndUserId(meetingId, targetUserId)
        .orElseThrow(() -> new ResourceNotFoundException("User is not invited to this meeting"));
    participant.setAttendance(attendance);
    auditService.record(callerId, groupId, AuditActions.ATTENDANCE_RECORDED, "Meeting",
        meeting.getId().toString(), "SUCCESS",
        "user=" + targetUserId + ",attendance=" + attendance);
    return toParticipantDto(participant);
  }

  @Transactional
  public MeetingDto.Note addNote(UUID groupId, UUID meetingId, UUID callerId, String body) {
    groupAuth.requireMembership(groupId, callerId);
    Meeting meeting = meetingInGroup(groupId, meetingId);
    if (meeting.getStatus() == MeetingStatus.CANCELLED) {
      throw new UnprocessableException("Cannot add notes to a cancelled meeting");
    }
    if (body == null || body.trim().isEmpty()) {
      throw new UnprocessableException("Note body is required");
    }
    MeetingNote note = notes.save(new MeetingNote(meetingId, callerId, body.trim()));
    auditService.record(callerId, groupId, AuditActions.MEETING_UPDATED, "MeetingNote",
        note.getId().toString(), "SUCCESS", "meeting=" + meetingId);
    return toNoteDto(note);
  }

  @Transactional
  public MeetingDto.Note updateNote(UUID groupId, UUID meetingId, UUID noteId, UUID callerId,
      String body) {
    groupAuth.requireMembership(groupId, callerId);
    meetingInGroup(groupId, meetingId);
    MeetingNote note = noteInMeeting(meetingId, noteId);
    boolean manager = groupAuth.hasPermission(groupId, callerId, Permission.MEETINGS_MANAGE);
    if (!note.getAuthorUserId().equals(callerId) && !manager) {
      throw new ForbiddenOperationException("Only the author or a manager can edit this note");
    }
    note.setBody(body.trim());
    return toNoteDto(note);
  }

  @Transactional
  public void deleteNote(UUID groupId, UUID meetingId, UUID noteId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    meetingInGroup(groupId, meetingId);
    MeetingNote note = noteInMeeting(meetingId, noteId);
    boolean manager = groupAuth.hasPermission(groupId, callerId, Permission.MEETINGS_MANAGE);
    if (!note.getAuthorUserId().equals(callerId) && !manager) {
      throw new ForbiddenOperationException("Only the author or a manager can delete this note");
    }
    notes.delete(note);
  }

  private Meeting meetingInGroup(UUID groupId, UUID meetingId) {
    Meeting meeting = meetings.findById(meetingId)
        .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
    if (!meeting.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Meeting not found in this group");
    }
    return meeting;
  }

  private MeetingNote noteInMeeting(UUID meetingId, UUID noteId) {
    MeetingNote note = notes.findById(noteId)
        .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
    if (!note.getMeetingId().equals(meetingId)) {
      throw new ResourceNotFoundException("Note not found for this meeting");
    }
    return note;
  }

  private Set<UUID> resolveInvitees(UUID groupId, List<UUID> invitedUserIds) {
    if (invitedUserIds == null || invitedUserIds.isEmpty()) {
      // Default: snapshot of all currently active members.
      return memberships.findByGroupIdAndStatus(groupId, MembershipStatus.ACTIVE).stream()
          .map(m -> m.getUserId()).collect(Collectors.toSet());
    }
    Set<UUID> active = memberships.findByGroupIdAndStatus(groupId, MembershipStatus.ACTIVE)
        .stream().map(m -> m.getUserId()).collect(Collectors.toSet());
    Set<UUID> invitees = new HashSet<>(invitedUserIds);
    for (UUID userId : invitees) {
      if (!active.contains(userId)) {
        throw new UnprocessableException("Invitee is not an active member: " + userId);
      }
    }
    return invitees;
  }

  private void validateTimes(OffsetDateTime startsAt, OffsetDateTime endsAt) {
    if (startsAt == null) {
      throw new UnprocessableException("Start time is required");
    }
    if (endsAt != null && !endsAt.isAfter(startsAt)) {
      throw new UnprocessableException("End time must be after the start time");
    }
  }

  private void validateTimezone(String timezone) {
    try {
      ZoneId.of(timezone.trim());
    } catch (Exception e) {
      throw new UnprocessableException("Unknown timezone: " + timezone);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private MeetingDto.Response toDto(Meeting meeting) {
    List<MeetingParticipant> invitees = participants.findByMeetingId(meeting.getId());
    Map<UUID, String> names = users.findAllById(
        invitees.stream().map(MeetingParticipant::getUserId).toList()).stream()
        .collect(Collectors.toMap(User::getId, User::getDisplayName));
    List<MeetingDto.Participant> participantDtos = invitees.stream()
        .map(p -> new MeetingDto.Participant(p.getUserId(),
            names.getOrDefault(p.getUserId(), "Unknown member"), p.getAttendance().name()))
        .toList();
    List<MeetingDto.Note> noteDtos = notes.findByMeetingIdOrderByCreatedAt(meeting.getId())
        .stream().map(this::toNoteDto).toList();
    return new MeetingDto.Response(meeting.getId(), meeting.getGroupId(), meeting.getTitle(),
        meeting.getAgenda(), meeting.getStartsAt(), meeting.getEndsAt(), meeting.getTimezone(),
        meeting.getLocation(), meeting.getMeetingLink(), meeting.getStatus().name(),
        participantDtos, noteDtos, meeting.getCreatedAt());
  }

  private MeetingDto.Participant toParticipantDto(MeetingParticipant participant) {
    String name = users.findById(participant.getUserId()).map(User::getDisplayName)
        .orElse("Unknown member");
    return new MeetingDto.Participant(participant.getUserId(), name,
        participant.getAttendance().name());
  }

  private MeetingDto.Note toNoteDto(MeetingNote note) {
    String name = users.findById(note.getAuthorUserId()).map(User::getDisplayName)
        .orElse("Unknown member");
    return new MeetingDto.Note(note.getId(), note.getAuthorUserId(), name, note.getBody(),
        note.getCreatedAt(), note.getUpdatedAt());
  }
}
