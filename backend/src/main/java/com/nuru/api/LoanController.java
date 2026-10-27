package com.nuru.api;

import com.nuru.api.dto.FinanceDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.ContributionService;
import com.nuru.service.LoanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/loans")
@Tag(name = "Loans")
public class LoanController {

  private final LoanService loanService;

  public LoanController(LoanService loanService) {
    this.loanService = loanService;
  }

  @PostMapping
  public ResponseEntity<FinanceDto.Loan> request(@PathVariable UUID groupId,
      @Valid @RequestBody FinanceDto.RequestLoanRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    ContributionService.Created<FinanceDto.Loan> result = loanService.requestLoan(groupId,
        currentUser(), request.principal(), request.interestRate(), request.termMonths(),
        request.purpose(), idempotencyKey);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.value());
  }

  @GetMapping
  public Page<FinanceDto.Loan> list(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return loanService.listLoans(groupId, currentUser(), pageable);
  }

  @GetMapping("/{loanId}")
  public FinanceDto.Loan get(@PathVariable UUID groupId, @PathVariable UUID loanId) {
    return loanService.getLoan(groupId, loanId, currentUser());
  }

  @PostMapping("/{loanId}/approve")
  public FinanceDto.Loan approve(@PathVariable UUID groupId, @PathVariable UUID loanId,
      @Valid @RequestBody(required = false) FinanceDto.DecideLoanRequest decision) {
    return loanService.approve(groupId, loanId, currentUser(),
        decision == null ? null : decision.reason());
  }

  @PostMapping("/{loanId}/reject")
  public FinanceDto.Loan reject(@PathVariable UUID groupId, @PathVariable UUID loanId,
      @Valid @RequestBody(required = false) FinanceDto.DecideLoanRequest decision) {
    return loanService.reject(groupId, loanId, currentUser(),
        decision == null ? null : decision.reason());
  }

  @PostMapping("/{loanId}/cancel")
  public FinanceDto.Loan cancel(@PathVariable UUID groupId, @PathVariable UUID loanId) {
    return loanService.cancel(groupId, loanId, currentUser());
  }

  @PostMapping("/{loanId}/disburse")
  public FinanceDto.Loan disburse(@PathVariable UUID groupId, @PathVariable UUID loanId) {
    return loanService.disburse(groupId, loanId, currentUser());
  }

  @PostMapping("/{loanId}/mark-defaulted")
  public FinanceDto.Loan markDefaulted(@PathVariable UUID groupId, @PathVariable UUID loanId,
      @Valid @RequestBody(required = false) FinanceDto.DecideLoanRequest decision) {
    return loanService.markDefaulted(groupId, loanId, currentUser(),
        decision == null ? null : decision.reason());
  }

  @PostMapping("/{loanId}/reactivate")
  public FinanceDto.Loan reactivate(@PathVariable UUID groupId, @PathVariable UUID loanId) {
    return loanService.reactivate(groupId, loanId, currentUser());
  }

  @GetMapping("/{loanId}/schedule")
  public List<FinanceDto.Installment> schedule(@PathVariable UUID groupId,
      @PathVariable UUID loanId) {
    return loanService.schedule(groupId, loanId, currentUser());
  }

  @PostMapping("/{loanId}/repayments")
  public ResponseEntity<FinanceDto.Repayment> submitRepayment(@PathVariable UUID groupId,
      @PathVariable UUID loanId,
      @Valid @RequestBody FinanceDto.SubmitRepaymentRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    ContributionService.Created<FinanceDto.Repayment> result = loanService.submitRepayment(
        groupId, loanId, currentUser(), request.amount(), idempotencyKey);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.value());
  }

  @GetMapping("/{loanId}/repayments")
  public Page<FinanceDto.Repayment> listRepayments(@PathVariable UUID groupId,
      @PathVariable UUID loanId, @PageableDefault(size = 20) Pageable pageable) {
    return loanService.listRepayments(groupId, loanId, currentUser(), pageable);
  }

  @PostMapping("/{loanId}/repayments/{repaymentId}/confirm")
  public FinanceDto.Repayment confirmRepayment(@PathVariable UUID groupId,
      @PathVariable UUID loanId, @PathVariable UUID repaymentId) {
    return loanService.confirmRepayment(groupId, loanId, repaymentId, currentUser());
  }

  @PostMapping("/{loanId}/repayments/{repaymentId}/reject")
  public FinanceDto.Repayment rejectRepayment(@PathVariable UUID groupId,
      @PathVariable UUID loanId, @PathVariable UUID repaymentId,
      @Valid @RequestBody(required = false) FinanceDto.RejectRequest request) {
    return loanService.rejectRepayment(groupId, loanId, repaymentId, currentUser(),
        request == null ? null : request.reason());
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
