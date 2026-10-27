package com.nuru.service;

import com.nuru.api.dto.FinanceDto;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.InstallmentStatus;
import com.nuru.domain.LedgerKind;
import com.nuru.domain.LoanStatus;
import com.nuru.domain.Money;
import com.nuru.domain.Permission;
import com.nuru.domain.RepaymentStatus;
import com.nuru.finance.Loan;
import com.nuru.finance.LoanRepository;
import com.nuru.finance.Repayment;
import com.nuru.finance.RepaymentRepository;
import com.nuru.finance.RepaymentSchedule;
import com.nuru.finance.RepaymentScheduleRepository;
import com.nuru.groups.SavingsGroup;
import com.nuru.provider.payment.PaymentService;
import com.nuru.provider.payment.ProviderPayment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

  private final LoanRepository loans;
  private final RepaymentRepository repayments;
  private final RepaymentScheduleRepository schedules;
  private final GroupAuth groupAuth;
  private final LedgerService ledgerService;
  private final PaymentService paymentService;
  private final AuditService auditService;
  private final NotificationPublisher notifications;

  public LoanService(LoanRepository loans, RepaymentRepository repayments,
      RepaymentScheduleRepository schedules, GroupAuth groupAuth, LedgerService ledgerService,
      PaymentService paymentService, AuditService auditService,
      NotificationPublisher notifications) {
    this.loans = loans;
    this.repayments = repayments;
    this.schedules = schedules;
    this.groupAuth = groupAuth;
    this.ledgerService = ledgerService;
    this.paymentService = paymentService;
    this.auditService = auditService;
    this.notifications = notifications;
  }

  @Transactional
  public ContributionService.Created<FinanceDto.Loan> requestLoan(UUID groupId, UUID callerId,
      BigDecimal principal, BigDecimal interestRate, int termMonths, String purpose,
      String idempotencyKey) {
    groupAuth.requireMembership(groupId, callerId);
    SavingsGroup group = groupAuth.groupOrNotFound(groupId);
    try {
      Money.requirePositive(principal, "principal");
      Money.requireNonNegative(interestRate == null ? BigDecimal.ZERO : interestRate,
          "interestRate");
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException(e.getMessage());
    }
    BigDecimal rate = interestRate == null ? BigDecimal.ZERO : interestRate;
    if (rate.compareTo(BigDecimal.ONE) > 0) {
      throw new UnprocessableException("Interest rate must not exceed 1.0 (100% flat)");
    }
    if (termMonths < 1 || termMonths > 60) {
      throw new UnprocessableException("Term must be between 1 and 60 months");
    }
    String key = normalizeKey(idempotencyKey);
    if (key != null) {
      var existing = loans.findByGroupIdAndIdempotencyKey(groupId, key);
      if (existing.isPresent()) {
        return new ContributionService.Created<>(toDto(existing.get()), false);
      }
    }
    BigDecimal totalDue = principal.multiply(BigDecimal.ONE.add(rate))
        .setScale(4, RoundingMode.HALF_EVEN);
    Loan loan = new Loan(groupId, callerId, principal, group.getCurrency(), rate, totalDue,
        termMonths, purpose == null ? null : purpose.trim(), callerId, key);
    try {
      loan = loans.saveAndFlush(loan);
    } catch (DataIntegrityViolationException e) {
      if (key != null) {
        var winner = loans.findByGroupIdAndIdempotencyKey(groupId, key);
        if (winner.isPresent()) {
          return new ContributionService.Created<>(toDto(winner.get()), false);
        }
      }
      throw e;
    }
    auditService.record(callerId, groupId, AuditActions.LOAN_REQUESTED, "Loan",
        loan.getId().toString(), "SUCCESS", "principal=" + principal);
    return new ContributionService.Created<>(toDto(loan), true);
  }

  @Transactional(readOnly = true)
  public Page<FinanceDto.Loan> listLoans(UUID groupId, UUID callerId, Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    if (canSeeAllLoans(groupId, callerId)) {
      return loans.findByGroupId(groupId, pageable).map(this::toDto);
    }
    return loans.findByGroupIdAndBorrowerUserId(groupId, callerId, pageable).map(this::toDto);
  }

  @Transactional(readOnly = true)
  public FinanceDto.Loan getLoan(UUID groupId, UUID loanId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    Loan loan = loanInGroup(groupId, loanId);
    ensureLoanVisible(groupId, callerId, loan);
    return toDto(loan);
  }

  @Transactional
  public FinanceDto.Loan approve(UUID groupId, UUID loanId, UUID callerId, String reason) {
    groupAuth.requirePermission(groupId, callerId, Permission.LOANS_APPROVE);
    Loan loan = loanInGroup(groupId, loanId);
    if (loan.getBorrowerUserId().equals(callerId)) {
      throw new ForbiddenOperationException("Cannot approve your own loan request");
    }
    if (loan.getStatus() != LoanStatus.REQUESTED) {
      throw new UnprocessableException("Only requested loans can be approved");
    }
    loan.setStatus(LoanStatus.APPROVED);
    decide(loan, callerId, reason);
    auditService.record(callerId, groupId, AuditActions.LOAN_DECIDED, "Loan",
        loan.getId().toString(), "SUCCESS", "decision=APPROVED");
    notifications.loanDecided(groupId, loan.getBorrowerUserId(), true,
        loan.getAmountPrincipal().toPlainString(), loan.getCurrency(), reason);
    return toDto(loan);
  }

  @Transactional
  public FinanceDto.Loan reject(UUID groupId, UUID loanId, UUID callerId, String reason) {
    groupAuth.requirePermission(groupId, callerId, Permission.LOANS_APPROVE);
    Loan loan = loanInGroup(groupId, loanId);
    if (loan.getBorrowerUserId().equals(callerId)) {
      throw new ForbiddenOperationException("Cannot decide your own loan request");
    }
    if (loan.getStatus() != LoanStatus.REQUESTED) {
      throw new UnprocessableException("Only requested loans can be rejected");
    }
    if (reason == null || reason.trim().isEmpty()) {
      throw new UnprocessableException("A rejection reason is required");
    }
    loan.setStatus(LoanStatus.REJECTED);
    decide(loan, callerId, reason);
    auditService.record(callerId, groupId, AuditActions.LOAN_DECIDED, "Loan",
        loan.getId().toString(), "SUCCESS", "decision=REJECTED");
    notifications.loanDecided(groupId, loan.getBorrowerUserId(), false,
        loan.getAmountPrincipal().toPlainString(), loan.getCurrency(), reason);
    return toDto(loan);
  }

  @Transactional
  public FinanceDto.Loan cancel(UUID groupId, UUID loanId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    Loan loan = loanInGroup(groupId, loanId);
    if (!loan.getBorrowerUserId().equals(callerId)) {
      throw new ForbiddenOperationException("Only the borrower can cancel");
    }
    if (loan.getStatus() != LoanStatus.REQUESTED) {
      throw new UnprocessableException("Only requested loans can be cancelled");
    }
    loan.setStatus(LoanStatus.CANCELLED);
    return toDto(loan);
  }

  /**
   * Records disbursement of an approved loan: writes the ledger outflow and generates
   * the repayment schedule. This records the group's decision — actual fund movement
   * happens outside Nuru until a live payment provider is integrated.
   */
  @Transactional
  public FinanceDto.Loan disburse(UUID groupId, UUID loanId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.LOANS_APPROVE);
    Loan loan = loanInGroup(groupId, loanId);
    if (loan.getBorrowerUserId().equals(callerId)) {
      throw new ForbiddenOperationException("Cannot disburse your own loan");
    }
    if (loan.getStatus() != LoanStatus.APPROVED) {
      throw new UnprocessableException("Only approved loans can be disbursed");
    }
    loan.setStatus(LoanStatus.ACTIVE);
    loan.setDisbursedAt(OffsetDateTime.now());
    ledgerService.append(groupId, LedgerKind.LOAN_DISBURSEMENT, loan.getId(),
        loan.getBorrowerUserId(), loan.getAmountPrincipal().negate(), loan.getCurrency(),
        OffsetDateTime.now());
    List<BigDecimal> parts = Money.splitEvenly(loan.getTotalDue(), loan.getTermMonths());
    LocalDate due = LocalDate.now().plusMonths(1);
    for (int i = 0; i < parts.size(); i++) {
      schedules.save(new RepaymentSchedule(loan.getId(), i + 1, due.plusMonths(i), parts.get(i)));
    }
    auditService.record(callerId, groupId, AuditActions.LOAN_DECIDED, "Loan",
        loan.getId().toString(), "SUCCESS", "decision=DISBURSED");
    return toDto(loan);
  }

  @Transactional
  public FinanceDto.Loan markDefaulted(UUID groupId, UUID loanId, UUID callerId, String reason) {
    groupAuth.requirePermission(groupId, callerId, Permission.LOANS_APPROVE);
    Loan loan = loanInGroup(groupId, loanId);
    if (loan.getStatus() != LoanStatus.ACTIVE) {
      throw new UnprocessableException("Only active loans can be marked defaulted");
    }
    if (reason == null || reason.trim().isEmpty()) {
      throw new UnprocessableException("A reason is required");
    }
    loan.setStatus(LoanStatus.DEFAULTED);
    loan.setDecisionReason(reason.trim());
    auditService.record(callerId, groupId, AuditActions.LOAN_DECIDED, "Loan",
        loan.getId().toString(), "SUCCESS", "decision=DEFAULTED");
    return toDto(loan);
  }

  @Transactional
  public FinanceDto.Loan reactivate(UUID groupId, UUID loanId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.LOANS_APPROVE);
    Loan loan = loanInGroup(groupId, loanId);
    if (loan.getStatus() != LoanStatus.DEFAULTED) {
      throw new UnprocessableException("Only defaulted loans can be reactivated");
    }
    loan.setStatus(LoanStatus.ACTIVE);
    auditService.record(callerId, groupId, AuditActions.LOAN_DECIDED, "Loan",
        loan.getId().toString(), "SUCCESS", "decision=REACTIVATED");
    return toDto(loan);
  }

  @Transactional(readOnly = true)
  public List<FinanceDto.Installment> schedule(UUID groupId, UUID loanId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    Loan loan = loanInGroup(groupId, loanId);
    ensureLoanVisible(groupId, callerId, loan);
    return schedules.findByLoanIdOrderByInstallmentNo(loanId).stream()
        .map(FinanceDto.Installment::from).toList();
  }

  // --- repayments ---

  @Transactional
  public ContributionService.Created<FinanceDto.Repayment> submitRepayment(UUID groupId,
      UUID loanId, UUID callerId, BigDecimal amount, String idempotencyKey) {
    groupAuth.requireMembership(groupId, callerId);
    Loan loan = loanInGroup(groupId, loanId);
    if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.DEFAULTED) {
      throw new UnprocessableException("Repayments need an active loan");
    }
    boolean confirmer = groupAuth.hasPermission(groupId, callerId,
        Permission.CONTRIBUTIONS_CONFIRM);
    if (!loan.getBorrowerUserId().equals(callerId) && !confirmer) {
      throw new ForbiddenOperationException("Only the borrower or a treasurer can repay");
    }
    try {
      Money.requirePositive(amount, "amount");
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException(e.getMessage());
    }
    BigDecimal outstanding = outstandingBalance(loan);
    if (amount.compareTo(outstanding) > 0) {
      throw new UnprocessableException(
          "Repayment exceeds the outstanding balance of " + outstanding);
    }
    String key = normalizeKey(idempotencyKey);
    if (key != null) {
      var existing = repayments.findByLoanIdAndIdempotencyKey(loanId, key);
      if (existing.isPresent()) {
        return new ContributionService.Created<>(FinanceDto.Repayment.from(existing.get()), false);
      }
    }
    ProviderPayment payment = paymentService.recordInbound(groupId, loan.getBorrowerUserId(),
        amount, loan.getCurrency(), "loan repayment");
    Repayment repayment = new Repayment(loanId, groupId, amount, loan.getCurrency(),
        loan.getBorrowerUserId(), callerId, payment.recordedVia(), payment.reference(), key);
    try {
      repayment = repayments.saveAndFlush(repayment);
    } catch (DataIntegrityViolationException e) {
      if (key != null) {
        var winner = repayments.findByLoanIdAndIdempotencyKey(loanId, key);
        if (winner.isPresent()) {
          return new ContributionService.Created<>(FinanceDto.Repayment.from(winner.get()),
              false);
        }
      }
      throw e;
    }
    auditService.record(callerId, groupId, AuditActions.LOAN_REPAID, "Repayment",
        repayment.getId().toString(), "SUCCESS", "submitted amount=" + amount);
    return new ContributionService.Created<>(FinanceDto.Repayment.from(repayment), true);
  }

  @Transactional(readOnly = true)
  public Page<FinanceDto.Repayment> listRepayments(UUID groupId, UUID loanId, UUID callerId,
      Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    Loan loan = loanInGroup(groupId, loanId);
    ensureLoanVisible(groupId, callerId, loan);
    return repayments.findByLoanId(loanId, pageable).map(FinanceDto.Repayment::from);
  }

  @Transactional
  public FinanceDto.Repayment confirmRepayment(UUID groupId, UUID loanId, UUID repaymentId,
      UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.CONTRIBUTIONS_CONFIRM);
    Loan loan = loanInGroup(groupId, loanId);
    Repayment repayment = repaymentInLoan(loanId, repaymentId);
    if (repayment.getStatus() != RepaymentStatus.SUBMITTED) {
      throw new UnprocessableException("Only submitted repayments can be confirmed");
    }
    repayment.setStatus(RepaymentStatus.CONFIRMED);
    repayment.setConfirmedBy(callerId);
    repayment.setConfirmedAt(OffsetDateTime.now());
    ledgerService.append(groupId, LedgerKind.LOAN_REPAYMENT, repayment.getId(),
        repayment.getPayerUserId(), repayment.getAmount(), repayment.getCurrency(),
        OffsetDateTime.now());
    applyToSchedule(loan, repayment.getAmount());
    if (outstandingBalance(loan).compareTo(BigDecimal.ZERO) <= 0
        && loan.getStatus() == LoanStatus.ACTIVE) {
      loan.setStatus(LoanStatus.REPAID);
    }
    auditService.record(callerId, groupId, AuditActions.LOAN_REPAID, "Repayment",
        repayment.getId().toString(), "SUCCESS", "confirmed amount=" + repayment.getAmount());
    notifications.repaymentConfirmed(groupId, repayment.getPayerUserId(),
        repayment.getAmount().toPlainString(), repayment.getCurrency(),
        outstandingBalance(loan).toPlainString());
    return FinanceDto.Repayment.from(repayment);
  }

  @Transactional
  public FinanceDto.Repayment rejectRepayment(UUID groupId, UUID loanId, UUID repaymentId,
      UUID callerId, String reason) {
    groupAuth.requirePermission(groupId, callerId, Permission.CONTRIBUTIONS_CONFIRM);
    loanInGroup(groupId, loanId);
    Repayment repayment = repaymentInLoan(loanId, repaymentId);
    if (repayment.getStatus() != RepaymentStatus.SUBMITTED) {
      throw new UnprocessableException("Only submitted repayments can be rejected");
    }
    repayment.setStatus(RepaymentStatus.REJECTED);
    repayment.setRejectionReason(reason == null ? null : reason.trim());
    return FinanceDto.Repayment.from(repayment);
  }

  private void applyToSchedule(Loan loan, BigDecimal amount) {
    BigDecimal remaining = amount;
    for (RepaymentSchedule installment : schedules.findByLoanIdOrderByInstallmentNo(
        loan.getId())) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }
      BigDecimal due = installment.remaining();
      if (due.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      BigDecimal applied = remaining.min(due);
      installment.setAmountPaid(installment.getAmountPaid().add(applied));
      remaining = remaining.subtract(applied);
      installment.setStatus(installment.remaining().compareTo(BigDecimal.ZERO) <= 0
          ? InstallmentStatus.PAID : InstallmentStatus.PARTIAL);
    }
  }

  private BigDecimal outstandingBalance(Loan loan) {
    BigDecimal repaid = repayments.sumByLoanAndStatus(loan.getId(), RepaymentStatus.CONFIRMED);
    return loan.getTotalDue().subtract(repaid);
  }

  private void decide(Loan loan, UUID callerId, String reason) {
    loan.setDecidedBy(callerId);
    loan.setDecidedAt(OffsetDateTime.now());
    loan.setDecisionReason(reason == null ? null : reason.trim());
  }

  private Loan loanInGroup(UUID groupId, UUID loanId) {
    Loan loan = loans.findById(loanId)
        .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
    if (!loan.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Loan not found in this group");
    }
    return loan;
  }

  private Repayment repaymentInLoan(UUID loanId, UUID repaymentId) {
    Repayment repayment = repayments.findById(repaymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Repayment not found"));
    if (!repayment.getLoanId().equals(loanId)) {
      throw new ResourceNotFoundException("Repayment not found for this loan");
    }
    return repayment;
  }

  private boolean canSeeAllLoans(UUID groupId, UUID callerId) {
    return groupAuth.hasAnyPermission(groupId, callerId, Permission.LOANS_APPROVE,
        Permission.LEDGER_VIEW_FULL);
  }

  private void ensureLoanVisible(UUID groupId, UUID callerId, Loan loan) {
    if (loan.getBorrowerUserId().equals(callerId) || canSeeAllLoans(groupId, callerId)) {
      return;
    }
    throw new ForbiddenOperationException("Cannot view another member's loan");
  }

  private FinanceDto.Loan toDto(Loan loan) {
    BigDecimal repaid = repayments.sumByLoanAndStatus(loan.getId(), RepaymentStatus.CONFIRMED);
    return FinanceDto.Loan.from(loan, loan.getTotalDue().subtract(repaid));
  }

  private String normalizeKey(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    if (key.length() > 64) {
      throw new UnprocessableException("Idempotency key must be at most 64 characters");
    }
    return key.trim();
  }
}
