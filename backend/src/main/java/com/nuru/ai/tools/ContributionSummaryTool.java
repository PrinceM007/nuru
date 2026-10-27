package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.FinanceDto;
import com.nuru.service.ContributionService;
import com.nuru.service.LedgerService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Reads the caller's own authorized contribution summary. Read-only. */
@Component
public class ContributionSummaryTool implements AiTool {

  private final ContributionService contributions;
  private final LedgerService ledgerService;

  public ContributionSummaryTool(ContributionService contributions, LedgerService ledgerService) {
    this.contributions = contributions;
    this.ledgerService = ledgerService;
  }

  @Override
  public String name() {
    return "contribution_summary";
  }

  @Override
  public String description() {
    return "Read the current user's own contribution totals (expected vs confirmed) "
        + "and recent contribution rows for the conversation's group.";
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public List<ToolParam> parameters() {
    return List.of();
  }

  @Override
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    FinanceDto.LedgerSummary summary = ledgerService.summary(groupId, callerId);
    var recent = contributions.listContributions(groupId, callerId, callerId, null,
        Pageable.ofSize(5));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (FinanceDto.Contribution row : recent.getContent()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("periodStart", row.periodStart().toString());
      item.put("periodEnd", row.periodEnd().toString());
      item.put("amount", row.amount());
      item.put("status", row.status());
      item.put("recordedVia", row.recordedVia());
      rows.add(item);
    }
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("currency", summary.currency());
    facts.put("myExpectedTotal", summary.myExpectedTotal());
    facts.put("myConfirmedTotal", summary.myConfirmedTotal());
    facts.put("recent", rows);
    return AiToolResult.of(
        "Your recorded contributions: expected " + summary.myExpectedTotal() + " "
            + summary.currency() + ", confirmed " + summary.myConfirmedTotal() + " "
            + summary.currency() + ".",
        facts);
  }
}
