package com.nuru.service;

import com.nuru.api.dto.FinanceDto;
import com.nuru.api.error.ConflictException;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.ContributionStatus;
import com.nuru.domain.LedgerKind;
import com.nuru.domain.Money;
import com.nuru.domain.Permission;
import com.nuru.domain.ScheduleFrequency;
import com.nuru.finance.Contribution;
import com.nuru.finance.ContributionRepository;
import com.nuru.finance.ContributionSchedule;
import com.nuru.finance.ContributionScheduleRepository;
import com.nuru.groups.SavingsGroup;
import com.nuru.provider.payment.PaymentService;
import com.nuru.provider.payment.ProviderPayment;
import java.math.BigDecimal;
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
public class ContributionService {

  private final ContributionRepository contributions;
  private final ContributionScheduleRepository schedules;
  private final GroupAuth groupAuth;
  private final LedgerService ledgerService;
  private final PaymentService paymentService;
  private final AuditService auditService;
  private final NotificationPublisher notifications;

  public ContributionService(ContributionRepository contributions,
      ContributionScheduleRepository schedules, GroupAuth groupAuth, LedgerService ledgerService,
      PaymentService paymentService, AuditService auditService,
      NotificationPublisher notifications) {
    this.contributions = contributions;
    this.schedules = schedules;
    this.groupAuth = groupAuth;
    this.ledgerService = ledgerService;
    this.paymentService = paymentService;
    this.auditService = auditService;
    this.notifications = notifications;
  }

  public record Created<T>(T value, boolean created) {
  }

  // --- schedules ---

  @Transactional
  public FinanceDto.Schedule createSchedule(UUID groupId, UUID callerId, String title,
      BigDecimal amount, ScheduleFrequency frequency, Integer dueDay, LocalDate startDate,
      LocalDate endDate) {
    groupAuth.requirePermission(groupId, callerId, Permission.CONTRIBUTIONS_CONFIRM);
    SavingsGroup group = groupAuth.groupOrNotFound(groupId);
    try {
      Money.requirePositive(amount, "amount");
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException(e.getMessage());
    }
    if (endDate != null && endDate.isBefore(startDate)) {
      throw new UnprocessableException("Schedule end date must not precede the start date");
    }
    ContributionSchedule schedule = schedules.save(new ContributionSchedule(groupId,
        title.trim(), amount, group.getCurrency(), frequency, dueDay, startDate, endDate,
        callerId));
    auditService.record(callerId, groupId, AuditActions.CONTRIBUTION_RECORDED,
        "ContributionSchedule", schedule.getId().toString(), "SUCCESS", "schedule created");
    return FinanceDto.Schedule.from(schedule);
  }

  @Transactional(readOnly = true)
  public Page<FinanceDto.Schedule> listSchedules(UUID groupId, UUID callerId, Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    return schedules.findByGroupId(groupId, pageable).map(FinanceDto.Schedule::from);
  }

  // --- contributions ---

  /**
   * Records a contribution. {@code assessment} distinguishes treasurer-posted expectations
   * (needs CONTRIBUTIONS_CONFIRM) from member payment reports. Idempotent on
   * {@code idempotencyKey} when provided.
   */
  @Transactional
  public Created<FinanceDto.Contribution> recordContribution(UUID groupId, UUID callerId,
      UUID memberUserId, UUID scheduleId, LocalDate periodStart, LocalDate periodEnd,
      BigDecimal amount, boolean assessment, String notes, String idempotencyKey) {
    groupAuth.requireMembership(groupId, callerId);
    SavingsGroup group = groupAuth.groupOrNotFound(groupId);
    UUID member = memberUserId == null ? callerId : memberUserId;
    boolean confirmer = groupAuth.hasPermission(groupId, callerId,
        Permission.CONTRIBUTIONS_CONFIRM);
    if (assessment && !confirmer) {
      throw new ForbiddenOperationException("Posting assessments needs confirmation rights");
    }
    if (!member.equals(callerId) && !confirmer) {
      throw new ForbiddenOperationException("Cannot record for another member");
    }
    if (scheduleId != null) {
      ContributionSchedule schedule = schedules.findById(scheduleId)
          .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
      if (!schedule.getGroupId().equals(groupId)) {
        throw new ResourceNotFoundException("Schedule not found in this group");
      }
    }
    try {
      Money.requirePositive(amount, "amount");
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException(e.getMessage());
    }
    if (periodEnd.isBefore(periodStart)) {
      throw new UnprocessableException("Period end must not precede period start");
    }
    String key = normalizeKey(idempotencyKey);
    if (key != null) {
      var existing = contributions.findByGroupIdAndIdempotencyKey(groupId, key);
      if (existing.isPresent()) {
        return new Created<>(FinanceDto.Contribution.from(existing.get(), List.of()), false);
      }
    }
    ProviderPayment payment = paymentService.recordInbound(groupId, member, amount,
        group.getCurrency(), assessment ? "assessment" : "member payment report");
    ContributionStatus status =
        assessment ? ContributionStatus.EXPECTED : ContributionStatus.SUBMITTED;
    Contribution contribution = new Contribution(groupId, scheduleId, member, periodStart,
        periodEnd, amount, group.getCurrency(), status, callerId, payment.recordedVia(),
        payment.reference(), null, notes == null ? null : notes.trim(), key);
    try {
      contribution = contributions.saveAndFlush(contribution);
    } catch (DataIntegrityViolationException e) {
      // Lost an idempotency race: return the winner's row instead of erroring.
      if (key != null) {
        var winner = contributions.findByGroupIdAndIdempotencyKey(groupId, key);
        if (winner.isPresent()) {
          return new Created<>(FinanceDto.Contribution.from(winner.get(), List.of()), false);
        }
      }
      throw e;
    }
    auditService.record(callerId, groupId, AuditActions.CONTRIBUTION_RECORDED, "Contribution",
        contribution.getId().toString(), "SUCCESS",
        "status=" + status + ",via=" + payment.recordedVia());
    return new Created<>(FinanceDto.Contribution.from(contribution, List.of()), true);
  }

  @Transactional(readOnly = true)
  public Page<FinanceDto.Contribution> listContributions(UUID groupId, UUID callerId,
      UUID memberFilter, String statusFilter, Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    boolean fullView = groupAuth.hasPermission(groupId, callerId, Permission.LEDGER_VIEW_FULL);
    if (memberFilter != null && !memberFilter.equals(callerId) && !fullView) {
      throw new ForbiddenOperationException("Cannot view another member's contributions");
    }
    if (!fullView) {
      memberFilter = callerId;
    }
    ContributionStatus status = statusFilter == null ? null : parseStatus(statusFilter);
    Page<Contribution> page;
    if (memberFilter != null && status != null) {
      page = contributions.findByGroupIdAndMemberUserIdAndStatus(groupId, memberFilter,
          status, pageable);
    } else if (memberFilter != null) {
      page = contributions.findByGroupIdAndMemberUserId(groupId, memberFilter, pageable);
    } else if (status != null) {
      page = contributions.findByGroupIdAndStatus(groupId, status, pageable);
    } else {
      page = contributions.findByGroupId(groupId, pageable);
    }
    return page.map(c -> FinanceDto.Contribution.from(c, List.of()));
  }

  @Transactional(readOnly = true)
  public FinanceDto.Contribution getContribution(UUID groupId, UUID contributionId,
      UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    Contribution contribution = contributionInGroup(groupId, contributionId);
    ensureVisible(groupId, callerId, contribution);
    List<FinanceDto.Contribution> adjustments = contributions
        .findByCorrectionOf(contribution.getId()).stream()
        .map(c -> FinanceDto.Contribution.from(c, List.of())).toList();
    return FinanceDto.Contribution.from(contribution, adjustments);
  }

  /** Edits an UNCONFIRMED row. Confirmed rows are immutable — use adjust(). */
  @Transactional
  public FinanceDto.Contribution updateUnconfirmed(UUID groupId, UUID contributionId,
      UUID callerId, BigDecimal amount, LocalDate periodStart, LocalDate periodEnd,
      String notes) {
    groupAuth.requireMembership(groupId, callerId);
    Contribution contribution = contributionInGroup(groupId, contributionId);
    if (contribution.isConfirmed()) {
      throw new UnprocessableException(
          "Confirmed contributions are immutable; post an adjustment instead");
    }
    boolean confirmer = groupAuth.hasPermission(groupId, callerId,
        Permission.CONTRIBUTIONS_CONFIRM);
    if (!contribution.getRecordedBy().equals(callerId) && !confirmer) {
      throw new ForbiddenOperationException("Only the recorder or a treasurer can edit");
    }
    if (amount != null) {
      try {
        Money.requirePositive(amount, "amount");
      } catch (IllegalArgumentException e) {
        throw new UnprocessableException(e.getMessage());
      }
      contribution.setAmount(amount);
    }
    if (periodStart != null) {
      contribution.setPeriodStart(periodStart);
    }
    if (periodEnd != null) {
      contribution.setPeriodEnd(periodEnd);
    }
    if (contribution.getPeriodEnd().isBefore(contribution.getPeriodStart())) {
      throw new UnprocessableException("Period end must not precede period start");
    }
    if (notes != null) {
      contribution.setNotes(notes.trim().isEmpty() ? null : notes.trim());
    }
    return FinanceDto.Contribution.from(contribution, List.of());
  }

  @Transactional
  public FinanceDto.Contribution confirm(UUID groupId, UUID contributionId, UUID callerId,
      OffsetDateTime occurredAt) {
    groupAuth.requirePermission(groupId, callerId, Permission.CONTRIBUTIONS_CONFIRM);
    Contribution contribution = contributionInGroup(groupId, contributionId);
    if (contribution.getStatus() != ContributionStatus.EXPECTED
        && contribution.getStatus() != ContributionStatus.SUBMITTED) {
      throw new UnprocessableException("Only expected or submitted contributions can be confirmed");
    }
    if (occurredAt != null && occurredAt.isAfter(OffsetDateTime.now().plusMinutes(5))) {
      throw new UnprocessableException("Confirmation date must not be in the future");
    }
    contribution.setStatus(ContributionStatus.CONFIRMED);
    contribution.setConfirmedBy(callerId);
    contribution.setConfirmedAt(OffsetDateTime.now());
    OffsetDateTime occurred = occurredAt == null ? OffsetDateTime.now() : occurredAt;
    ledgerService.append(groupId, LedgerKind.CONTRIBUTION, contribution.getId(),
        contribution.getMemberUserId(), contribution.getAmount(), contribution.getCurrency(),
        occurred);
    auditService.record(callerId, groupId, AuditActions.CONTRIBUTION_CONFIRMED, "Contribution",
        contribution.getId().toString(), "SUCCESS", "amount=" + contribution.getAmount());
    notifications.contributionConfirmed(groupId, contribution.getMemberUserId(),
        contribution.getAmount().toPlainString(), contribution.getCurrency(),
        contribution.getPeriodStart() + " to " + contribution.getPeriodEnd());
    return FinanceDto.Contribution.from(contribution, List.of());
  }

  @Transactional
  public FinanceDto.Contribution reject(UUID groupId, UUID contributionId, UUID callerId,
      String reason) {
    groupAuth.requirePermission(groupId, callerId, Permission.CONTRIBUTIONS_CONFIRM);
    Contribution contribution = contributionInGroup(groupId, contributionId);
    if (contribution.getStatus() != ContributionStatus.EXPECTED
        && contribution.getStatus() != ContributionStatus.SUBMITTED) {
      throw new UnprocessableException("Only expected or submitted contributions can be rejected");
    }
    contribution.setStatus(ContributionStatus.REJECTED);
    contribution.setRejectionReason(reason == null ? null : reason.trim());
    return FinanceDto.Contribution.from(contribution, List.of());
  }

  @Transactional
  public FinanceDto.Contribution cancel(UUID groupId, UUID contributionId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    Contribution contribution = contributionInGroup(groupId, contributionId);
    if (contribution.isConfirmed()) {
      throw new UnprocessableException("Confirmed contributions cannot be cancelled");
    }
    boolean confirmer = groupAuth.hasPermission(groupId, callerId,
        Permission.CONTRIBUTIONS_CONFIRM);
    if (!contribution.getRecordedBy().equals(callerId) && !confirmer) {
      throw new ForbiddenOperationException("Only the recorder or a treasurer can cancel");
    }
    contribution.setStatus(ContributionStatus.CANCELLED);
    return FinanceDto.Contribution.from(contribution, List.of());
  }

  /**
   * Posts an auditable adjustment (delta, may be negative) against a CONFIRMED row.
   * The original row is never modified; the adjustment links back to it.
   */
  @Transactional
  public FinanceDto.Contribution adjust(UUID groupId, UUID contributionId, UUID callerId,
      BigDecimal delta, String reason) {
    groupAuth.requirePermission(groupId, callerId, Permission.CONTRIBUTIONS_CONFIRM);
    Contribution original = contributionInGroup(groupId, contributionId);
    if (!original.isConfirmed()) {
      throw new UnprocessableException("Only confirmed contributions can be adjusted");
    }
    try {
      Money.requireNonZero(delta, "amount");
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException(e.getMessage());
    }
    if (reason == null || reason.trim().isEmpty()) {
      throw new UnprocessableException("An adjustment reason is required");
    }
    Contribution adjustment = new Contribution(groupId, original.getScheduleId(),
        original.getMemberUserId(), original.getPeriodStart(), original.getPeriodEnd(), delta,
        original.getCurrency(), ContributionStatus.CONFIRMED, callerId,
        original.getRecordedVia(), original.getPaymentReference(), original.getId(),
        reason.trim(), null);
    adjustment.setConfirmedBy(callerId);
    adjustment.setConfirmedAt(OffsetDateTime.now());
    adjustment = contributions.save(adjustment);
    ledgerService.append(groupId, LedgerKind.CONTRIBUTION_ADJUSTMENT, adjustment.getId(),
        original.getMemberUserId(), delta, original.getCurrency(), OffsetDateTime.now());
    auditService.record(callerId, groupId, AuditActions.CONTRIBUTION_CORRECTED, "Contribution",
        adjustment.getId().toString(), "SUCCESS", "corrects=" + original.getId());
    return FinanceDto.Contribution.from(adjustment, List.of());
  }

  private Contribution contributionInGroup(UUID groupId, UUID contributionId) {
    Contribution contribution = contributions.findById(contributionId)
        .orElseThrow(() -> new ResourceNotFoundException("Contribution not found"));
    if (!contribution.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Contribution not found in this group");
    }
    return contribution;
  }

  private void ensureVisible(UUID groupId, UUID callerId, Contribution contribution) {
    if (contribution.getMemberUserId().equals(callerId)) {
      return;
    }
    if (!groupAuth.hasPermission(groupId, callerId, Permission.LEDGER_VIEW_FULL)) {
      throw new ForbiddenOperationException("Cannot view another member's contributions");
    }
  }

  private ContributionStatus parseStatus(String status) {
    try {
      return ContributionStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("Unknown contribution status: " + status);
    }
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
