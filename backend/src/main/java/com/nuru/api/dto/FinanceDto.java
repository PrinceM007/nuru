package com.nuru.api.dto;

import com.nuru.finance.Contribution;
import com.nuru.finance.ContributionSchedule;
import com.nuru.finance.LedgerEntry;
import com.nuru.finance.Loan;
import com.nuru.finance.Repayment;
import com.nuru.finance.RepaymentSchedule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class FinanceDto {

  private FinanceDto() {
  }

  public record CreateScheduleRequest(
      @NotNull @Size(min = 1, max = 160) String title,
      @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
      @NotNull String frequency,
      @Min(1) @Max(31) Integer dueDay,
      @NotNull LocalDate startDate,
      LocalDate endDate) {
  }

  public record Schedule(UUID id, String title, BigDecimal amount, String currency,
      String frequency, Integer dueDay, LocalDate startDate, LocalDate endDate,
      OffsetDateTime createdAt) {
    public static Schedule from(ContributionSchedule schedule) {
      return new Schedule(schedule.getId(), schedule.getTitle(), schedule.getAmount(),
          schedule.getCurrency(), schedule.getFrequency().name(), schedule.getDueDay(),
          schedule.getStartDate(), schedule.getEndDate(), schedule.getCreatedAt());
    }
  }

  public record RecordContributionRequest(
      UUID memberUserId,
      UUID scheduleId,
      @NotNull LocalDate periodStart,
      @NotNull LocalDate periodEnd,
      @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
      boolean assessment,
      @Size(max = 1000) String notes) {
  }

  public record UpdateContributionRequest(
      @DecimalMin(value = "0.01") BigDecimal amount,
      LocalDate periodStart,
      LocalDate periodEnd,
      @Size(max = 1000) String notes) {
  }

  public record ConfirmContributionRequest(OffsetDateTime occurredAt) {
  }

  public record RejectRequest(@Size(max = 500) String reason) {
  }

  public record AdjustContributionRequest(
      @NotNull BigDecimal amount,
      @NotNull @Size(min = 1, max = 1000) String reason) {
  }

  public record Contribution(UUID id, UUID groupId, UUID scheduleId, UUID memberUserId,
      LocalDate periodStart, LocalDate periodEnd, BigDecimal amount, String currency,
      String status, UUID recordedBy, String recordedVia, String paymentReference,
      UUID confirmedBy, OffsetDateTime confirmedAt, String rejectionReason, UUID correctionOf,
      String notes, OffsetDateTime createdAt, List<Contribution> adjustments) {
    public static Contribution from(com.nuru.finance.Contribution contribution,
        List<Contribution> adjustments) {
      return new Contribution(contribution.getId(), contribution.getGroupId(),
          contribution.getScheduleId(), contribution.getMemberUserId(),
          contribution.getPeriodStart(), contribution.getPeriodEnd(), contribution.getAmount(),
          contribution.getCurrency(), contribution.getStatus().name(),
          contribution.getRecordedBy(), contribution.getRecordedVia().name(),
          contribution.getPaymentReference(), contribution.getConfirmedBy(),
          contribution.getConfirmedAt(), contribution.getRejectionReason(),
          contribution.getCorrectionOf(), contribution.getNotes(), contribution.getCreatedAt(),
          adjustments);
    }
  }

  public record LedgerSummary(UUID groupId, String currency, BigDecimal expectedTotal,
      BigDecimal confirmedContributionsTotal, BigDecimal loansDisbursedTotal,
      BigDecimal loansRepaidTotal, BigDecimal loansOutstandingTotal, BigDecimal netBalance,
      BigDecimal myExpectedTotal, BigDecimal myConfirmedTotal, OffsetDateTime asOf) {
  }

  public record LedgerEntry(Long id, String kind, UUID referenceId, UUID memberUserId,
      BigDecimal amount, String currency, OffsetDateTime occurredAt, OffsetDateTime createdAt) {
    public static LedgerEntry from(com.nuru.finance.LedgerEntry entry) {
      return new LedgerEntry(entry.getId(), entry.getKind().name(), entry.getReferenceId(),
          entry.getMemberUserId(), entry.getAmount(), entry.getCurrency(),
          entry.getOccurredAt(), entry.getCreatedAt());
    }
  }

  public record RequestLoanRequest(
      @NotNull @DecimalMin(value = "0.01") BigDecimal principal,
      @DecimalMin(value = "0.0") BigDecimal interestRate,
      @Min(1) @Max(60) int termMonths,
      @Size(max = 500) String purpose) {
  }

  public record DecideLoanRequest(@Size(max = 500) String reason) {
  }

  public record Loan(UUID id, UUID groupId, UUID borrowerUserId, BigDecimal principal,
      String currency, BigDecimal interestRate, BigDecimal totalDue, BigDecimal outstandingBalance,
      int termMonths, String purpose, String status, OffsetDateTime requestedAt, UUID decidedBy,
      OffsetDateTime decidedAt, String decisionReason, OffsetDateTime disbursedAt) {
    public static Loan from(com.nuru.finance.Loan loan, BigDecimal outstandingBalance) {
      return new Loan(loan.getId(), loan.getGroupId(), loan.getBorrowerUserId(),
          loan.getAmountPrincipal(), loan.getCurrency(), loan.getInterestRate(),
          loan.getTotalDue(), outstandingBalance, loan.getTermMonths(), loan.getPurpose(),
          loan.getStatus().name(), loan.getRequestedAt(), loan.getDecidedBy(),
          loan.getDecidedAt(), loan.getDecisionReason(), loan.getDisbursedAt());
    }
  }

  public record Installment(int installmentNo, LocalDate dueDate, BigDecimal amountDue,
      BigDecimal amountPaid, String status) {
    public static Installment from(RepaymentSchedule installment) {
      return new Installment(installment.getInstallmentNo(), installment.getDueDate(),
          installment.getAmountDue(), installment.getAmountPaid(),
          installment.getStatus().name());
    }
  }

  public record SubmitRepaymentRequest(@NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
  }

  public record Repayment(UUID id, UUID loanId, BigDecimal amount, String currency,
      UUID payerUserId, String status, String recordedVia, String paymentReference,
      String rejectionReason, OffsetDateTime createdAt, OffsetDateTime confirmedAt) {
    public static Repayment from(com.nuru.finance.Repayment repayment) {
      return new Repayment(repayment.getId(), repayment.getLoanId(), repayment.getAmount(),
          repayment.getCurrency(), repayment.getPayerUserId(), repayment.getStatus().name(),
          repayment.getRecordedVia().name(), repayment.getPaymentReference(),
          repayment.getRejectionReason(), repayment.getCreatedAt(), repayment.getConfirmedAt());
    }
  }
}
