package com.nuru.api;

import com.nuru.api.dto.MeetingDto;
import com.nuru.api.error.UnprocessableException;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.domain.AttendanceStatus;
import com.nuru.service.MeetingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/meetings")
@Tag(name = "Meetings")
public class MeetingController {

  private final MeetingService meetingService;

  public MeetingController(MeetingService meetingService) {
    this.meetingService = meetingService;
  }

  @PostMapping
  public ResponseEntity<MeetingDto.Response> create(@PathVariable UUID groupId,
      @Valid @RequestBody MeetingDto.CreateRequest request) {
    MeetingDto.Response meeting = meetingService.create(groupId, currentUser(),
        request.title(), request.agenda(), request.startsAt(), request.endsAt(),
        request.timezone(), request.location(), request.meetingLink(),
        request.invitedUserIds());
    return ResponseEntity.status(HttpStatus.CREATED).body(meeting);
  }

  @GetMapping
  public Page<MeetingDto.Response> list(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return meetingService.list(groupId, currentUser(), pageable);
  }

  @GetMapping("/{meetingId}")
  public MeetingDto.Response get(@PathVariable UUID groupId, @PathVariable UUID meetingId) {
    return meetingService.get(groupId, meetingId, currentUser());
  }

  @PatchMapping("/{meetingId}")
  public MeetingDto.Response update(@PathVariable UUID groupId, @PathVariable UUID meetingId,
      @Valid @RequestBody MeetingDto.UpdateRequest request) {
    return meetingService.update(groupId, meetingId, currentUser(), request.title(),
        request.agenda(), request.startsAt(), request.endsAt(), request.timezone(),
        request.location(), request.meetingLink());
  }

  @PostMapping("/{meetingId}/complete")
  public MeetingDto.Response complete(@PathVariable UUID groupId,
      @PathVariable UUID meetingId) {
    return meetingService.complete(groupId, meetingId, currentUser());
  }

  @PostMapping("/{meetingId}/cancel")
  public MeetingDto.Response cancel(@PathVariable UUID groupId, @PathVariable UUID meetingId) {
    return meetingService.cancel(groupId, meetingId, currentUser());
  }

  @PostMapping("/{meetingId}/attendance/{userId}")
  public MeetingDto.Participant markAttendance(@PathVariable UUID groupId,
      @PathVariable UUID meetingId, @PathVariable UUID userId,
      @Valid @RequestBody MeetingDto.AttendanceRequest request) {
    AttendanceStatus attendance;
    try {
      attendance = AttendanceStatus.valueOf(request.attendance().trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown attendance status: " + request.attendance());
    }
    return meetingService.markAttendance(groupId, meetingId, userId, currentUser(), attendance);
  }

  @PostMapping("/{meetingId}/notes")
  public ResponseEntity<MeetingDto.Note> addNote(@PathVariable UUID groupId,
      @PathVariable UUID meetingId, @Valid @RequestBody MeetingDto.NoteRequest request) {
    MeetingDto.Note note = meetingService.addNote(groupId, meetingId, currentUser(),
        request.body());
    return ResponseEntity.status(HttpStatus.CREATED).body(note);
  }

  @PatchMapping("/{meetingId}/notes/{noteId}")
  public MeetingDto.Note updateNote(@PathVariable UUID groupId, @PathVariable UUID meetingId,
      @PathVariable UUID noteId, @Valid @RequestBody MeetingDto.NoteRequest request) {
    return meetingService.updateNote(groupId, meetingId, noteId, currentUser(), request.body());
  }

  @DeleteMapping("/{meetingId}/notes/{noteId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteNote(@PathVariable UUID groupId, @PathVariable UUID meetingId,
      @PathVariable UUID noteId) {
    meetingService.deleteNote(groupId, meetingId, noteId, currentUser());
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
