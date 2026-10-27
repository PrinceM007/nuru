package com.nuru.service;

import com.nuru.api.dto.FinanceDto;
import com.nuru.domain.ContributionStatus;
import com.nuru.domain.LedgerKind;
import com.nuru.domain.LoanStatus;
import com.nuru.domain.Permission;
import com.nuru.finance.ContributionRepository;
import com.nuru.finance.LedgerEntry;
import com.nuru.finance.LedgerEntryRepository;
import com.nuru.finance.LoanRepository;
import com.nuru.finance.RepaymentRepository;
import com.nuru.groups.SavingsGroup;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger journal + summaries. The journal is the immutable record of confirmed
 * movements; expectations come from unconfirmed contribution rows. Every figure
 * is recomputed from persisted rows, so results are always reproducible.
 */
@Service
public class LedgerService {

  private static final List<ContributionStatus> EXPECTED_STATUSES =
      List.of(ContributionStatus.EXPECTED, ContributionStatus.SUBMITTED);
  private static final List<ContributionStatus> CONFIRMED_STATUSES =
      List.of(ContributionStatus.CONFIRMED);

  private static final List<LoanStatus> OPEN_LOAN_STATUSES =
      List.of(LoanStatus.ACTIVE, LoanStatus.DEFAULTED, LoanStatus.REPAID);

  private final LedgerEntryRepository entries;
  private final ContributionRepository contributions;
  private final LoanRepository loans;
  private final RepaymentRepository repayments;
  private final GroupAuth groupAuth;

  public LedgerService(LedgerEntryRepository entries, ContributionRepository contributions,
      LoanRepository loans, RepaymentRepository repayments, GroupAuth groupAuth) {
    this.entries = entries;
    this.contributions = contributions;
    this.loans = loans;
    this.repayments = repayments;
    this.groupAuth = groupAuth;
  }

  /** Called by finance services inside their own transactions. Never called directly by UI/AI. */
  @Transactional
  public void append(UUID groupId, LedgerKind kind, UUID referenceId, UUID memberUserId,
      BigDecimal amount, String currency, OffsetDateTime occurredAt) {
    entries.save(new LedgerEntry(groupId, kind, referenceId, memberUserId, amount, currency,
        occurredAt == null ? OffsetDateTime.now() : occurredAt));
  }

  @Transactional(readOnly = true)
  public FinanceDto.LedgerSummary summary(UUID groupId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    SavingsGroup group = groupAuth.groupOrNotFound(groupId);
    BigDecimal expected = contributions.sumByGroupAndStatuses(groupId, EXPECTED_STATUSES);
    BigDecimal confirmed = contributions.sumByGroupAndStatuses(groupId, CONFIRMED_STATUSES);
    BigDecimal disbursed = entries.sumByGroupAndKinds(groupId,
        List.of(LedgerKind.LOAN_DISBURSEMENT)).negate();
    BigDecimal repaid = entries.sumByGroupAndKinds(groupId, List.of(LedgerKind.LOAN_REPAYMENT));
    // Outstanding = total due (principal + flat interest) minus confirmed repayments,
    // across disbursed loans. Repaid loans net to zero by construction.
    BigDecimal outstanding = loans.sumTotalDueByGroupAndStatuses(groupId, OPEN_LOAN_STATUSES)
        .subtract(
            repayments.sumConfirmedByGroupAndLoanStatuses(groupId, OPEN_LOAN_STATUSES));
    BigDecimal netBalance = confirmed.add(repaid).subtract(disbursed);
    BigDecimal myConfirmed = contributions.sumByGroupMemberAndStatuses(groupId, callerId,
        CONFIRMED_STATUSES);
    BigDecimal myExpected = contributions.sumByGroupMemberAndStatuses(groupId, callerId,
        EXPECTED_STATUSES);
    return new FinanceDto.LedgerSummary(
        groupId, group.getCurrency(), expected, confirmed, disbursed, repaid, outstanding,
        netBalance, myExpected, myConfirmed, OffsetDateTime.now());
  }

  @Transactional(readOnly = true)
  public Page<FinanceDto.LedgerEntry> listEntries(UUID groupId, UUID callerId,
      Pageable pageable) {
    groupAuth.requirePermission(groupId, callerId, Permission.LEDGER_VIEW_FULL);
    return entries.findByGroupIdOrderByOccurredAtDesc(groupId, pageable)
        .map(FinanceDto.LedgerEntry::from);
  }
}
