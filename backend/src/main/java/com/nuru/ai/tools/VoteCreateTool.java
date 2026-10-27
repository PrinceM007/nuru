package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.VoteDto;
import com.nuru.domain.BallotPolicy;
import com.nuru.service.VoteService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates a draft vote. Runs through proposal + confirm; opening is a separate step. */
@Component
public class VoteCreateTool implements AiTool {

  private final VoteService voteService;

  public VoteCreateTool(VoteService voteService) {
    this.voteService = voteService;
  }

  @Override
  public String name() {
    return "vote_create";
  }

  @Override
  public String description() {
    return "Create a draft group vote with options and a voting window. Requires "
        + "confirmation; the caller needs vote management rights. The vote stays a "
        + "draft until an authorized user opens it.";
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ToolParam> parameters() {
    return List.of(
        new ToolParam("question", ParamType.STRING, true, "Vote question", 500),
        new ToolParam("ballotPolicy", ParamType.STRING, true, "SECRET or ATTRIBUTABLE",
            List.of("SECRET", "ATTRIBUTABLE"), null),
        new ToolParam("opensAt", ParamType.DATETIME, true, "Opening time with offset"),
        new ToolParam("closesAt", ParamType.DATETIME, true, "Closing time with offset"),
        new ToolParam("options", ParamType.STRING_LIST, true, "2-10 distinct option labels"),
        new ToolParam("quorum", ParamType.INTEGER, false, "Minimum ballots for validity"));
  }

  @Override
  @SuppressWarnings("unchecked")
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    VoteDto.Response vote = voteService.create(groupId, callerId,
        (String) args.get("question"), null,
        BallotPolicy.valueOf(((String) args.get("ballotPolicy")).toUpperCase()),
        (OffsetDateTime) args.get("opensAt"), (OffsetDateTime) args.get("closesAt"),
        (Integer) args.get("quorum"), (List<String>) args.get("options"));
    return AiToolResult.of(
        "Draft vote created (" + vote.id() + "). Open it when ready.",
        Map.of("voteId", vote.id().toString(), "status", vote.status()));
  }

  @Override
  public String describeProposal(Map<String, Object> args) {
    return "Create " + args.get("ballotPolicy") + " draft vote: '" + args.get("question")
        + "'.";
  }
}
