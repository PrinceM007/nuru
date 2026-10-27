package com.nuru.ai.tools;

import com.nuru.ai.AiTool;
import com.nuru.ai.AiToolResult;
import com.nuru.api.dto.FinanceDto;
import com.nuru.service.ContributionService;
import com.nuru.service.LoanService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Submits a loan request for the caller. Runs through proposal + confirm. */
@Component
public class LoanRequestTool implements AiTool {

  private final LoanService loanService;

  public LoanRequestTool(LoanService loanService) {
    this.loanService = loanService;
  }

  @Override
  public String name() {
    return "loan_request";
  }

  @Override
  public String description() {
    return "Submit a loan request for the current user. Requires confirmation; "
        + "group approval follows the normal loan workflow.";
  }

  @Override
  public boolean requiresConfirmation() {
    return true;
  }

  @Override
  public List<ToolParam> parameters() {
    return List.of(
        new ToolParam("principal", ParamType.NUMBER, true, "Loan principal amount"),
        new ToolParam("termMonths", ParamType.INTEGER, true, "Term in months, 1-60"),
        new ToolParam("interestRate", ParamType.NUMBER, false,
            "Flat interest rate as a fraction, e.g. 0.10 (default 0)"),
        new ToolParam("purpose", ParamType.STRING, false, "Loan purpose", 500));
  }

  @Override
  public AiToolResult execute(UUID callerId, UUID groupId, Map<String, Object> args) {
    AiTool.requireGroup(groupId);
    ContributionService.Created<FinanceDto.Loan> created = loanService.requestLoan(groupId,
        callerId, (BigDecimal) args.get("principal"),
        (BigDecimal) args.get("interestRate"), (Integer) args.get("termMonths"),
        (String) args.get("purpose"), null);
    FinanceDto.Loan loan = created.value();
    return AiToolResult.of(
        "Loan request submitted: total due " + loan.totalDue() + " " + loan.currency() + ".",
        Map.of("loanId", loan.id().toString(), "totalDue", loan.totalDue(),
            "status", loan.status()));
  }

  @Override
  public String describeProposal(Map<String, Object> args) {
    return "Request a loan of " + args.get("principal") + " over "
        + args.get("termMonths") + " months.";
  }
}
