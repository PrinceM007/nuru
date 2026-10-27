package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.MeetingDto;
import com.nuru.service.MeetingService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Reads group meetings the caller is allowed to see. Read-only. */
@Component
public class MeetingInfoTool implements AiTool {

  private final MeetingService meetingService;

  public MeetingInfoTool(MeetingService meetingService) {
    this.meetingService = meetingService;
  }

  @Override
  public String name() {
    return "meeting_info";
  }

  @Override
  public String description() {
    return "List group meetings visible to the current user, newest first. "
        + "Set upcomingOnly=true to show only meetings that have not started yet.";
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public List<ToolParam> parameters() {
    return List.of(new ToolParam("upcomingOnly", ParamType.BOOLEAN, false,
        "Only include meetings starting in the future"));
  }

  @Override
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    boolean upcomingOnly = Boolean.TRUE.equals(args.get("upcomingOnly"));
    var page = meetingService.list(groupId, callerId, Pageable.ofSize(20));
    List<Map<String, Object>> rows = new ArrayList<>();
    OffsetDateTime now = OffsetDateTime.now();
    for (MeetingDto.Response meeting : page.getContent()) {
      if (upcomingOnly && meeting.startsAt().isBefore(now)) {
        continue;
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", meeting.id().toString());
      item.put("title", meeting.title());
      item.put("startsAt", meeting.startsAt().toString());
      item.put("timezone", meeting.timezone());
      item.put("location", meeting.location());
      item.put("meetingLink", meeting.meetingLink());
      item.put("status", meeting.status());
      rows.add(item);
      if (rows.size() >= 10) {
        break;
      }
    }
    return AiToolResult.of(
        rows.isEmpty() ? "No meetings found." : "Found " + rows.size() + " meeting(s).",
        Map.of("meetings", rows));
  }
}
