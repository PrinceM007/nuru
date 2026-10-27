package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.FinanceDto;
import com.nuru.service.LedgerService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Explains the recorded ledger summary. Figures are facts from persisted rows;
 * the interpretation is labeled guidance, not financial advice.
 */
@Component
public class LedgerExplainTool implements AiTool {

  private final LedgerService ledgerService;

  public LedgerExplainTool(LedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @Override
  public String name() {
    return "ledger_explain";
  }

  @Override
  public String description() {
    return "Read the group ledger summary (expected vs confirmed funds, loans, net "
        + "balance) and explain what each recorded figure means.";
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
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("currency", summary.currency());
    facts.put("expectedTotal", summary.expectedTotal());
    facts.put("confirmedContributionsTotal", summary.confirmedContributionsTotal());
    facts.put("loansDisbursedTotal", summary.loansDisbursedTotal());
    facts.put("loansRepaidTotal", summary.loansRepaidTotal());
    facts.put("loansOutstandingTotal", summary.loansOutstandingTotal());
    facts.put("netBalance", summary.netBalance());
    facts.put("asOf", summary.asOf().toString());
    String explanation = "Recorded facts as of " + summary.asOf() + " (" + summary.currency()
        + "): expected contributions " + summary.expectedTotal() + " (assessed or reported but "
        + "not yet confirmed), confirmed contributions " + summary.confirmedContributionsTotal()
        + ", loans disbursed " + summary.loansDisbursedTotal() + ", loans repaid "
        + summary.loansRepaidTotal() + ", loans outstanding "
        + summary.loansOutstandingTotal() + ". Net recorded balance "
        + summary.netBalance() + " = confirmed + repaid − disbursed. "
        + "Guidance (not a recorded fact): manually recorded entries reflect what members "
        + "reported — verify real-world funds separately. This is bookkeeping information, "
        + "not financial advice.";
    return AiToolResult.of(explanation, facts);
  }
}
