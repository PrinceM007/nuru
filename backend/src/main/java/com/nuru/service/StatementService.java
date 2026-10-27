package com.nuru.service;

import com.nuru.api.dto.StatementDto;
import com.nuru.api.error.ForbiddenOperationException;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.domain.ContributionStatus;
import com.nuru.domain.MembershipStatus;
import com.nuru.domain.Permission;
import com.nuru.domain.RepaymentStatus;
import com.nuru.domain.StatementRequestStatus;
import com.nuru.domain.StatementRequestType;
import com.nuru.finance.Contribution;
import com.nuru.finance.ContributionRepository;
import com.nuru.finance.Loan;
import com.nuru.finance.LoanRepository;
import com.nuru.finance.RepaymentRepository;
import com.nuru.groups.MembershipRepository;
import com.nuru.groups.SavingsGroup;
import com.nuru.statements.StatementPolicy;
import com.nuru.statements.StatementPolicyRepository;
import com.nuru.statements.StatementRequest;
import com.nuru.statements.StatementRequestRepository;
import com.nuru.users.User;
import com.nuru.users.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {

  private final StatementRequestRepository requests;
  private final StatementPolicyRepository policies;
  private final ContributionRepository contributions;
  private final LoanRepository loans;
  private final RepaymentRepository repayments;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final GroupAuth groupAuth;
  private final LedgerService ledgerService;
  private final AuditService auditService;

  public StatementService(StatementRequestRepository requests,
      StatementPolicyRepository policies, ContributionRepository contributions,
      LoanRepository loans, RepaymentRepository repayments, MembershipRepository memberships,
      UserRepository users, GroupAuth groupAuth, LedgerService ledgerService,
      AuditService auditService) {
    this.requests = requests;
    this.policies = policies;
    this.contributions = contributions;
    this.loans = loans;
    this.repayments = repayments;
    this.memberships = memberships;
    this.users = users;
    this.groupAuth = groupAuth;
    this.ledgerService = ledgerService;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<StatementDto.Policy> listPolicies(UUID groupId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    List<StatementDto.Policy> result = new ArrayList<>();
    for (StatementRequestType type : StatementRequestType.values()) {
      result.add(new StatementDto.Policy(type.name(), approvalRequired(groupId, type)));
    }
    return result;
  }

  @Transactional
  public StatementDto.Policy configurePolicy(UUID groupId, UUID callerId,
      StatementRequestType type, boolean approvalRequired) {
    groupAuth.requirePermission(groupId, callerId, Permission.APPROVAL_POLICIES_MANAGE);
    groupAuth.groupOrNotFound(groupId);
    StatementPolicy policy = policies.findByGroupIdAndRequestType(groupId, type)
        .orElseGet(() -> policies.save(new StatementPolicy(groupId, type, approvalRequired,
            callerId)));
    policy.setApprovalRequired(approvalRequired);
    policy.setUpdatedBy(callerId);
    return new StatementDto.Policy(type.name(), approvalRequired);
  }

  @Transactional
  public StatementDto.Request request(UUID groupId, UUID callerId, StatementRequestType type,
      LocalDate periodStart, LocalDate periodEnd) {
    groupAuth.requireMembership(groupId, callerId);
    if (periodEnd.isBefore(periodStart)) {
      throw new UnprocessableException("Period end must not precede period start");
    }
    boolean needsApproval = approvalRequired(groupId, type);
    StatementRequest request = requests.save(new StatementRequest(groupId, callerId, type,
        periodStart, periodEnd,
        needsApproval ? StatementRequestStatus.PENDING : StatementRequestStatus.APPROVED));
    if (!needsApproval) {
      request.setDecidedAt(OffsetDateTime.now());
    }
    auditService.record(callerId, groupId, AuditActions.STATEMENT_REQUESTED, "StatementRequest",
        request.getId().toString(), "SUCCESS", "type=" + type + ",status=" + request.getStatus());
    return StatementDto.Request.from(request);
  }

  @Transactional(readOnly = true)
  public Page<StatementDto.Request> list(UUID groupId, UUID callerId, Pageable pageable) {
    groupAuth.requireMembership(groupId, callerId);
    if (groupAuth.hasPermission(groupId, callerId, Permission.STATEMENTS_APPROVE)) {
      return requests.findByGroupId(groupId, pageable).map(StatementDto.Request::from);
    }
    return requests.findByGroupIdAndRequesterUserId(groupId, callerId, pageable)
        .map(StatementDto.Request::from);
  }

  @Transactional
  public StatementDto.Request approve(UUID groupId, UUID requestId, UUID callerId) {
    groupAuth.requirePermission(groupId, callerId, Permission.STATEMENTS_APPROVE);
    StatementRequest request = requestInGroup(groupId, requestId);
    if (request.getStatus() != StatementRequestStatus.PENDING) {
      throw new UnprocessableException("Only pending requests can be approved");
    }
    request.setStatus(StatementRequestStatus.APPROVED);
    request.setReviewerUserId(callerId);
    request.setDecidedAt(OffsetDateTime.now());
    auditService.record(callerId, groupId, AuditActions.STATEMENT_DECIDED, "StatementRequest",
        request.getId().toString(), "SUCCESS", "decision=APPROVED");
    return StatementDto.Request.from(request);
  }

  @Transactional
  public StatementDto.Request reject(UUID groupId, UUID requestId, UUID callerId, String reason) {
    groupAuth.requirePermission(groupId, callerId, Permission.STATEMENTS_APPROVE);
    StatementRequest request = requestInGroup(groupId, requestId);
    if (request.getStatus() != StatementRequestStatus.PENDING) {
      throw new UnprocessableException("Only pending requests can be rejected");
    }
    if (reason == null || reason.trim().isEmpty()) {
      throw new UnprocessableException("A rejection reason is required");
    }
    request.setStatus(StatementRequestStatus.REJECTED);
    request.setReviewerUserId(callerId);
    request.setDecidedAt(OffsetDateTime.now());
    request.setRejectionReason(reason.trim());
    auditService.record(callerId, groupId, AuditActions.STATEMENT_DECIDED, "StatementRequest",
        request.getId().toString(), "SUCCESS", "decision=REJECTED");
    return StatementDto.Request.from(request);
  }

  /**
   * Generates the statement document live from persisted records. Nothing is
   * snapshotted: re-generating later reflects later records (period-bounded).
   */
  @Transactional(readOnly = true)
  public StatementDto.Document document(UUID groupId, UUID requestId, UUID callerId) {
    groupAuth.requireMembership(groupId, callerId);
    StatementRequest request = requestInGroup(groupId, requestId);
    boolean approver = groupAuth.hasPermission(groupId, callerId, Permission.STATEMENTS_APPROVE);
    if (!request.getRequesterUserId().equals(callerId) && !approver) {
      throw new ForbiddenOperationException("Cannot view another member's statement request");
    }
    if (request.getStatus() != StatementRequestStatus.APPROVED) {
      throw new UnprocessableException("Statement is available once the request is approved");
    }
    SavingsGroup group = groupAuth.groupOrNotFound(groupId);
    OffsetDateTime generatedAt = OffsetDateTime.now();
    if (request.getRequestType() == StatementRequestType.PERSONAL_STATEMENT) {
      return personalDocument(group, request, generatedAt);
    }
    return groupDocument(group, request, callerId, generatedAt);
  }

  private boolean approvalRequired(UUID groupId, StatementRequestType type) {
    return policies.findByGroupIdAndRequestType(groupId, type)
        .map(StatementPolicy::isApprovalRequired)
        .orElse(type == StatementRequestType.GROUP_SUMMARY);
  }

  private StatementRequest requestInGroup(UUID groupId, UUID requestId) {
    StatementRequest request = requests.findById(requestId)
        .orElseThrow(() -> new ResourceNotFoundException("Statement request not found"));
    if (!request.getGroupId().equals(groupId)) {
      throw new ResourceNotFoundException("Statement request not found in this group");
    }
    return request;
  }

  private StatementDto.Document personalDocument(SavingsGroup group, StatementRequest request,
      OffsetDateTime generatedAt) {
    UUID member = request.getRequesterUserId();
    List<Contribution> rows = contributions.findOverlapping(group.getId(), member,
        request.getPeriodStart(), request.getPeriodEnd());
    BigDecimal expected = BigDecimal.ZERO;
    BigDecimal confirmed = BigDecimal.ZERO;
    List<StatementDto.ContributionLine> lines = new ArrayList<>();
    for (Contribution row : rows) {
      lines.add(new StatementDto.ContributionLine(row.getPeriodStart(), row.getPeriodEnd(),
          row.getAmount(), row.getStatus().name(), row.getRecordedVia().name()));
      if (row.getStatus() == ContributionStatus.CONFIRMED) {
        confirmed = confirmed.add(row.getAmount());
      } else if (row.getStatus() == ContributionStatus.EXPECTED
          || row.getStatus() == ContributionStatus.SUBMITTED) {
        expected = expected.add(row.getAmount());
      }
    }
    List<StatementDto.LoanLine> loanLines = new ArrayList<>();
    BigDecimal borrowed = BigDecimal.ZERO;
    BigDecimal loanOutstanding = BigDecimal.ZERO;
    for (Loan loan : loans.findByGroupIdAndBorrowerUserId(group.getId(), member)) {
      BigDecimal repaid = repayments.sumByLoanAndStatus(loan.getId(), RepaymentStatus.CONFIRMED);
      BigDecimal outstanding = loan.getTotalDue().subtract(repaid);
      borrowed = borrowed.add(loan.getAmountPrincipal());
      loanOutstanding = loanOutstanding.add(outstanding.max(BigDecimal.ZERO));
      loanLines.add(new StatementDto.LoanLine(loan.getId(), loan.getAmountPrincipal(),
          loan.getTotalDue(), outstanding, loan.getStatus().name()));
    }
    String displayName = users.findById(member).map(User::getDisplayName)
        .orElse("Unknown member");
    return new StatementDto.Document(request.getId(), request.getRequestType().name(),
        group.getCurrency(), request.getPeriodStart(), request.getPeriodEnd(), generatedAt,
        "Personal statement for " + displayName, lines, loanLines, expected, confirmed,
        borrowed, loanOutstanding, null);
  }

  private StatementDto.Document groupDocument(SavingsGroup group, StatementRequest request,
      UUID callerId, OffsetDateTime generatedAt) {
    var summary = ledgerService.summary(group.getId(), callerId);
    int activeMembers = memberships.findByGroupIdAndStatus(group.getId(),
        MembershipStatus.ACTIVE).size();
    var totals = new StatementDto.GroupTotals(summary.expectedTotal(),
        summary.confirmedContributionsTotal(), summary.loansDisbursedTotal(),
        summary.loansRepaidTotal(), summary.loansOutstandingTotal(), summary.netBalance(),
        activeMembers);
    return new StatementDto.Document(request.getId(), request.getRequestType().name(),
        group.getCurrency(), request.getPeriodStart(), request.getPeriodEnd(), generatedAt,
        "Group summary for " + group.getName(), List.of(), List.of(), BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, totals);
  }
}
