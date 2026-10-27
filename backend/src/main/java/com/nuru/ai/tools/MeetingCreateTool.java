package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.MeetingDto;
import com.nuru.service.MeetingService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates a group meeting. Consequential: always runs through proposal + confirm. */
@Component
public class MeetingCreateTool implements AiTool {

  private final MeetingService meetingService;

  public MeetingCreateTool(MeetingService meetingService) {
    this.meetingService = meetingService;
  }

  @Override
  public String name() {
    return "meeting_create";
  }

  @Override
  public String description() {
    return "Create a group meeting with title, start time, timezone, and optional "
        + "details. Requires confirmation; the caller needs meeting management rights.";
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public List<ToolParam> parameters() {
    return List.of(
        new ToolParam("title", ParamType.STRING, true, "Meeting title", 200),
        new ToolParam("startsAt", ParamType.DATETIME, true,
            "Start time with offset, e.g. 2026-11-02T10:00:00+03:00"),
        new ToolParam("timezone", ParamType.STRING, true,
            "IANA timezone, e.g. Africa/Nairobi", 64),
        new ToolParam("endsAt", ParamType.DATETIME, false, "End time with offset"),
        new ToolParam("location", ParamType.STRING, false, "Physical location", 500),
        new ToolParam("meetingLink", ParamType.STRING, false, "Video meeting link", 500),
        new ToolParam("agenda", ParamType.STRING, false, "Agenda text", 2000));
  }

  @Override
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    MeetingDto.Response meeting = meetingService.create(groupId, callerId,
        (String) args.get("title"), (String) args.get("agenda"),
        (OffsetDateTime) args.get("startsAt"), (OffsetDateTime) args.get("endsAt"),
        (String) args.get("timezone"), (String) args.get("location"),
        (String) args.get("meetingLink"), null);
    return AiToolResult.of(
        "Meeting '" + meeting.title() + "' created for " + meeting.startsAt() + ".",
        Map.of("meetingId", meeting.id().toString(), "title", meeting.title(),
            "startsAt", meeting.startsAt().toString()));
  }

  @Override
  public String describeProposal(Map<String, Object> args) {
    return "Create meeting '" + args.get("title") + "' at " + args.get("startsAt")
        + " (" + args.get("timezone") + ").";
  }
}
