package com.nuru.provider.credit;

import com.nuru.domain.ContributionStatus;
import com.nuru.domain.LoanStatus;
import com.nuru.domain.RepaymentStatus;
import com.nuru.finance.Contribution;
import com.nuru.finance.ContributionRepository;
import com.nuru.finance.Loan;
import com.nuru.finance.LoanRepository;
import com.nuru.finance.RepaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Transparent group-history estimator (0–100). Inputs are ONLY the member's own
 * recorded contributions and loan repayments in this group — no protected or
 * unrelated characteristics. Every factor, weight, and the data period are
 * returned with the score. Labeled an estimate; never wired into decisions.
 */
@Component
public class GroupHistoryCreditProvider implements CreditScoreProvider {

  private final ContributionRepository contributions;
  private final LoanRepository loans;
  private final RepaymentRepository repayments;

  public GroupHistoryCreditProvider(ContributionRepository contributions, LoanRepository loans,
      RepaymentRepository repayments) {
    this.contributions = contributions;
    this.loans = loans;
    this.repayments = repayments;
  }

  @Override
  public String name() {
    return "GROUP_HISTORY";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public CreditEstimate estimate(UUID groupId, UUID memberUserId) {
    List<Contribution> rows = contributions
        .findByGroupIdAndMemberUserId(groupId, memberUserId, Pageable.unpaged()).getContent();
    List<Loan> memberLoans = loans.findByGroupIdAndBorrowerUserId(groupId, memberUserId)
        .stream()
        .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.REPAID
            || l.getStatus() == LoanStatus.DEFAULTED)
        .toList();

    if (rows.isEmpty() && memberLoans.isEmpty()) {
      return new CreditEstimate(true, null,
          List.of(new Factor("history_present", "none", 0.0)), null, null,
          OffsetDateTime.now(), estimateLabel());
    }

    long confirmedCount = rows.stream().filter(c -> c.getStatus()
        == ContributionStatus.CONFIRMED).count();
    double contributionRate = rows.isEmpty() ? 0.0
        : (double) confirmedCount / rows.size();

    BigDecimal totalDue = BigDecimal.ZERO;
    BigDecimal totalRepaid = BigDecimal.ZERO;
    for (Loan loan : memberLoans) {
      totalDue = totalDue.add(loan.getTotalDue());
      totalRepaid = totalRepaid.add(
          repayments.sumByLoanAndStatus(loan.getId(), RepaymentStatus.CONFIRMED));
    }
    double repaymentRate = totalDue.compareTo(BigDecimal.ZERO) <= 0 ? 1.0
        : totalRepaid.divide(totalDue, 4, RoundingMode.HALF_EVEN).doubleValue();

    double score;
    List<Factor> factors = new ArrayList<>();
    factors.add(new Factor("contribution_confirmation_rate",
        String.format("%.2f", contributionRate), 0.6));
    if (memberLoans.isEmpty()) {
      score = contributionRate * 100.0;
      factors.add(new Factor("loan_repayment_rate", "no loans", 0.0));
    } else {
      score = (0.6 * contributionRate + 0.4 * repaymentRate) * 100.0;
      factors.add(new Factor("loan_repayment_rate",
          String.format("%.2f", repaymentRate), 0.4));
    }

    LocalDate periodStart = rows.stream().map(Contribution::getPeriodStart)
        .min(LocalDate::compareTo).orElse(null);
    LocalDate periodEnd = rows.stream().map(Contribution::getPeriodEnd)
        .max(LocalDate::compareTo).orElse(null);
    return new CreditEstimate(false, (int) Math.round(Math.min(100.0, Math.max(0.0, score))),
        factors, periodStart, periodEnd, OffsetDateTime.now(), estimateLabel());
  }

  private String estimateLabel() {
    return "ESTIMATE from this group's records only — not a bureau score, never used "
        + "automatically in decisions. Correct source records or request human review "
        + "via your group admin if you believe the inputs are wrong.";
  }
}
