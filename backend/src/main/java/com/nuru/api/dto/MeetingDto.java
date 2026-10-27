package com.nuru.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MeetingDto {

  private MeetingDto() {
  }

  public record CreateRequest(
      @NotBlank @Size(min = 1, max = 200) String title,
      @Size(max = 2000) String agenda,
      @NotNull OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      @NotBlank @Size(max = 64) String timezone,
      @Size(max = 500) String location,
      @Size(max = 500) String meetingLink,
      List<UUID> invitedUserIds) {
  }

  public record UpdateRequest(
      @Size(min = 1, max = 200) String title,
      @Size(max = 2000) String agenda,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      @Size(max = 64) String timezone,
      @Size(max = 500) String location,
      @Size(max = 500) String meetingLink) {
  }

  public record AttendanceRequest(@NotNull String attendance) {
  }

  public record NoteRequest(@NotBlank @Size(min = 1, max = 20000) String body) {
  }

  public record Participant(UUID userId, String displayName, String attendance) {
  }

  public record Note(UUID id, UUID authorUserId, String authorDisplayName, String body,
      OffsetDateTime createdAt, OffsetDateTime updatedAt) {
  }

  public record Response(UUID id, UUID groupId, String title, String agenda,
      OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, String location,
      String meetingLink, String status, List<Participant> participants, List<Note> notes,
      OffsetDateTime createdAt) {
  }
}
