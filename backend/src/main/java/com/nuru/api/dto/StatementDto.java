package com.nuru.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class StatementDto {

  private StatementDto() {
  }

  public record PolicyRequest(@NotNull String requestType, @NotNull Boolean approvalRequired) {
  }

  public record Policy(String requestType, boolean approvalRequired) {
  }

  public record CreateRequest(
      @NotNull String requestType,
      @NotNull LocalDate periodStart,
      @NotNull LocalDate periodEnd) {
  }

  public record RejectRequest(@Size(max = 500) String reason) {
  }

  public record Request(UUID id, UUID groupId, UUID requesterUserId, String requestType,
      LocalDate periodStart, LocalDate periodEnd, String status, UUID reviewerUserId,
      OffsetDateTime decidedAt, String rejectionReason, OffsetDateTime createdAt) {
    public static Request from(com.nuru.statements.StatementRequest request) {
      return new Request(request.getId(), request.getGroupId(), request.getRequesterUserId(),
          request.getRequestType().name(), request.getPeriodStart(), request.getPeriodEnd(),
          request.getStatus().name(), request.getReviewerUserId(), request.getDecidedAt(),
          request.getRejectionReason(), request.getCreatedAt());
    }
  }

  public record ContributionLine(LocalDate periodStart, LocalDate periodEnd, BigDecimal amount,
      String status, String recordedVia) {
  }

  public record LoanLine(UUID loanId, BigDecimal principal, BigDecimal totalDue,
      BigDecimal outstanding, String status) {
  }

  public record GroupTotals(BigDecimal expectedTotal, BigDecimal confirmedContributionsTotal,
      BigDecimal loansDisbursedTotal, BigDecimal loansRepaidTotal,
      BigDecimal loansOutstandingTotal, BigDecimal netBalance, int activeMembers) {
  }

  public record Document(UUID requestId, String requestType, String currency,
      LocalDate periodStart, LocalDate periodEnd, OffsetDateTime generatedAt, String title,
      List<ContributionLine> contributions, List<LoanLine> loans,
      BigDecimal expectedTotal, BigDecimal confirmedTotal, BigDecimal borrowedTotal,
      BigDecimal loansOutstandingTotal, GroupTotals groupTotals) {
  }
}
